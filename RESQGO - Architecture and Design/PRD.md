# PRD.md — RESQGO
## Project Requirements Document

### 1. Project Summary
**Name:** RESQGO
**One-liner:** An AI-powered mobile safety app that automatically detects road accidents and falls for delivery and ride-hailing gig workers, then triggers emergency alerts within seconds — without the rider having to do anything.

### 2. Problem Statement
Gig workers (Zomato, Swiggy, Blinkit, Zepto, Uber, Rapido riders, etc.) spend hours on the road daily and face significantly higher accident risk than the general public. When an accident happens:
- The rider may be unconscious or unable to call for help.
- Family and employer have no immediate way of knowing.
- Every minute of delay in emergency response increases risk to life (the "golden hour").

Existing solutions (Apple Crash Detection, Google Car Crash Detection, smartwatch fall detection) are general-purpose, not built for the specific context of two-wheeler gig delivery work (helmet use, bike mounts, backpacks, long working hours, low individual purchasing power for dedicated hardware).

### 3. Target Users
- **Primary:** Delivery and ride-hailing gig workers (2-wheeler riders) — Zomato, Swiggy, Blinkit, Zepto, Uber, Rapido, Ola, Porter, etc.
- **Secondary:** Family members / emergency contacts of riders.
- **Tertiary:** Gig platform employers / fleet operators who want to reduce liability and improve rider retention through safety.
- **Future:** Insurance providers offering usage-based safety discounts.

### 4. Goals & Success Metrics
| Goal | Metric |
|---|---|
| Detect accidents reliably | >90% detection recall on test crash/fall dataset |
| Minimize false positives | <1 false alarm per rider per week in real-world pilot |
| Fast emergency response | SOS triggered within 20–30 seconds of impact |
| Rider adoption | Battery drain <5%/hour of active monitoring |
| Employer trust | Dashboard shows live safety status for active riders |

### 5. Core Features (MVP)
1. Background ride monitoring (GPS + accelerometer + gyroscope) during working hours.
2. Rule-based accident/fall detection engine.
3. 20-second "I'm OK" confirmation screen with countdown + siren.
4. Automatic SOS: call emergency contact, send live GPS location via SMS/notification, alert employer dashboard.
5. Manual SOS button + voice-activated "Help" trigger (works even when phone is locked).
6. Simple onboarding: phone number auth, emergency contact setup, working-hours schedule.

### 6. Post-MVP / Future Features
- On-device ML model (TensorFlow Lite) replacing/augmenting rule-based detection.
- Daily safety score per rider.
- Route risk analysis (heatmap of accident-prone zones).
- Employer dashboard with fleet-wide analytics.
- Optional Bluetooth wearable / helmet attachment for more reliable crash signals.
- Integration with nearby ambulance/hospital dispatch services.
- Insurance partner integration.

### 7. User Stories
- As a rider, I want the app to detect if I crash so my family is notified even if I'm unconscious.
- As a rider, I want to cancel a false alarm quickly so I'm not annoyed by unnecessary alerts.
- As a rider, I want to trigger an SOS manually or by voice if I'm in danger but not in a "crash."
- As an emergency contact, I want to receive a call/SMS with live location the moment an accident is confirmed.
- As an employer, I want a dashboard showing which riders are currently active and their safety status.

### 8. Non-Functional Requirements
- **Reliability:** Detection service must survive app backgrounding, phone lock, and OS battery optimization killing background processes.
- **Privacy:** Location tracking only during declared "working hours"; riders must explicitly consent.
- **Battery:** Sensor polling must be optimized (adaptive sampling rate) to avoid excessive drain.
- **Latency:** End-to-end detection-to-SOS latency target: under 30 seconds.
- **Offline resilience:** Core detection + local alert (siren/vibration) must work without internet; SOS messages queue and send once connectivity resumes.

### 9. Constraints & Assumptions
- MVP targets Android only (dominant OS among Indian gig workers).
- Assumes rider carries phone on person or bike mount, not in a bag, for MVP accuracy (bag/backpack case is a known limitation, addressed later via wearable).
- Assumes rider has an active data/SIM connection for SOS delivery (SMS fallback for no-data scenarios).
- Backend uses Firebase for MVP speed; may be re-architected for scale post-hackathon.

### 10. Out of Scope (for MVP)
- iOS app.
- Hardware wearable/helmet sensor (design only, not built).
- Insurance/claims integration.
- Multi-language voice assistant (English/Hindi only for MVP).
