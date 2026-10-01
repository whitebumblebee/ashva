---
seq: 2
agent: codex
date: "2026-09-30T11:30:01Z"
task: relay-setup
status: done
summary: Completed Relay project context and four harness entry points with the user-selected next milestone.
next: "Claim user-test-guide, verify the Android debug APK, and write the installation and user walkthrough guide."
supersedes: []
---

# relay-setup

## What I did

Initialized Relay in code mode for the existing project. Added AGENTS.md (Codex/Cursor), CLAUDE.md and the Claude skill pointer, .cursor/rules/relay.mdc with alwaysApply enabled, and .kiro/steering/relay.md. Completed .relay/PROJECT.md, tasks.md, config.json, and the adoption bootstrap; generated the history index through the CLI.

Recorded the user's explicit priority: chess correctness and opening identification after user testing. Recorded Git initialization as declined for now. Other product enhancement priorities remain undecided. The canonical Relay skill remains .agents/skills/relay; no dependencies or extra harnesses were installed.

## Commands run and their outcomes

- `node .agents/skills/relay/bin/relay.mjs init --harness codex,claude-code,cursor,kiro --name 'Opening Lab' --mode code`: exit 0; created nine Relay/harness artifacts and reported all four harnesses wired.
- `node .agents/skills/relay/bin/relay.mjs claim relay-setup --agent codex`: exit 0; task claimed with one-hour TTL.
- `node .agents/skills/relay/bin/relay.mjs index`: exit 0; regenerated index from the completed bootstrap.
- `node .agents/skills/relay/bin/relay.mjs doctor --strict`: exit 0; all checks passed, one history entry and three tasks at that point.
- Read the generated four harness surfaces: pointers resolve to .relay context and the project-local CLI; Cursor alwaysApply frontmatter is present.
- App/build gates are not claimed as current validation in this setup log. They belong to user-test-guide, which follows setup.

## External resources touched

None. Local Relay initialization does not touch the network. No commits, pushes, publications, paid resources, or credentials were used.

## Risks, warnings, and what is NOT done

The harness files were inspected on disk; no new sessions were launched inside Claude Code, Cursor, or Kiro to demonstrate automatic loading. Relay coordinates through files and claims; it does not replace Git. The app is still the existing prototype, with the limitations recorded in bootstrap and PROJECT.md. App code was not edited.

## Next

Run `node .agents/skills/relay/bin/relay.mjs claim user-test-guide --agent codex`, then verify the Android debug APK and write the installation plus Ruy López user walkthrough. Keep the chess-correctness development task unclaimed during documentation/testing.
