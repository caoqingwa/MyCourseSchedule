"""Run with Python 3 to check the v9-to-v10 SQL against the exported v9 schema."""

import json
import re
import sqlite3
from pathlib import Path


APP = Path(__file__).resolve().parents[2]
SCHEMA = APP / "schemas/com.example.courseschedule.data.db.AppDatabase/9.json"
SOURCE = APP / "src/main/java/com/example/courseschedule/data/db/AppDatabase.kt"


def verify_migration():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))["database"]
    source = SOURCE.read_text(encoding="utf-8")
    sql = re.search(
        r'MIGRATION_9_10.*?"""(CREATE TABLE IF NOT EXISTS schedule_changes.*?)"""',
        source, re.S,
    ).group(1)

    with sqlite3.connect(":memory:") as db:
        db.execute("PRAGMA foreign_keys = ON")
        for entity in schema["entities"]:
            table = entity["tableName"]
            db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
            for index in entity.get("indices", []):
                db.execute(index["createSql"].replace("${TABLE_NAME}", table))

        db.execute("INSERT INTO semesters VALUES (1, '学期', 1788739200000, 20, 12, 5, '[]')")
        db.execute("INSERT INTO courses VALUES (1, 1, '计算机', '老师', '0', NULL)")
        db.execute("INSERT INTO schedules VALUES (1, 1, 1, 1, 2, 1, 20, 1, NULL)")
        db.execute("INSERT INTO schedules VALUES (2, 1, 2, 3, 4, 1, 20, 2, NULL)")
        db.execute("INSERT INTO exams VALUES (1, 1, 1790000000000, 48, '期中')")

        tables = ("semesters", "courses", "schedules", "exams")
        before = {table: db.execute(f"SELECT * FROM {table} ORDER BY id").fetchall() for table in tables}
        db.execute(sql)
        after = {table: db.execute(f"SELECT * FROM {table} ORDER BY id").fetchall() for table in tables}
        assert before == after
        assert not db.execute("SELECT * FROM schedule_changes").fetchall()

        db.execute("INSERT INTO schedule_changes VALUES (1, 20703, NULL, 1, 2, NULL, 0)")
        db.execute("DELETE FROM schedules WHERE id = 1")
        assert not db.execute("SELECT * FROM schedule_changes").fetchall()
        assert not db.execute("PRAGMA foreign_key_check").fetchall()


if __name__ == "__main__":
    verify_migration()
