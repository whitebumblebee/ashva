---
seq: 13
agent: codex
date: "2026-10-01T15:47:39Z"
task: public-alpha-readiness
status: done
summary: Applied owner-selected Apache-2.0 with Ashva NOTICE and separate upstream scopes; 17 tooling tests and public/secret/CI/Relay checks pass. Public-source preparation complete; Git and hosted CI remain owner-managed.
next: Claim repertoire-coverage and connect installed taxonomy to source-attributed opening search and explicitly bounded repertoire routes without inventing teaching plans.
supersedes: []
---

# public-alpha-readiness

## What I did

- The owner explicitly approved Apache-2.0 in response to the license-selection question. Claimed the remaining `public-alpha-readiness` task and applied that choice locally; no further license decision is pending.
- Added root LICENSE as an exact copy of the retained official Apache-2.0 terms. The standard appendix stays unchanged as an application example. Added Ashva NOTICE with Copyright 2026 Shishir Jha and SPDX-License-Identifier: Apache-2.0, stating the original-code/documentation/assets scope and upstream exclusions without adding license restrictions.
- Updated README, CONTRIBUTING, LICENSES/README, THIRD_PARTY_NOTICES, PUBLIC_READINESS and current Relay PROJECT/tasks to reflect the selected license. Contributors get explicit Apache-2.0 scope, new-original-file SPDX guidance and notice/modification obligations; source data and vendored tooling keep their original rights. Historical decision logs remain unchanged.
- Public audit now requires NOTICE and treats it as text for privacy checking. Added one regression test for exact license-term preservation, actual Ashva copyright/SPDX declaration and separate CC0/CC-BY-SA/Relay MIT scopes. Seventeen Node tooling tests total.
- This completes authorized local early-alpha public-source preparation. It does not publish the source, run hosted CI, certify trademark/security/production binary compliance, implement full repertoires or change app behavior. Personal-path redaction from log 0012 remains intact.

## Commands run and their outcomes

- `relay status`: twelve existing history entries, nine TODO / one BLOCKED / nine DONE; no Git repository. `relay claim public-alpha-readiness --agent codex` succeeded and was renewed between stages.
- `node --test scripts/*.test.mjs`: **17 passed / 0 failed / 0 skipped**, repeated after the final notice/readiness text edits. The new test confirms root LICENSE is byte-for-byte identical to LICENSES/Apache-2.0.txt and NOTICE retains separate upstream scopes.
- `node scripts/public-audit.mjs`: **176 candidate files / 0 findings / exit 0** before this handoff log, including LICENSE/NOTICE and historical privacy checks. Previous root-license blocker is cleared. Rerun against the final handoff/task state before completing the task.
- `node scripts/run-secret-scan.mjs`: checksum-pinned Gitleaks 8.30.1, **0 findings / exit 0**; no matched values/files uploaded. Rerun after writing this completion log to verify the final tree.
- `actionlint -shellcheck= .github/workflows/ci.yml` using the previously verified external tool: **exit 0 / no diagnostics**. CI workflow itself is unchanged; hosted execution is still unverified.
- `relay doctor --strict`: **exit 0**, twelve entries / nineteen tasks before this log. Regenerate the index, cite 0013 when marking done and rerun strict doctor for the final handoff.
- No Android/common/domain/content bytes, dependencies, package/database identifiers, screenshots, emulator install or learner data changed. App/runtime/content build evidence remains log 0011, not a fresh rerun for licensing/tooling-only edits. Relevant tooling gates were rerun here.

## External resources touched

- Official Apache application guidance: https://www.apache.org/foundation/license-faq.html . Retained terms originally sourced from https://www.apache.org/licenses/LICENSE-2.0.txt . No custom license clauses or Apache Foundation ownership/endorsement claims added.
- Verified temporary Gitleaks download/execution and existing actionlint tool for local checks. No Git initialization/status/staging/commit/remote/push, account changes, external messages, production secrets, payments, publication or deployment.
- Existing private original-history backup and ignored runtime-location file were preserved, not copied into public context. No emulator actions or upstream chess-data downloads.

## Risks, warnings, and what is NOT done

- Original Ashva code/documentation/assets are Apache-2.0. Do not apply it to CC0 taxonomy, CC-BY-SA raw/derived broadcasts, vendored Relay MIT, other dependency notices, system fonts or other upstream material. Preserve source manifests/checksums/ATTRIBUTION bytes.
- All Git and actual publication remain exclusively owner-managed. The owner should review staged files/screenshots and any new Git history, keep ignored credentials/databases/backups out, configure repository security settings and inspect both hosted CI jobs after pushing. Local validation is not hosted-CI evidence.
- Public readiness is for an honestly labeled early Android source alpha, not a complete repertoire teacher or signed app-store release. Prior four dependency-update lint warnings and Room language warning remain documented. No engine/GM-game coaching/performance equivalence/iOS-client/legal/trademark/security certification is claimed.
- Scans are directory-based and do not certify secret absence, ownership or security; they do not inspect pixels or future Git history/staging overrides. External original-history backup is temporary storage that may be purged; preserve it privately elsewhere if durable recovery is needed. Actual paths remain only in ignored local configuration.

## Next

When the owner requests the next development pass, claim `repertoire-coverage` through `node .agents/skills/relay/bin/relay.mjs claim repertoire-coverage --agent <you>`. Read current source/storage/learning requirements and connect installed taxonomy to source-attributed opening search and explicitly bounded repertoire routes. Retain every source route/depth, offline access and honest unreviewed/incomplete coverage; do not fabricate White/Black teaching plans. Use an isolated exact-serial device for instrumentation, never the owner's learner install. Do not perform Git/publication operations.
