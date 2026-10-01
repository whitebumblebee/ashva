---
seq: 12
agent: codex
date: "2026-10-01T15:06:59Z"
task: public-alpha-readiness
status: blocked
summary: "Redacted eight personal-path findings with owner approval and preserved external originals; 16 tooling tests and secret scan pass, with only app-code license selection still pending."
next: "Obtain the owner's explicit app-code license selection, add the root license and matching scope/notices, then rerun public/secret/Relay gates to complete public-alpha-readiness before repertoire-coverage."
supersedes: []
---

# public-alpha-readiness

## What I did

- Followed the owner's explicit request to replace personal machine paths with placeholders. This authorizes a privacy-only exception to Relay's normal append-only rule; it does not authorize rewriting past technical facts or deleting history.
- Copied all eleven existing history entries, unchanged, to an external private temporary directory before editing. Replaced only literal SDK/project/temp-directory prefixes in logs 0003, 0006, 0009 and 0010 with `<android-sdk>`, `<project-root>`, `<pre-release-pack-backup-directory>` and `<isolated-test-avd-directory>`. Dates, outcomes, commands apart from path prefixes, limitations and historical status remain unchanged.
- Added ignored `.public-audit/local-environment.json` for private backup recovery and reusable isolated test/SDK paths. This is deliberately outside the publishable surface; never stage, force-add or publish it. Updated PROJECT, tasks and PUBLIC_READINESS to record the authorization, completed redaction and sole remaining license decision.
- Researched MIT, Apache-2.0 and MPL-2.0 using their primary license/maintainer sources. Apache-2.0 adds explicit limited contributor patent grants/termination, modification marking and notice obligations, but remains permissive and allows closed-source commercial derivatives. MPL-2.0's file-level copyleft requires covered source/modifications to remain available under MPL when distributed; separate new files can remain proprietary. The owner asked about Apache, but did not explicitly select it. No root license was silently applied.

## Commands run and their outcomes

- `relay claim public-alpha-readiness --agent codex` via the vendored Node CLI succeeded; renewed before the handoff.
- `node scripts/public-audit.mjs` before edits: 173 candidate files / 9 findings (eight personal-path findings and missing root LICENSE). After edits: 173 candidates / 1 finding, solely `LICENSE · required-public-file-missing`; exit 1 intentionally preserves the remaining readiness gate.
- Read-only Node assertion compared each of the four modified logs to its untouched external original after applying only the approved literal prefix substitutions: all four matched exactly. No unrelated historical text changed.
- `node --test scripts/*.test.mjs`: **16 passed / 0 failed / 0 skipped**. No tooling implementation or content bytes changed.
- `node scripts/run-secret-scan.mjs`: verified Gitleaks 8.30.1 downloaded/executed under approved network escalation, **0 findings**. Reports stay redacted; no matched values or files were uploaded.
- `actionlint -shellcheck= .github/workflows/ci.yml` using the previously verified external local tool: exit 0, no diagnostics. Workflow is unchanged.
- `relay doctor --strict`: exit 0 before this log, eleven entries / nineteen tasks. Regenerate the index, block on explicit license selection and rerun audit/secret/doctor after this body is filled.
- No Android/domain/Compose/iOS rebuild was run for documentation/privacy-only changes. Prior build/runtime evidence remains log 0011; this session makes no new app verification claim.

## External resources touched

- Official license references: https://opensource.org/license/mit ; https://www.apache.org/licenses/LICENSE-2.0.html ; https://www.apache.org/foundation/license-faq.html ; https://www.mozilla.org/en-US/MPL/2.0/FAQ/ . Read-only research, no accounts or posts.
- External private temporary copy of original Relay history; location recorded only in the ignored local environment file. Pinned Gitleaks release download and existing local actionlint tool, with no Git operations, secret upload, publishing or paid resources.
- No emulator was started, stopped, installed, tested or cleared. User emulator/learner state is untouched; reusable test runtime paths remain private local configuration.

## Risks, warnings, and what is NOT done

- `public-alpha-readiness` remains BLOCKED solely on the owner's explicit app-code license choice. Apache-2.0 is being discussed, not selected; root LICENSE remains absent. Do not mark the task done or represent it as publication-ready until the choice is applied and gates pass.
- Preserve separate CC0/CC-BY-SA data and vendored Relay MIT licensing. An app-code license does not relicense third-party content, nor clear future Stockfish distribution or trademark/name questions.
- Backup/test/pre-release-pack directories use temporary storage that the OS can purge. Tell the owner to preserve the original-history backup in durable private storage if long-term recovery is wanted. Never publish private runtime/recovery paths. Historical command placeholders must be substituted before execution.
- Scans do not certify legal ownership, inspect screenshot pixels or scan future Git history/staging overrides. All Git/account/publication work remains owner-managed; no hosted CI or binary-release compliance claim.

## Next

Obtain the owner's explicit app-code license selection. Then claim `public-alpha-readiness` with the vendored Relay CLI, add the root license and matching scope/notices without changing third-party rights, and rerun `node --test scripts/*.test.mjs`, `node scripts/public-audit.mjs`, `node scripts/run-secret-scan.mjs` and `relay doctor --strict`. Log and complete the readiness task only when its remaining gates pass, before returning to `repertoire-coverage`. Perform no Git operations.
