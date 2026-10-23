"""Apply/verify derived embedding storage using the existing dev connection."""
import datetime
import json
import re
import sys
import uuid
from chat_schema import ROOT, connect

try:
    with connect() as conn, conn.cursor() as cur:
        cur.execute('SHOW TABLES')
        names = {r[0] for r in cur.fetchall()}
        print('Embedding table present:', 'ai_document_embeddings' in names)
        if '--apply' in sys.argv:
            definitions = []
            for name in ('ai_documents','ai_document_versions','ai_document_chunks','ai_document_embeddings'):
                if name in names:
                    cur.execute(f'SHOW CREATE TABLE `{name}`'); definitions.append(cur.fetchone()[1]+';')
            backup = ROOT/'target'/('embedding-schema-before-'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')+'.sql')
            backup.parent.mkdir(exist_ok=True); backup.write_text('\n\n'.join(definitions),encoding='utf-8')
            sql = re.sub(r'--[^\n]*','',(ROOT/'docs/schema/VF_EMBEDDING_001.sql').read_text())
            for statement in sql.split(';'):
                if statement.strip(): cur.execute(statement)
            print('Applied VF_EMBEDDING_001; schema snapshot:',backup.name)
        cur.execute('SHOW CREATE TABLE ai_document_embeddings')
        schema = cur.fetchone()[1]
        assert all(name in schema for name in ('uk_ai_embedding_chunk_model','fk_ai_embedding_chunk'))
        owner, document, version, chunk, embedding = [str(uuid.uuid4()) for _ in range(5)]
        conn.begin()
        try:
            cur.execute('INSERT INTO users (id,keycloak_id,username,email) VALUES (%s,%s,%s,%s)',(owner,owner,owner,owner+'@example.invalid'))
            cur.execute('INSERT INTO ai_documents VALUES (%s,%s,NULL,%s,1,NOW(6),NOW(6))',(document,owner,'Synthetic verification'))
            cur.execute('INSERT INTO ai_document_versions VALUES (%s,%s,1,%s,%s,1,1,%s,NOW(6),NOW(6))',(version,document,'a'*64,'text/plain','ready'))
            cur.execute('INSERT INTO ai_document_chunks VALUES (%s,%s,1,1,%s,NOW(6),NOW(6))',(chunk,version,'Synthetic test'))
            vector = [1.0]+[0.0]*383
            cur.execute('INSERT INTO ai_document_embeddings VALUES (%s,%s,%s,%s,NOW(6),NOW(6))',(embedding,chunk,'b'*64,json.dumps(vector)))
            cur.execute('SELECT embedding FROM ai_document_embeddings WHERE id=%s',(embedding,))
            assert json.loads(cur.fetchone()[0]) == vector
            cur.execute('DELETE FROM users WHERE id=%s',(owner,))
            cur.execute('SELECT COUNT(*) FROM ai_document_embeddings WHERE id=%s',(embedding,))
            assert cur.fetchone()[0] == 0
        finally: conn.rollback()
        print('Verified vector write/read and owner-to-vector cascade; synthetic writes rolled back')
except Exception as error:
    print('Embedding migration failed:',type(error).__name__,'code:',error.args[0] if error.args and isinstance(error.args[0],int) else 'configuration/verification error')
    sys.exit(1)
