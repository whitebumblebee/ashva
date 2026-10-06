# Deep courses

Build inputs, evidence and published packs for generated deep courses. The method, phases and rules are in [the deep course plan](../../docs/DEEP_COURSE_PLAN.md); tooling is the `courseTools` module and `scripts/fetch-course-data.mjs`.

Per course (`<course-id>/`):

| Path | Contents |
| --- | --- |
| `course.config.json` | Chapters, thresholds and learner-move overrides |
| `inputs/` | Download manifests (URLs, byte ranges, SHA-256) and filter reports for the exact data used |
| `tree/` | Deterministic line trees and coverage reports (Phase 5) |
| `facts/` | Engine facts per edge: threats, prevented moves, losses, continuations (Phase 3) |
| `claims/` | Writer claims (agent mode) keyed by SAN path (Phase 8) |
| `checked/` | Every claim with its pass/fail result and reason (Phase 7) |
| `game/` | Selected original game metadata and critical moments (Phase 11) |
| `eval/` | Puzzle sample, engine-derived gold set and metric results (Phase 4) |
| `v<version>/` | Published `course.json` plus `course.manifest.json`; the app bundles only `course.json` and checks its SHA-256 |

Raw downloads, filtered game tables and the engine cache stay in the ignored `.course-cache/` directory; they are reproducible from the manifests.

Licensing: club statistics derive from CC0 Lichess games; master statistics and the original game derive from CC BY-SA 4.0 Lichess broadcasts. A course pack therefore carries CC BY-SA 4.0 with attribution (see `THIRD_PARTY_NOTICES.md` and each pack's provenance block). Generated text is labelled as generated and is not a human coach's or player's words.
