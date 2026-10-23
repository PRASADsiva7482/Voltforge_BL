"""Apply additive preference storage to the configured development DB only.
Uses existing datasource/driver; never prints account data or credentials.
"""
import datetime
import re
import sys
import uuid
from chat_schema import ROOT, connect

if __name__ == '__main__':
    try:
        with connect() as conn, conn.cursor() as cur:
            cur.execute('SHOW TABLES')
            names = [row[0] for row in cur.fetchall()]
            print('Memory-related tables:', [name for name in names if 'memor' in name])
            if '--apply' in sys.argv:
                backup = ROOT / 'target' / ('memory-schema-before-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '.sql')
                backup.parent.mkdir(exist_ok=True)
                definitions = []
                for name in ('users', 'ai_user_memories'):
                    if name in names:
                        cur.execute(f'SHOW CREATE TABLE `{name}`')
                        definitions.append(cur.fetchone()[1] + ';')
                backup.write_text('\n\n'.join(definitions), encoding='utf-8')
                sql = re.sub(r'--[^\n]*', '', (ROOT / 'docs/schema/VF_MEMORY_001.sql').read_text(encoding='utf-8'))
                for statement in sql.split(';'):
                    if statement.strip(): cur.execute(statement)
                print('Applied VF_MEMORY_001; existing data preserved; schema snapshot:', backup.name)
            cur.execute('SHOW CREATE TABLE ai_user_memories')
            schema = cur.fetchone()[1]
            assert all(name in schema for name in ('uk_ai_user_memory', 'fk_ai_user_memory_owner', 'ck_ai_user_memory_value'))
            # Use synthetic owner so no user's actual preference is touched.
            test_owner, memory = str(uuid.uuid4()), str(uuid.uuid4())
            conn.begin()
            try:
                cur.execute('INSERT INTO users (id,keycloak_id,username,email) VALUES (%s,%s,%s,%s)', (test_owner,test_owner,test_owner,test_owner+'@example.invalid'))
                cur.execute('INSERT INTO ai_user_memories (id,owner_id,preference_key,preference_value,created_at,updated_at) VALUES (%s,%s,%s,%s,NOW(6),NOW(6))', (memory,test_owner,'response_detail','concise'))
                cur.execute('UPDATE ai_user_memories SET preference_value=%s WHERE id=%s', ('detailed',memory))
                cur.execute('SELECT preference_value FROM ai_user_memories WHERE owner_id=%s', (test_owner,))
                assert cur.fetchone()[0] == 'detailed'
                try:
                    cur.execute('UPDATE ai_user_memories SET preference_value=%s WHERE id=%s', ('arbitrary text',memory))
                    raise AssertionError('Memory constraint did not reject arbitrary text')
                except Exception as error:
                    if not error.args or error.args[0] != 3819: raise
                cur.execute('DELETE FROM users WHERE id=%s', (test_owner,))
                cur.execute('SELECT COUNT(*) FROM ai_user_memories WHERE id=%s', (memory,))
                assert cur.fetchone()[0] == 0
            finally:
                conn.rollback()
            print('Verified schema, insert/read/update, invalid-value rejection, owner cascade; synthetic writes rolled back')
    except Exception as error:
        print('Memory schema operation failed:', type(error).__name__, 'code:', error.args[0] if error.args and isinstance(error.args[0], int) else 'configuration/verification error')
        sys.exit(1)
