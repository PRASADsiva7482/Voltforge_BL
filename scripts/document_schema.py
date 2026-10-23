"""Inspect/apply additive document storage on the configured development DB.
Only synthetic verification data is written and rolled back. No user text logged.
"""
import datetime
import re
import sys
import uuid
from chat_schema import ROOT, connect

try:
    with connect() as conn, conn.cursor() as cur:
        cur.execute('SHOW TABLES')
        names = [r[0] for r in cur.fetchall()]
        print('Document-related tables:', [n for n in names if any(w in n for w in ('document', 'chunk', 'source'))])
        expected = ('ai_documents', 'ai_document_versions', 'ai_document_chunks')
        if '--apply' in sys.argv:
            backup = ROOT / 'target' / ('document-schema-before-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '.sql')
            backup.parent.mkdir(exist_ok=True)
            definitions = []
            for name in ('users', 'projects', *expected):
                if name in names:
                    cur.execute(f'SHOW CREATE TABLE `{name}`')
                    definitions.append(cur.fetchone()[1] + ';')
            backup.write_text('\n\n'.join(definitions), encoding='utf-8')
            sql = re.sub(r'--[^\n]*', '', (ROOT / 'docs/schema/VF_DOCUMENT_001.sql').read_text())
            for statement in sql.split(';'):
                if statement.strip(): cur.execute(statement)
            print('Applied VF_DOCUMENT_001; schema snapshot:', backup.name)
        if '--apply' in sys.argv or '--verify' in sys.argv:
            for table in expected:
                cur.execute(f'SHOW CREATE TABLE `{table}`')
                assert 'FOREIGN KEY' in cur.fetchone()[1]
            owner, document, version, chunk = [str(uuid.uuid4()) for _ in range(4)]
            conn.begin()
            try:
                cur.execute('INSERT INTO users (id,keycloak_id,username,email) VALUES (%s,%s,%s,%s)', (owner,owner,owner,owner+'@example.invalid'))
                cur.execute('INSERT INTO ai_documents VALUES (%s,%s,NULL,%s,1,NOW(6),NOW(6))', (document,owner,'Synthetic verification'))
                cur.execute('INSERT INTO ai_document_versions VALUES (%s,%s,1,%s,%s,1,1,%s,NOW(6),NOW(6))', (version,document,'a'*64,'text/plain','ready'))
                cur.execute('INSERT INTO ai_document_chunks VALUES (%s,%s,1,1,%s,NOW(6),NOW(6))', (chunk,version,'ATmega328P synthetic reference'))
                cur.execute('UPDATE ai_documents SET title=%s WHERE id=%s', ('Verified',document))
                cur.execute('SELECT content FROM ai_document_chunks WHERE version_id=%s', (version,))
                assert cur.fetchone()[0].startswith('ATmega328P')
                cur.execute('DELETE FROM users WHERE id=%s', (owner,))
                for table, key in zip(expected, (document,version,chunk)):
                    cur.execute(f'SELECT COUNT(*) FROM `{table}` WHERE id=%s', (key,))
                    assert cur.fetchone()[0] == 0
            finally:
                conn.rollback()
            print('Verified schema, insert/update/read and cascades; synthetic DML rolled back')
except Exception as error:
    print('Document schema operation failed:', type(error).__name__, 'code:', error.args[0] if error.args and isinstance(error.args[0], int) else 'configuration/verification error')
    sys.exit(1)
