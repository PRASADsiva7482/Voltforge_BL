"""Read-only audit of this checkout's additive migrations on the configured dev DB.

Only aggregate counts and schema metadata are recorded; no account/chat contents.
Run with system Python (PyMySQL/PyYAML), not the AI inference virtualenv.
"""
import datetime
import json
import re
import sys
from chat_schema import ROOT, connect


def audit():
    result = {"checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "migrations": {}, "rows": {}, "orphan_counts": {}, "problems": []}
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SELECT DATABASE()")
        result["database"] = cur.fetchone()[0]
        cur.execute("SHOW TABLES")
        tables = {row[0] for row in cur.fetchall()}
        for path in sorted((ROOT / "docs/schema").glob("*.sql")):
            sql = re.sub(r"--[^\n]*", "", path.read_text(encoding="utf-8"))
            problems = []
            for table, body in re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\) ENGINE=", sql, re.S | re.I):
                if table not in tables:
                    problems.append(f"Missing table {table}")
                    continue
                cur.execute("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s", (table,))
                actual = {name: (kind.lower(), nullable) for name, kind, nullable in cur.fetchall()}
                for name, kind, rest in re.findall(r"^\s*(\w+)\s+(VARCHAR\(\d+\)|DATETIME\(\d+\)|BIGINT|INT|JSON|MEDIUMTEXT)([^\n]*)", body, re.M | re.I):
                    nullable = "NO" if "NOT NULL" in rest.upper() or "PRIMARY KEY" in rest.upper() else "YES"
                    if actual.get(name) != (kind.lower(), nullable):
                        problems.append(f"Column mismatch {table}.{name}")
                expected_constraints = re.findall(r"CONSTRAINT\s+(\w+)", body, re.I)
                cur.execute("SELECT CONSTRAINT_NAME FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=%s", (table,))
                actual_constraints = {row[0] for row in cur.fetchall()}
                problems.extend(f"Missing constraint {table}.{name}" for name in expected_constraints if name not in actual_constraints)
                expected_indexes = re.findall(r"^\s*(?:UNIQUE\s+)?KEY\s+(\w+)\s*\(([^)]*)\)", body, re.I | re.M)
                for name, columns in expected_indexes:
                    check_index(cur, table, name, columns, problems)
            if path.stem == "VFOPT_X_001_F001":
                cur.execute("SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='projects' AND column_name='document_revision'")
                if cur.fetchone() != ("bigint", "NO", "0"):
                    problems.append("Missing/incorrect projects.document_revision")
            if path.stem == "VFOPT_X_001_F005":
                for name, columns in re.findall(r"ADD INDEX\s+(\w+)\s*\(([^)]*)\)", sql, re.I):
                    check_index(cur, "projects", name, columns, problems)
            result["migrations"][path.stem] = "verified" if not problems else "mismatch"
            result["problems"].extend(problems)
        for table in sorted(tables - {"flyway_schema_history"}):
            if re.fullmatch(r"[a-z_]+", table):
                cur.execute(f"SELECT COUNT(*) FROM `{table}`")
                result["rows"][table] = cur.fetchone()[0]
        cur.execute("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME,CONSTRAINT_NAME FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL")
        for table, column, parent, parent_key, constraint in cur.fetchall():
            if not all(re.fullmatch(r"[A-Za-z0-9_]+", n) for n in (table, column, parent, parent_key)):
                raise ValueError("Unexpected identifier in schema")
            cur.execute(f"SELECT COUNT(*) FROM `{table}` c LEFT JOIN `{parent}` p ON c.`{column}`=p.`{parent_key}` WHERE c.`{column}` IS NOT NULL AND p.`{parent_key}` IS NULL")
            count = cur.fetchone()[0]
            result["orphan_counts"][constraint] = count
            if count: result["problems"].append(f"Orphans for {constraint}: {count}")
        if "flyway_schema_history" in tables:
            cur.execute("SELECT version,success FROM flyway_schema_history ORDER BY installed_rank")
            result["legacy_flyway_versions"] = [{"version": version, "success": bool(success)} for version, success in cur.fetchall()]
        # End this read-only transaction; a fresh connection confirms visibility.
        conn.rollback()
    with connect() as conn, conn.cursor() as cur:
        cur.execute("SHOW TABLES")
        result["visible_from_new_connection"] = tables == {row[0] for row in cur.fetchall()}
    report = ROOT / "target/shared-db-schema-audit.json"
    report.parent.mkdir(exist_ok=True)
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    print("Read-only audit report:", report)
    return bool(result["problems"]) or not result["visible_from_new_connection"]


def check_index(cur, table, name, columns, problems):
    expected = [(part.strip().split()[0], "D" if "DESC" in part.upper() else "A") for part in columns.split(",")]
    cur.execute("SELECT COLUMN_NAME,COLLATION FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=%s AND index_name=%s ORDER BY SEQ_IN_INDEX", (table, name))
    if list(cur.fetchall()) != expected:
        problems.append(f"Index mismatch {table}.{name}")


if __name__ == "__main__":
    try:
        sys.exit(int(audit()))
    except Exception as error:
        print("Schema audit failed:", type(error).__name__, "code:", error.args[0] if error.args and isinstance(error.args[0], int) else "configuration/verification error")
        sys.exit(1)
