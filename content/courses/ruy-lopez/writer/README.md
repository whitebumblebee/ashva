# Ruy Lopez writer sources

Claude Code (agent mode) wrote the course text here. Running `ruy_claims.py` turns it into `../claims/<chapter>.json`, which `courseTools check` then verifies claim by claim.

| File | Contents |
| --- | --- |
| `ruy_intros.py` | Variation introductions (`IDEA` claims). `I` is keyed by variation name, `IP` by SAN move path. |
| `ruy_plans.py` | "How White/Black wins" plans (`WINPLAN` claims), keyed by move path. A plan passes only if every move it names is among that side's statistically distinctive winning moves (`../ideas/`). |
| `ruy_claims.py` | Generator: `python3 ruy_claims.py content/courses/ruy-lopez`. It skips the GAME chapter, whose claims come from the pilot (`content/courses/ruy-lopez-white/claims/`). |

Rebuild order: `toc → build → ideas → facts → (write) → ruy_claims.py → check → pack`. See [docs/DEEP_COURSE_PLAN.md](../../../../docs/DEEP_COURSE_PLAN.md).
