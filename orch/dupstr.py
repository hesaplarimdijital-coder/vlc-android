# dupstr.py <worktree>: strings.xml files that do not parse or hold a name twice (union merges can do both)
import re,glob,collections,sys
import xml.dom.minidom as M
bad=0
for p in glob.glob(sys.argv[1]+'/application/*/src/main/res/values*/strings.xml'):
    s=open(p).read()
    try: M.parseString(s.encode())
    except Exception as e: print(p,'XML:',e); bad=1
    names=re.findall(r'<(?:string|plurals|string-array) name="([^"]+)"',s)
    d=[n for n,c in collections.Counter(names).items() if c>1]
    if d: print(p,'dup:',d); bad=1
sys.exit(bad)
