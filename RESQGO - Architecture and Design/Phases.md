# Phases.md — Build Roadmap

> Complete phases in order and update `Memory.md` after each one. Don't start a phase until the previous phase's core deliverable works end-to-end.

### Phase 0 — Project Setup
- Initialize Android project (Kotlin) + Node.js backend repo.
- Set up Firebase project (Auth, Firestore, FCM).
- Set up version control, environment configs, `.env.example`.
- **Deliverable:** Empty app builds and runs; backend responds to a health-check endpoint.

### Phase 1 — Authentication & Onboarding
- Phone number OTP login (Firebase Auth).
- Onboarding flow: emergency contact entry, working-hours setup, permission requests (location, motion, phone/SMS).
- **Deliverable:** New user can sign up, add an emergency contact, and reach the home screen.

### Phase 2 — Sensor Monitoring
- Build `SensorManagerWrapper` to stream accelerometer, gyroscope, GPS speed.
- Implement foreground `RideMonitoringService` with "Go Online/Offline" toggle.
- **Deliverable:** App logs live sensor readings while "online," survives screen lock.

### Phase 3 — Rule-Based Detection Engine
- Implement threshold logic (speed drop, impact g-force, rotation, post-impact stillness).
- Tune thresholds against test scenarios (hard brake vs. crash vs. drop).
- **Deliverable:** Detector correctly flags simulated crash/fall test cases without excessive false positives.

### Phase 4 — Confirmation UI & Local SOS
- Build "Possible accident detected — Tap I'm OK" screen with 20s countdown + siren/vibration.
- On timeout, trigger local actions: auto-call emergency contact, prepare SMS with GPS link.
- **Deliverable:** Full offline-capable detect → confirm/timeout → local SOS action loop works.

### Phase 5 — Backend & Notifications
- Build Firestore schema (`riders`, `rides`, `events`).
- Backend routes for SOS event ingestion, employer/family push notifications, SMS via Twilio.
- **Deliverable:** Confirmed accident event reaches backend and triggers a push notification + SMS.

### Phase 6 — Employer/Family Dashboard
- Simple web dashboard (minimal React app is fine) showing active riders, live status, event history.
- **Deliverable:** Employer can see rider's online/offline status and any triggered events in near real-time.

### Phase 7 — ML Model Integration (Stretch Goal)
- Collect/label sample sensor data (real or synthetic).
- Train a lightweight model (Random Forest or 1D CNN) → convert to TensorFlow Lite.
- Swap or blend with the rule-based detector.
- **Deliverable:** On-device model runs inference alongside rule-based logic; compare accuracy.

### Phase 8 — Additional Features (Stretch Goals)
- Voice-activated "Help" trigger (works when phone is locked).
- Daily safety score calculation.
- Route risk analysis / heatmap.
- **Deliverable:** At least one stretch feature demoable.

### Phase 9 — Testing & Hardening
- Battery drain testing over multi-hour sessions.
- False-positive tuning using real-world test rides.
- Edge cases: no internet, phone in backpack, low battery.
- **Deliverable:** App passes the test checklist defined in `Rules.md` §8.

### Phase 10 — Demo & Deployment Prep
- Polish UI, record demo video/script, prepare pitch deck.
- Package APK for judges/testers; deploy backend to a public URL (Render/Railway/Cloud Run).
- **Deliverable:** End-to-end live demo ready — simulate a crash and show the full SOS flow.
