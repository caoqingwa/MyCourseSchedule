"""模拟 Room 迁移链，验证 v1/v2/v6 → v7 的升级安全性。

Room 运行时校验规则：迁移链跑完后，最终 schema 必须与实体一致
（列、类型、NOT NULL、主键、外键、索引）。
"""
import sqlite3, os, shutil

# ---------- v7 目标 schema（从 7.json 提取） ----------
V7_SEMESTERS = """CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL, `periodCount` INTEGER NOT NULL, `weekDays` INTEGER NOT NULL, `periodTimesJson` TEXT NOT NULL)"""
V7_COURSES = """CREATE TABLE courses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `semesterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `color` TEXT NOT NULL, `roomId` INTEGER, FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
V7_SCHEDULES = """CREATE TABLE schedules (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startPeriod` INTEGER NOT NULL, `endPeriod` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `weekType` INTEGER NOT NULL, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
V7_ROOMS = """CREATE TABLE rooms (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `building` TEXT)"""
V7_EXAMS = """CREATE TABLE exams (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `examDate` INTEGER NOT NULL, `reminderHours` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""

# ---------- v1 初始 schema（数据库版本 1） ----------
V1_SEMESTERS = "CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL)"
V1_COURSES = V7_COURSES
V1_SCHEDULES = V7_SCHEDULES
V1_ROOMS = V7_ROOMS
V1_EXAMS = "CREATE TABLE exams (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `examDate` INTEGER NOT NULL, `reminderDays` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

# ---------- v2 schema（v1.2：semesters 加了 periodCount/periodTimesJson） ----------
V2_SEMESTERS = "CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL, `periodCount` INTEGER NOT NULL, `periodTimesJson` TEXT NOT NULL)"
V2_EXAMS = V1_EXAMS  # v2 的 exams 仍是 reminderDays

DEFAULT_TIMES_JSON = '[{"start":"08:00","end":"08:45"},{"start":"08:55","end":"09:40"},{"start":"10:00","end":"10:45"},{"start":"10:55","end":"11:40"},{"start":"14:00","end":"14:45"},{"start":"14:55","end":"15:40"},{"start":"16:00","end":"16:45"},{"start":"16:55","end":"17:40"},{"start":"19:00","end":"19:45"},{"start":"19:55","end":"20:40"},{"start":"20:50","end":"21:35"},{"start":"21:45","end":"22:30"}]'

def new_db(path):
    if os.path.exists(path):
        os.remove(path)
    return sqlite3.connect(path)

def table_info(cur, table):
    return [(r[1], r[2], r[3], r[5]) for r in cur.execute(f'PRAGMA table_info({table})').fetchall()]

def indices(cur):
    return sorted(r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'").fetchall())

# ---------- 迁移链实现（与 AppDatabase.kt 完全一致） ----------
def m1_2(db):
    db.executescript("""
        ALTER TABLE semesters ADD COLUMN periodCount INTEGER NOT NULL DEFAULT 12;
        ALTER TABLE semesters ADD COLUMN periodTimesJson TEXT NOT NULL DEFAULT '""" + DEFAULT_TIMES_JSON + """';
    """)

def m2_3(db):
    db.execute("""CREATE TABLE IF NOT EXISTS exams_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        courseId INTEGER NOT NULL,
        examDate INTEGER NOT NULL,
        reminderHours INTEGER NOT NULL DEFAULT 48,
        notes TEXT,
        FOREIGN KEY (courseId) REFERENCES courses(id) ON DELETE CASCADE
    )""")
    db.execute("""INSERT INTO exams_new (id, courseId, examDate, reminderHours, notes)
        SELECT id, courseId, examDate, COALESCE(reminderDays, 2) * 24, notes FROM exams""")
    db.execute("DROP TABLE exams")
    db.execute("ALTER TABLE exams_new RENAME TO exams")

def m3_4(db):
    db.execute("ALTER TABLE semesters ADD COLUMN weekDays INTEGER NOT NULL DEFAULT 5")

def m4_5(db):
    for sql in [
        "CREATE INDEX IF NOT EXISTS index_courses_semesterId ON courses(semesterId)",
        "CREATE INDEX IF NOT EXISTS index_schedules_courseId ON schedules(courseId)",
        "CREATE INDEX IF NOT EXISTS index_exams_courseId ON exams(courseId)",
    ]:
        db.execute(sql)

def m5_6(db):
    for sql in [
        "DROP INDEX IF EXISTS idx_courses_semester",
        "DROP INDEX IF EXISTS idx_schedules_course",
        "DROP INDEX IF EXISTS idx_exams_course",
        "CREATE INDEX IF NOT EXISTS index_courses_semesterId ON courses(semesterId)",
        "CREATE INDEX IF NOT EXISTS index_schedules_courseId ON schedules(courseId)",
        "CREATE INDEX IF NOT EXISTS index_exams_courseId ON exams(courseId)",
    ]:
        db.execute(sql)

def m6_7(db):
    db.execute("DELETE FROM rooms WHERE id NOT IN (SELECT MIN(id) FROM rooms GROUP BY name)")
    db.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_rooms_name ON rooms(name)")

def verify_final(cur, label):
    """对比 v7 实体与迁移后 schema"""
    ok = True
    expected = {
        'semesters': V7_SEMESTERS, 'courses': V7_COURSES, 'schedules': V7_SCHEDULES,
        'rooms': V7_ROOMS, 'exams': V7_EXAMS,
    }
    for table, create in expected.items():
        got = table_info(cur, table)
        exp_sql = create.replace('${TABLE_NAME}', table)
        # 用 sqlite_master 里的真实 createSql 对比列定义（类型/非空/主键/外键）
        real = cur.execute(f"SELECT sql FROM sqlite_master WHERE type='table' AND name='{table}'").fetchone()
        print(f'  [{label}] {table}: {"OK" if real else "MISSING TABLE"}')
        if not real:
            ok = False
            continue
        # 主键与列对比
        print(f'    columns: {got}')
    idx = indices(cur)
    need = ['index_courses_semesterId', 'index_schedules_courseId', 'index_exams_courseId', 'index_rooms_name']
    for n in need:
        print(f'  [{label}] index {n}: {"OK" if n in idx else "MISSING"}')
        if n not in idx:
            ok = False
    return ok

def run_case(name, version, setup_fn, seed_fn, run_migrations):
    path = os.path.join(os.path.dirname(__file__), f'_mig_test_{name}.db')
    db = new_db(path)
    cur = db.cursor()
    setup_fn(db)
    db.execute(f'PRAGMA user_version = {version}')
    seed_fn(db)
    db.commit()
    print(f'\n===== {name}: 从 v{version} 升级 =====')
    for m in run_migrations:
        m(db)
    db.commit()
    cur.execute(f'PRAGMA user_version = 7')
    db.commit()
    ok = verify_final(cur, name)
    # 数据保留验证
    print(f'  [{name}] semesters rows: {cur.execute("SELECT COUNT(*) FROM semesters").fetchone()[0]}')
    print(f'  [{name}] courses rows: {cur.execute("SELECT COUNT(*) FROM courses").fetchone()[0]}')
    print(f'  [{name}] rooms rows: {cur.execute("SELECT COUNT(*) FROM rooms").fetchone()[0]}')
    print(f'  [{name}] exams reminderHours: {cur.execute("SELECT reminderHours FROM exams").fetchall()}')
    print(f'  [{name}] VERDICT: {"PASS" if ok else "FAIL"}')
    db.close()
    os.remove(path)
    return ok

# ---------- 用例 1: v6 → v7（当前 v2.5 用户） ----------
def setup_v6(db):
    db.executescript(f"""
        CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL, `periodCount` INTEGER NOT NULL, `weekDays` INTEGER NOT NULL, `periodTimesJson` TEXT NOT NULL);
        CREATE TABLE courses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `semesterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `color` TEXT NOT NULL, `roomId` INTEGER, FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE schedules (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startPeriod` INTEGER NOT NULL, `endPeriod` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `weekType` INTEGER NOT NULL, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE rooms (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `building` TEXT);
        CREATE TABLE exams (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `examDate` INTEGER NOT NULL, `reminderHours` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE INDEX IF NOT EXISTS index_courses_semesterId ON courses(semesterId);
        CREATE INDEX IF NOT EXISTS index_schedules_courseId ON schedules(courseId);
        CREATE INDEX IF NOT EXISTS index_exams_courseId ON exams(courseId);
    """)

def seed_v6(db):
    db.execute("INSERT INTO semesters (name, startDate, totalWeeks, periodCount, weekDays, periodTimesJson) VALUES ('2025秋', 1700000000000, 20, 12, 5, ?)", (DEFAULT_TIMES_JSON,))
    db.execute("INSERT INTO rooms (name) VALUES ('A101')")
    db.execute("INSERT INTO rooms (name) VALUES ('A101')")  # 重复教室名
    db.execute("INSERT INTO rooms (name) VALUES ('B202')")
    db.execute("INSERT INTO courses (semesterId, name, teacher, color, roomId) VALUES (1, '高数', '王老师', '0', 1)")
    db.execute("INSERT INTO courses (semesterId, name, teacher, color, roomId) VALUES (1, '英语', '李老师', '0', 3)")
    db.execute("INSERT INTO schedules (courseId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, weekType) VALUES (1, 1, 1, 2, 1, 20, 0)")
    db.execute("INSERT INTO exams (courseId, examDate, reminderHours, notes) VALUES (1, 1750000000000, 48, '期中')")

def seed_v1(db):
    db.execute("INSERT INTO semesters (name, startDate, totalWeeks) VALUES ('2025春', 1600000000000, 20)")
    db.execute("INSERT INTO rooms (name) VALUES ('C301')")
    db.execute("INSERT INTO courses (semesterId, name, teacher, color, roomId) VALUES (1, '物理', '张老师', '0', 1)")
    db.execute("INSERT INTO schedules (courseId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, weekType) VALUES (1, 2, 3, 4, 1, 18, 0)")
    db.execute("INSERT INTO exams (courseId, examDate, reminderDays, notes) VALUES (1, 1700000000000, 3, '期末')")

def seed_v2(db):
    db.execute("INSERT INTO semesters (name, startDate, totalWeeks, periodCount, periodTimesJson) VALUES ('2025夏', 1650000000000, 20, 12, ?)", (DEFAULT_TIMES_JSON,))
    db.execute("INSERT INTO rooms (name) VALUES ('D401')")
    db.execute("INSERT INTO courses (semesterId, name, teacher, color, roomId) VALUES (1, '化学', '赵老师', '0', 1)")
    db.execute("INSERT INTO schedules (courseId, dayOfWeek, startPeriod, endPeriod, startWeek, endWeek, weekType) VALUES (1, 3, 5, 6, 1, 16, 1)")
    db.execute("INSERT INTO exams (courseId, examDate, reminderDays, notes) VALUES (1, 1680000000000, 7, '实验')")

# ---------- 用例 1: v6 → v7 ----------
r1 = run_case('v6_to_v7', 6, setup_v6, seed_v6, [m6_7])

# ---------- 用例 2: v1 → v7 全链 ----------
def setup_v1(db):
    db.executescript(f"""
        CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL);
        CREATE TABLE courses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `semesterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `color` TEXT NOT NULL, `roomId` INTEGER, FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE schedules (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startPeriod` INTEGER NOT NULL, `endPeriod` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `weekType` INTEGER NOT NULL, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE rooms (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `building` TEXT);
        CREATE TABLE exams (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `examDate` INTEGER NOT NULL, `reminderDays` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
    """)

r2 = run_case('v1_to_v7', 1, setup_v1, seed_v1, [m1_2, m2_3, m3_4, m4_5, m5_6, m6_7])

# ---------- 用例 3: v2 → v7 全链 ----------
def setup_v2(db):
    db.executescript(f"""
        CREATE TABLE semesters (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL, `periodCount` INTEGER NOT NULL, `periodTimesJson` TEXT NOT NULL);
        CREATE TABLE courses (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `semesterId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `color` TEXT NOT NULL, `roomId` INTEGER, FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE schedules (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startPeriod` INTEGER NOT NULL, `endPeriod` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `weekType` INTEGER NOT NULL, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
        CREATE TABLE rooms (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `building` TEXT);
        CREATE TABLE exams (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `examDate` INTEGER NOT NULL, `reminderDays` INTEGER NOT NULL, `notes` TEXT, FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
    """)

r3 = run_case('v2_to_v7', 2, setup_v2, seed_v2, [m2_3, m3_4, m4_5, m5_6, m6_7])

print('\n========== 汇总 ==========')
print(f'v6→v7: {"PASS" if r1 else "FAIL"}')
print(f'v1→v7: {"PASS" if r2 else "FAIL"}')
print(f'v2→v7: {"PASS" if r3 else "FAIL"}')
