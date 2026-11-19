"""验证解析规则：按表头名匹配列、解析上课时间字符串、跳过课程设计类"""
import openpyxl
import re

wb = openpyxl.load_workbook(r'F:\Projects\CourseSchedule\课程表.xlsx', data_only=True)
ws = wb['sheet1']

# 1. 表头名 → 列索引（列顺序可打乱：这里按名字找）
headers = {}
for c in range(1, ws.max_column + 1):
    v = ws.cell(row=1, column=c).value
    if v:
        headers[str(v).strip()] = c
print('headers:', headers)

# 关键列
COL_NAME = '课程名称'
COL_TIME = '上课时间'
COL_PLACE = '教学地点'
COL_TEACHER = '教师姓名'
COL_WEEK = '起始结束周'

DAY_MAP = {'星期一': 1, '星期二': 2, '星期三': 3, '星期四': 4, '星期五': 5, '星期六': 6, '星期日': 7}

def parse_time(t):
    """解析 '星期四第9-11节{3-11周(单)}' → (day, sp, ep, sw, ew, weekType)"""
    m = re.match(r'(星期[一二三四五六日])第(\d+)-(\d+)节\{(\d+)-(\d+)周(\(单\)|\(双\))?\}', t.strip())
    if not m:
        return None
    day = DAY_MAP[m.group(1)]
    sp, ep, sw, ew = int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
    wt = 1 if m.group(6) == '(单)' else (2 if m.group(6) == '(双)' else 0)
    return (day, sp, ep, sw, ew, wt)

skipped, imported = [], []
for r in range(2, ws.max_row + 1):
    name = ws.cell(row=r, column=headers[COL_NAME]).value
    if not name:
        continue
    name = str(name).strip()
    # 课程设计类跳过（名称含"课程设计"或上课时间为空）
    time_raw = str(ws.cell(row=r, column=headers[COL_TIME]).value or '').strip()
    if '课程设计' in name or not time_raw:
        skipped.append((r, name, time_raw))
        continue
    places = [p.strip() for p in str(ws.cell(row=r, column=headers[COL_PLACE]).value or '').split(';')]
    teacher = str(ws.cell(row=r, column=headers[COL_TEACHER]).value or '').strip()
    schedules = []
    for i, seg in enumerate(time_raw.split(';')):
        parsed = parse_time(seg)
        if parsed:
            day, sp, ep, sw, ew, wt = parsed
            place = places[i] if i < len(places) else ''
            schedules.append((day, sp, ep, sw, ew, wt, place))
    imported.append((name, teacher, schedules))

print('\n=== 跳过（课程设计/无时间）===')
for s in skipped:
    print(' ', s)
print('\n=== 导入 ===')
for name, teacher, scheds in imported:
    print(f'  {name} | {teacher} | {len(scheds)} 时段')
    for s in scheds:
        print(f'    周{s[0]} 第{s[1]}-{s[2]}节 周{s[3]}-{s[4]} type={s[5]} @{s[6]}')
