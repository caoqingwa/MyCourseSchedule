import zipfile
import re

path = r'F:\Projects\CourseSchedule\课程表.xlsx'
z = zipfile.ZipFile(path)
print('=== zip 内容 ===')
for n in z.namelist():
    print(' ', n)

# 查看 sharedStrings 结构（决定解析方式）
if 'xl/sharedStrings.xml' in z.namelist():
    ss = z.read('xl/sharedStrings.xml').decode('utf-8')
    print('\n=== sharedStrings 前 800 字符 ===')
    print(ss[:800])
