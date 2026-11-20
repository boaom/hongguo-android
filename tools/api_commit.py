#!/usr/bin/env python3
"""通过 GitHub Git Data API 提交变更（绕过被阻断的 github.com:443 git 传输）。"""
import base64, json, os, subprocess, sys, urllib.request

TOKEN = os.environ["GITHUB_TOKEN"]
REPO = "boaom/hongguo-android"
BRANCH = "main"
BASE = f"https://api.github.com/repos/{REPO}"


def api(method, path, data=None):
    req = urllib.request.Request(
        BASE + path, method=method,
        data=json.dumps(data).encode() if data is not None else None,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        body = r.read()
        return json.loads(body) if body else {}


def local_bytes(path):
    with open(path, "rb") as fh:
        return fh.read()


def main(message, paths):
    head = api("GET", f"/git/ref/heads/{BRANCH}")
    head_sha = head["object"]["sha"]
    commit = api("GET", f"/git/commits/{head_sha}")
    base_tree = commit["tree"]["sha"]
    print(f"HEAD {head_sha[:7]}  tree {base_tree[:7]}")

    entries = []
    for p in paths:
        data = local_bytes(p)
        blob = api("POST", "/git/blobs",
                   {"content": base64.b64encode(data).decode(), "encoding": "base64"})
        entries.append({"path": p, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print(f"blob {p} -> {blob['sha'][:7]} ({len(data)} bytes)")

    tree = api("POST", "/git/trees", {"base_tree": base_tree, "tree": entries})
    new_commit = api("POST", "/git/commits",
                     {"message": message, "tree": tree["sha"], "parents": [head_sha]})
    api("PATCH", f"/git/refs/heads/{BRANCH}", {"sha": new_commit["sha"]})
    print(f"已提交 {new_commit['sha'][:7]} -> {BRANCH}")


if __name__ == "__main__":
    message = sys.stdin.read()
    paths = sys.argv[1:]
    if not paths:
        sys.exit("需要至少一个文件路径")
    main(message, paths)