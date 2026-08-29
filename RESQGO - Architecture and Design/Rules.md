# Rules.md — AI Coding Assistant Boundaries

These rules apply to any AI tool (Claude, Cursor, Copilot, etc.) working on this codebase.

### 1. General Principles
- Never remove or weaken the 20-second confirmation/safety flow without explicit human approval — this is a safety-critical feature.
- Prefer readability and explicitness over clever one-liners; this app has real-world safety implications and must be easy to audit.
- Every change touching `detection/` or `sos/` must include or update a corresponding test.
- Do not introduce a new third-party dependency without checking it against Section 3 (Libraries — Avoid).

### 2. Libraries — Use
- **Android:** Kotlin Coroutines, Android Jetpack (Lifecycle, WorkManager, Room), Google Play Services Location, TensorFlow Lite.
- **Backend:** Express, Firebase Admin SDK, Twilio SDK, Joi (validation), dotenv.
- **Testing:** JUnit + Espresso (Android), Jest (Node backend).

### 3. Libraries — Avoid
- Avoid heavy cross-platform frameworks (React Native, Flutter) for the core sensor/detection module unless the whole app is being re-architected — background reliability on Android is the priority.
- Avoid unmaintained or single-maintainer npm packages for anything touching SOS delivery (call/SMS) — use Twilio or another established provider.
- Avoid storing any raw sensor data in cloud storage; process on-device and only sync derived events (see Section 5, Privacy).

### 4. Error Handling
- All sensor read failures must fail safe: if a sensor becomes unavailable, log it and continue monitoring with remaining sensors rather than crashing the service.
- All network calls (SOS delivery, Firestore sync) must have retry logic with exponential backoff, and must queue locally on failure — never silently drop an SOS event.
- User-facing errors (e.g., permissions denied) must show a clear, non-technical message with a fix action (e.g., "Location permission needed — tap to enable").

### 5. Privacy & Security Rules
- Location tracking is only allowed during rider-declared working hours or an active SOS event — never track passively outside these windows.
- Raw accelerometer/gyroscope data must never be uploaded to any backend; only detection *results* (event type, timestamp, GPS) may sync.
- Emergency contact phone numbers and any personal data must be stored encrypted at rest (Firestore + Firebase encryption defaults), with no plaintext logs of PII.
- Never hardcode API keys, Firebase config secrets, or Twilio credentials in source — use environment variables / `google-services.json` kept out of version control.

### 6. Code Style
- Kotlin: follow the official Kotlin style guide; use coroutines over callbacks/threads for async sensor work.
- Node.js: use async/await, avoid callback pyramids, ESLint with a standard config.
- Commit messages: `[phase-x] short description` to keep traceability with `Phases.md`.

### 7. What the AI Must NOT Do
- Do not auto-merge or auto-generate the ML model training pipeline without a human reviewing training data quality first — false negatives here are a safety risk.
- Do not disable battery-optimization workarounds (foreground service, wake locks) to "simplify" code — they exist because Android will kill background detection otherwise.
- Do not fabricate sensor thresholds or accuracy numbers in documentation/marketing copy — flag them as "to be validated" until real test data exists.
- Do not add analytics/tracking SDKs beyond what's declared in `PRD.md` without flagging it for privacy review.

### 8. Testing Requirements
- `RuleBasedDetector` must have unit tests covering: normal ride (no trigger), hard brake (no trigger), simulated crash (trigger), simulated fall (trigger), phone dropped on soft surface (no trigger).
- SOS flow must be tested with mocked Twilio/FCM responses, including failure/retry paths.
- Any new detection threshold change must be run against the existing test dataset before merging.
