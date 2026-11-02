# Contributing

## Commit messages

Write a short subject that says what the commit does. Put the detail in the body as bullets. Use the imperative for the subject and for every bullet (`Add`, `Fix`, `Remove`), and leave the trailing period off the subject.

```
[#1] Add CandleGapDetector to the architecture docs

- Keep bar-to-candle conversion in Candle.from
- Make CandleGapDetector the only owner of gap detection
- Return cached raw bars when the bar cache already has them
```

One subject, one concern. If a commit has no related issue, then write [none] as prefix.

## Issue prefixes

A branch can hold commits for more than one issue. Each commit names the one issue it moves forward.

| Prefix | Meaning |
| --- | --- |
| `[#1]` | This commit does part of issue #1. The issue is still unfinished on the branch. |
| `[developed #1]` | This commit finishes issue #1 on the branch. Moves the issue to "developed" on GitHub. |
| `closes #1` | Moves the issue to "done" on GitHub once the commit is on `main`. |

`[#1]` and `[developed #1]` do not close the issue. Do not write `closes`, `fix`, or `resolve` on a branch commit.

## Merge commits

Merge a pull request into `main` with a merge commit. The subject sums up everything the pull request did. The body only lists the issues that merge closes, one per line:

```
Merge PR #3: Add docs for 01 iteration
    closes #1
    closes #2
```

A branch that finishes two issues then reads:

```
[#1] Add the module diagram
[developed #1] Describe each backend module
[#2] Draft the physical architecture
[developed #2] Add the deployment diagram
Merge PR #3: Add docs for 01 iteration
    closes #1
    closes #2
```
