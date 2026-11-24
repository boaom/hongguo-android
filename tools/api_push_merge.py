#!/usr/bin/env python3
"""通过 Git Data API 推送提交（含 merge 双 parent）。github.com:443 被阻时取代 git push。

用法：python3 tools/api_push_merge.py <parent1[,parent2]> <file> [file...]
      commit message 从 stdin 读。parent 必须是远端存在的 sha。
"""
import base64, json, os, subprocess, sys, urllib.request

TOKEN = os.environ["GITHUB_TOKEN"]
REPO = "boaom/hongguo-android"
BRANCH = "main"
BASE = f"https://api.github.com/repos/{REPO}"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def api(method, path, data=None):
    req = urllib.request.Request(
        BASE + path, method=method,
        data=json.dumps(data).encode() if data is not None else None,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as r:
        body = r.read()
        return json.loads(body) if body else {}


def remote_head():
    return api("GET", f"/git/ref/heads/{BRANCH}")["object"]["sha"]


def main(message, parents, paths):
    base_tree = api("GET", f"/git/commits/{parents[0]}")["tree"]["sha"]
    print(f"base {parents[0][:7]}  tree {base_tree[:7]}")
    entries = []
    for p in paths:
        full = os.path.join(ROOT, p)
        if not os.path.exists(full):
            entries.append({"path": p, "mode": "100644", "type": "blob", "sha": None})
            print(f"删除 {p}")
            continue
        with open(full, "rb") as fh:
            data = fh.read()
        blob = api("POST", "/git/blobs",
                   {"content": base64.b64encode(data).decode(), "encoding": "base64"})
        entries.append({"path": p, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print(f"blob {p} -> {blob['sha'][:7]} ({len(data)} B)")
    tree = api("POST", "/git/trees", {"base_tree": base_tree, "tree": entries})
    commit = api("POST", "/git/commits",
                 {"message": message, "tree": tree["sha"], "parents": parents})
    api("PATCH", f"/git/refs/heads/{BRANCH}", {"sha": commit["sha"]})
    print(f"已推送 {commit['sha'][:7]} -> {BRANCH}")


if __name__ == "__main__":
    msg = sys.stdin.read()
    ps = sys.argv[1].split(",")
    main(msg, ps, sys.argv[2:])
