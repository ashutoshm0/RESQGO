# Architecture.md — RESQGO

### 1. High-Level System Overview

```
┌───────────────────────┐
│   Rider's Android App │
│  ┌──────────────────┐ │
│  │  Sensor Manager   │ │  Accelerometer, Gyroscope, GPS
│  └─────────┬────────┘ │
│            │           │
│  ┌─────────▼────────┐ │
│  │ Detection Engine  │ │  Rule-based (MVP) → TFLite model (later)
│  └─────────┬────────┘ │
│            │           │
│  ┌─────────▼────────┐ │
│  │ Confirmation UI   │ │  20s "I'm OK" countdown
│  └─────────┬────────┘ │
└────────────┼──────────┘
             │ (no response / accident confirmed)
             ▼
┌────────────────────────────┐
│      Firebase Backend       │
│   Firestore · Auth · FCM    │
└────────────┬────────────────┘
             │
   ┌─────────┼──────────────┬────────────────┐
   ▼         ▼              ▼                ▼
Emergency  Employer     SMS/Call         (Future)
Contact    Dashboard    Gateway          Ambulance API
(push)     (web)        (Twilio/etc.)
```

### 2. App Flow (Rider Journey)
1. **Onboarding:** Phone number login → emergency contact setup → set working hours → permissions (location "always", motion sensors, phone/SMS).
2. **Ride start:** Rider taps "Go Online" (or auto-detected via working-hours schedule) → foreground service starts monitoring.
3. **Monitoring loop:** Sensor Manager reads accelerometer/gyroscope/GPS at adaptive intervals → feeds Detection Engine.
4. **Event trigger:** Detection Engine flags a possible accident/fall → Confirmation UI shown with siren + 20s countdown.
5. **Resolution:**
   - Rider taps "I'm OK" → event logged as false positive, monitoring resumes.
   - No response → SOS Flow triggered.
6. **SOS Flow:** Call emergency contact → send SMS with live GPS link → push alert to employer dashboard → (future) notify nearby ambulance service.
7. **Ride end:** Rider taps "Go Offline" → monitoring stops, day's safety score calculated.

### 3. Folder Structure

**Android App (Kotlin)**
```
app/
├── src/main/java/com/resqgo/app/
│   ├── sensors/
│   │   ├── SensorManagerWrapper.kt
│   │   ├── AccelerometerListener.kt
│   │   └── GyroscopeListener.kt
│   ├── detection/
│   │   ├── RuleBasedDetector.kt
│   │   ├── MLDetector.kt          # TFLite, post-MVP
│   │   └── DetectionResult.kt
│   ├── service/
│   │   └── RideMonitoringService.kt   # Foreground service
│   ├── ui/
│   │   ├── onboarding/
│   │   ├── home/
│   │   ├── confirmation/          # "I'm OK" screen
│   │   └── settings/
│   ├── sos/
│   │   ├── SOSManager.kt
│   │   ├── EmergencyCall.kt
│   │   └── LocationSharer.kt
│   ├── data/
│   │   ├── repository/
│   │   ├── firebase/
│   │   └── local/                 # Room DB for offline queue
│   └── voice/
│       └── VoiceTriggerService.kt  # "Help" hotword detection
├── build.gradle
└── AndroidManifest.xml
```

**Backend (Node.js)**
```
backend/
├── src/
│   ├── routes/
│   │   ├── sos.routes.js
│   │   ├── employer.routes.js
│   │   └── rider.routes.js
│   ├── services/
│   │   ├── notification.service.js   # FCM
│   │   ├── sms.service.js            # Twilio/SMS gateway
│   │   └── firestore.service.js
│   ├── middleware/
│   └── index.js
├── package.json
└── .env.example
```

### 4. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Mobile app | Kotlin (native Android) | Reliable background sensor + service access; cross-platform frameworks add a bridge layer that risks weakening background detection reliability |
| Sensors | Android SensorManager + FusedLocationProvider | Native, battery-optimized APIs |
| On-device ML | TensorFlow Lite | Runs inference offline, low latency, no raw data leaves device |
| Backend | Node.js + Express | Pairs naturally with Firebase Admin SDK; large ecosystem for SMS/call gateway integrations |
| Database | Firebase Firestore | Real-time sync for dashboard, fast MVP setup |
| Auth | Firebase Auth (phone OTP) | Matches gig-worker context, no password friction |
| Push notifications | Firebase Cloud Messaging | Employer/family alerts |
| Maps | Google Maps SDK | Live location + route risk heatmap |
| SMS/Call | Twilio (or local SMS gateway) | SOS delivery when data is weak |

> **Assumption flagged:** Defaulted to native Kotlin over Flutter, and Node.js over FastAPI, since background sensor reliability and tight Firebase integration matter more than cross-platform reach for a hackathon MVP. Update this table if your team prefers otherwise — nothing downstream depends on this specific choice except the folder structure above.

### 5. Data Flow for a Detection Event
1. Sensors stream raw data → Detection Engine (on-device, real-time, no network needed).
2. On trigger, Confirmation UI shown locally (works even offline).
3. If no "I'm OK" response: event + GPS payload written to local Room DB (offline queue) → synced to Firestore when online → backend route fires SOS actions (call, SMS, push).

### 6. Third-Party Integrations
- **Google Maps SDK** — live location display, route risk heatmap.
- **Twilio (or equivalent)** — emergency call placement + SMS delivery.
- **Firebase suite** — Auth, Firestore, Cloud Messaging.
- **(Future)** Local ambulance/hospital dispatch APIs, insurance partner APIs.

### 7. Scalability Notes
- Detection logic must run 100% on-device — the backend should never be a dependency for the crash-to-confirmation step (only for the follow-up SOS actions).
- Firestore structure should partition by `rides/{rideId}/events/{eventId}` to keep dashboard queries fast per rider/employer.
- Design the SOS action pipeline as a queue (Cloud Function trigger) so a burst of simultaneous accidents (unlikely but possible) doesn't bottleneck notification delivery.
