"""Inspect/apply the versioned chat schema using the configured development datasource.
Never prints credentials or conversation contents. Requires PyMySQL and PyYAML.
"""
import os
import pathlib
import re
import sys
import urllib.parse
import yaml
import pymysql
import datetime
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]

def resolve(value):
    return re.sub(r'\$\{([^}:]+)(?::([^}]*))?\}', lambda m: os.environ.get(m[1], m[2] or ''), str(value))

def connect():
    config = next(yaml.safe_load_all((ROOT / 'src/main/resources/application.yml').read_text(encoding='utf-8')))
    profile = os.environ.get('SPRING_PROFILES_ACTIVE', config['spring']['profiles']['active'])
    environment = config['app']['ai']['environment']
    if profile != 'dev' or environment != 'development':
        raise RuntimeError('Only the configured dev/development environment is allowed')
    ds = config['spring']['datasource']
    url = urllib.parse.urlparse(resolve(os.environ.get('SPRING_DATASOURCE_URL', ds['url']))[5:])
    print(f'Environment: {profile}/{environment}; database: {url.hostname}:{url.port or 3306}{url.path}')
    return pymysql.connect(host=url.hostname, port=url.port or 3306,
        user=resolve(os.environ.get('SPRING_DATASOURCE_USERNAME', ds['username'])),
        password=resolve(os.environ.get('SPRING_DATASOURCE_PASSWORD', ds['password'])),
        database=url.path.lstrip('/'), connect_timeout=8, read_timeout=20, charset='utf8mb4')

if __name__ == '__main__':
    try:
        with connect() as conn, conn.cursor() as cur:
            cur.execute('SHOW TABLES')
            names = [row[0] for row in cur.fetchall()]
            print('Tables:', ', '.join(names))
            if '--apply' in sys.argv:
                # MySQL DDL commits implicitly. Keep an additive, rerunnable script and
                # a schema snapshot; never rewrite existing data or migration history.
                backup = ROOT / 'target' / ('chat-schema-before-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '.sql')
                backup.parent.mkdir(exist_ok=True)
                definitions = []
                for name in ('users', 'projects', 'ai_conversations', 'ai_messages'):
                    if name in names:
                        cur.execute(f'SHOW CREATE TABLE `{name}`')
                        definitions.append(cur.fetchone()[1] + ';')
                backup.write_text('\n\n'.join(definitions), encoding='utf-8')
                sql = (ROOT / 'docs/schema/VF_CHAT_001.sql').read_text(encoding='utf-8')
                sql = re.sub(r'--[^\n]*', '', sql)
                for statement in sql.split(';'):
                    if statement.strip(): cur.execute(statement)
                print('Applied VF_CHAT_001; previous schema preserved in', backup.name)
            if '--verify' in sys.argv or '--apply' in sys.argv:
                cur.execute('SELECT id FROM users LIMIT 1')
                owner = cur.fetchone()[0]
                conversation, request, user_message, assistant_message = [str(uuid.uuid4()) for _ in range(4)]
                conn.begin()
                try:
                    cur.execute('INSERT INTO ai_conversations (id,owner_id,title,next_sequence,created_at,updated_at) VALUES (%s,%s,%s,2,NOW(6),NOW(6))', (conversation,owner,'Schema verification'))
                    for mid, sequence, role in ((user_message,1,'user'),(assistant_message,2,'assistant')):
                        cur.execute('INSERT INTO ai_messages (id,conversation_id,client_request_id,sequence_number,role,status,content,created_at,updated_at) VALUES (%s,%s,%s,%s,%s,%s,%s,NOW(6),NOW(6))', (mid,conversation,request,sequence,role,'completed','Synthetic verification'))
                    cur.execute('UPDATE ai_conversations SET title=%s WHERE id=%s', ('Verified',conversation))
                    cur.execute('SELECT sequence_number,role FROM ai_messages WHERE conversation_id=%s ORDER BY sequence_number', (conversation,))
                    assert cur.fetchall() == ((1,'user'),(2,'assistant'))
                    cur.execute('DELETE FROM ai_conversations WHERE id=%s', (conversation,))
                    cur.execute('SELECT COUNT(*) FROM ai_messages WHERE conversation_id=%s', (conversation,))
                    assert cur.fetchone()[0] == 0
                    print('Verified transactional insert, update, ordered read, and cascading delete using synthetic records only')
                finally:
                    conn.rollback()
                cur.execute('SELECT COUNT(*) FROM ai_conversations WHERE id=%s', (conversation,))
                assert cur.fetchone()[0] == 0
                print('Verification rolled back; no synthetic chat data retained')
                cur.execute('SHOW TABLES')
                names = [row[0] for row in cur.fetchall()]
            for name in names:
                if any(word in name.lower() for word in ('chat', 'conversation', 'message')) or name in ('users', 'projects'):
                    if not re.fullmatch(r'[A-Za-z0-9_]+', name): continue
                    cur.execute(f'SHOW CREATE TABLE `{name}`')
                    print(cur.fetchone()[1])
                    cur.execute(f'SELECT COUNT(*) FROM `{name}`')
                    print('Existing rows:', cur.fetchone()[0])
    except Exception as error:
        print('Database inspection failed:', type(error).__name__, 'code:', error.args[0] if error.args and isinstance(error.args[0], int) else 'configuration/connection error')
        sys.exit(1)
