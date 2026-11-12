"""ktsyntax.py <worktree> [base-ref]: counts tree-sitter ERROR/MISSING nodes in every Kotlin file changed against
base-ref (default origin/master) and compares with the same file at base-ref. A count above the base's is a
syntax slip (the grammar has a few known false positives, the same on both sides)."""
import subprocess, sys
import tree_sitter, tree_sitter_kotlin

wt = sys.argv[1]
base = sys.argv[2] if len(sys.argv) > 2 else "origin/master"
lang = tree_sitter.Language(tree_sitter_kotlin.language())
parser = tree_sitter.Parser(lang)

def errors(src: bytes):
    tree = parser.parse(src)
    found = []
    stack = [tree.root_node]
    while stack:
        n = stack.pop()
        if n.type == "ERROR" or n.is_missing:
            found.append((n.start_point[0] + 1, n.type, src[n.start_byte:n.end_byte][:60]))
        stack.extend(n.children)
    return sorted(found)

files = subprocess.run(["git", "-C", wt, "diff", "--name-only", f"{base}...HEAD"], capture_output=True, text=True, check=True).stdout.split()
bad = 0
for f in files:
    if not f.endswith(".kt"):
        continue
    now = errors(open(f"{wt}/{f}", "rb").read())
    old = subprocess.run(["git", "-C", wt, "show", f"{base}:{f}"], capture_output=True)
    before = errors(old.stdout) if old.returncode == 0 else []
    mark = "ok " if len(now) <= len(before) else "BAD"
    if len(now) > len(before):
        bad += 1
    print(f"{mark} {f.split('/')[-1]}: {len(now)} now, {len(before)} before")
    if len(now) > len(before):
        for e in now:
            print("     ", e)
print("syntax slips:", bad)
