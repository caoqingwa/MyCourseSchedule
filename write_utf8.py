import codecs
import sys

# Read the content from stdin
content = sys.stdin.read()
filepath = sys.argv[1]

with codecs.open(filepath, 'w', 'utf-8') as f:
    f.write(content)
print(f"Written {len(content)} chars to {filepath}")
