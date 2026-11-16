#!/bin/sh
# 上游取流修复监控：每次触发打印三个参考实现的最新提交与 issue 动向。
# 有 11-16 之后的新提交（尤其涉及 playback / sign / app-source）就值得立刻跟进。
set -u
TOKEN="$GITHUB_TOKEN"
API="https://api.github.com"
SINCE="2026-11-16T00:00:00Z"

for repo in tsukiyo-zero/hongguoTV jackotom/hongguo-drama sunnysky123/hongguo-web; do
  echo "=== $repo"
  echo "-- 新提交（自 $SINCE）:"
  curl -s -H "Authorization: Bearer $TOKEN" "$API/repos/$repo/commits?since=$SINCE&per_page=5" \
    | python3 -c '
import json,sys
try:
    d=json.load(sys.stdin)
except Exception:
    print("  （解析失败）"); raise SystemExit
if not d: print("  （无）")
for c in d[:5]:
    print("  ", c["commit"]["committer"]["date"], c["sha"][:7], c["commit"]["message"].split("\n")[0][:70])
'
  echo "-- 最近更新的 issue:"
  curl -s -H "Authorization: Bearer $TOKEN" "$API/repos/$repo/issues?state=all&sort=updated&per_page=3" \
    | python3 -c '
import json,sys
try:
    d=json.load(sys.stdin)
except Exception:
    print("  （解析失败）"); raise SystemExit
for it in d[:3]:
    kind = "PR" if "pull_request" in it else "IS"
    print("  ", it["updated_at"][:16], kind, "#%s" % it["number"], it["title"][:60])
'
done
echo
echo "判断标准：出现 11-16 之后涉及 playback/sign/取流 的新提交或维护者新回复 => 需要跟进"