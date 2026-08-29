# Memory.md — Build Progress Log

> This file is the AI's persistent memory across sessions/tools. Update it at the end of every work session — don't wait to be asked. Keep entries short and factual; this file exists so the AI never has to re-read the whole codebase to know where things stand.

### How to use this file
- Add a new dated entry every session, at the top (most recent first).
- Mark each phase from `Phases.md` as `Not Started / In Progress / Done`.
- Note any deviations from `Architecture.md`, `Rules.md`, or `Design.md` and why.
- Note open questions or blockers so the next session picks up instantly.

---

### Phase Status
| Phase | Status |
|---|---|
| 0 — Project Setup | Not Started |
| 1 — Auth & Onboarding | Not Started |
| 2 — Sensor Monitoring | Not Started |
| 3 — Rule-Based Detection | Not Started |
| 4 — Confirmation UI & Local SOS | Not Started |
| 5 — Backend & Notifications | Not Started |
| 6 — Employer/Family Dashboard | Not Started |
| 7 — ML Model Integration | Not Started |
| 8 — Additional Features | Not Started |
| 9 — Testing & Hardening | Not Started |
| 10 — Demo & Deployment Prep | Not Started |

---

### Reference Docs
- `RESQGO_Beginner_Guide_Phases_0-2.md` — beginner walkthrough of Project Setup, Auth & Onboarding, Sensor Monitoring.
- `RESQGO_Beginner_Guide_Phases_3-6.md` — beginner walkthrough of Detection Engine, Confirmation UI & Local SOS, Backend & Notifications, Employer/Family Dashboard.
- `RESQGO_Beginner_Guide_Phases_7-10.md` — beginner walkthrough of ML Model Integration, Bonus Features, Testing & Hardening, Demo & Deployment Prep.

---

### Session Log

**[YYYY-MM-DD] — Session 3**
- Status: Final beginner-friendly build guide created, covering Phases 7 through 10 — completes the full roadmap from Phase 0 to Phase 10 across three guide files.
- Note: Still **learning/instructional guides only** — no actual app code has been written into a repo yet, so Phase Status below remains unchanged until real implementation begins.
- Next up: Start actually building Phase 0 using the first guide, then work forward one phase at a time.
- Open questions / blockers: None yet.

**[YYYY-MM-DD] — Session 2**
- Status: Two beginner-friendly build guides created, covering Phases 0 through 6 in simplified, step-by-step language with sample Kotlin/JS code and checkpoints.
- Note: These are **learning/instructional guides only** — no actual app code has been written into a repo yet, so Phase Status below remains unchanged until real implementation begins.
- Next up: Start Phase 0 for real — install Android Studio, create the project, set up Firebase — using the Phase 0-2 guide.
- Open questions / blockers: None yet.

**[YYYY-MM-DD] — Session 1**
- Status: Project initialized. PRD, Architecture, Rules, Phases, and Design docs created.
- Decisions made: Kotlin (native Android) over Flutter; Node.js + Express over FastAPI; Firebase for Auth/Firestore/FCM (see `Architecture.md` §4 for reasoning).
- Next up: Phase 0 — repo setup, Firebase project creation.
- Open questions / blockers: None yet.

*(Add new entries above this line as work progresses.)*
