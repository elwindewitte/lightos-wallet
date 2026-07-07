---
description: Sync the latest Light SDK changes from upstream on a branch, verify, then merge into main — pausing if anything conflicts
---

Sync this repo with the upstream Light SDK. The workflow is: bring upstream's
changes onto a **branch** first, verify everything still builds there, and only
then merge into `main`. Never merge upstream straight onto `main` — the branch is
what keeps `main` safe and gives us a place to test before trusting the sync.

I am new to git and cannot resolve conflicts myself, so your job is to keep me
safe and explain things in plain language, not to make merge decisions for me.
Follow these steps in order and do not skip the safety checks.

1. **Check for unsaved work.** Run `git status`. If there are any uncommitted
   changes, STOP immediately. Tell me to commit or stash first, and run nothing
   else — merging on top of unsaved work is how work gets lost.

2. **Pick the branch to sync on.** Tell me which branch I'm on, then make sure we
   sync on a branch — never directly on `main`:
   - If I'm already on a feature branch I'm working on, we can sync on it; the
     upstream changes will ride along with that branch's PR into `main`.
   - If I'm on `main`, do NOT merge here. Create a dedicated sync branch first
     with `git switch -c sync-upstream` (if that name already exists, add a short
     date suffix), and tell me you've done so and why.
   - Either way, state the branch we'll sync on before going further.

3. **Download upstream.** Run `git fetch upstream`. This downloads the latest SDK
   code without touching my files yet.

4. **Show me what's incoming.** Run `git log --oneline HEAD..upstream/main` and
   `git diff --stat HEAD...upstream/main`, and summarize in plain English.
   Specifically call out anything that affects how I build or what the SDK allows:
   build files (build.gradle, build.gradle.kts, libs.versions.toml,
   consumer-rules.pro), the build guardrail plugin under `plugin/`, the SDK's
   public API or UI components under `sdk/` (especially any my tool uses), and
   setup/docs. Also note whether any file I've changed overlaps with upstream's
   changes — that's what predicts a conflict.

5. **Merge.** Run `git merge upstream/main`.

6a. **If it merges cleanly:** tell me it worked and list the files that changed.

6b. **If there are conflicts:** STOP. Do NOT resolve them yourself — this is the
    most important rule in this command. List exactly which files conflict. For
    each, show me the conflicting section and explain, in plain language, what
    upstream changed versus what I have. Then give me two clear choices: (a) walk
    me through resolving a specific file, or (b) run `git merge --abort`, which
    safely undoes the entire merge and puts me back exactly where I started. Wait
    for my decision. Do not choose for me.

    Note on README.md: it is intentionally mine, not upstream's, and my
    build/setup instructions live in CLAUDE.md, not the README. So if README.md is
    the only conflict, the likely answer is to keep my version
    (`git checkout --ours README.md`) — but still show me upstream's change first
    so I can check whether they added anything real I'd want to copy over by hand.

7. **Verify before trusting it.** Once the merge is committed (clean or resolved),
   rebuild and run the guardrail checks: `./gradlew :tool:check`. If it fails —
   especially because the guardrail plugin was tightened or an SDK component my
   tool uses changed — tell me plainly and stop; do not push a broken sync.
   Remind me that full confidence still needs a test on the phone or the LightOS
   emulator before I rely on it.

8. **Merge the branch into main.** Only after it builds:
   - Push the branch with `git push -u origin HEAD`.
   - If a PR for this branch already exists, it's now updated — point me to it.
     Otherwise open one into main, e.g.
     `gh pr create --base main --title "Sync upstream SDK"` (or fold the sync into
     the feature branch's existing PR).
   - Pushing my own verified branch and opening/updating its PR is routine, so you
     can do that without asking. But summarize what will land on `main` and let me
     click the final merge myself, unless I explicitly tell you to merge it for me.
