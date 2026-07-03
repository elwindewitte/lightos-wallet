---
description: Pull the latest Light SDK changes from upstream, pausing if anything conflicts
---

Sync this repo with the upstream Light SDK. Follow these steps in order and do
not skip the safety checks. I am new to git and cannot resolve conflicts myself,
so your job is to keep me safe and explain things in plain language, not to make
merge decisions for me.

1. Run `git status`. If there are any uncommitted changes, STOP immediately.
   Tell me I need to commit or stash first, and run nothing else — merging on top
   of unsaved work is how work gets lost.

2. Confirm I'm on the `main` branch. If I'm not, stop and tell me which branch
   I'm on before doing anything.

3. Run `git fetch upstream`. This downloads the latest SDK code without touching
   my files yet.

4. Before merging, show me what's incoming: run
   `git log --oneline main..upstream/main` and summarize it in plain English.
   Specifically call out if any of these changed, since they affect how I build
   or what the SDK allows: build files (build.gradle, build.gradle.kts,
   libs.versions.toml), setup/docs, or the SDK's public API.

5. Attempt the merge with `git merge upstream/main`.

6a. If it merges cleanly: tell me it worked, list the files that changed, flag
    anything I should re-check, and remind me to rebuild and test on the phone
    before trusting it.

6b. If there are conflicts: STOP. Do NOT resolve them yourself — this is the most
    important rule in this command. List exactly which files conflict. For each,
    show me the conflicting section and explain, in plain language, what upstream
    changed versus what I have. Then give me two clear choices: (a) walk me
    through resolving a specific file, or (b) run `git merge --abort`, which
    safely undoes the entire merge and puts me back exactly where I started.
    Wait for my decision. Do not choose for me.

Note on README.md: it is intentionally mine, not upstream's, and my build/setup
instructions live in CLAUDE.md, not the README. So if README.md is the only
conflict, the likely answer is to keep my version — but still show me upstream's
README so I can check whether they added real
