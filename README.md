# RESQGO — Ride Safe, Help Faster

Automatic crash detection and SOS for two-wheeler gig workers (delivery and ride-hailing riders).
If a rider crashes and doesn't respond, RESQGO alerts their emergency contacts with a live
location within seconds. The rider doesn't need to do anything.

## How it works

1. **Ride monitoring:** a foreground service (`RideMonitoringService`) reads the accelerometer,
   gyroscope and GPS during a ride, and keeps running when the app is in the background or the phone is locked.
2. **Detection:** `RuleBasedDetector` flags a crash on a high-G impact, confirmed by tumbling
   (gyroscope) and post-impact stillness with near-zero speed. Thresholds are tuned for Indian
   roads, so potholes and speed breakers don't trigger it. Sensitivity can be set to Low, Medium or High.
3. **Confirmation:** an "I'm OK" countdown with a siren and voice prompt lets the rider cancel a false alarm.
   It runs 30 seconds by default and can be changed in Settings.
4. **SOS:** when the countdown ends, alerts go out through a fallback chain:
   - **Layer 1:** SMS from the handset SIM (tries each SIM on dual-SIM phones, confirmed by a delivery receipt)
   - **Layer 2:** internet SMS through Fast2SMS, if the SIM route fails
   - **Manual fallback:** WhatsApp deep link and phone call from the SOS screen
5. **History:** every event is saved locally in Room and sent to Firebase Firestore (`crash_events`).

There is also a manual SOS button, ride history, emergency contact management, in-ride
navigation (OpenStreetMap via osmdroid, OSRM routing, Nominatim search), light and dark themes, and Hindi strings.

## Tech stack

| Area | Stack |
|---|---|
| App | Kotlin, ViewBinding, Coroutines, Lifecycle/ViewModel |
| Storage | Room, EncryptedSharedPreferences |
| Location & maps | Play Services Location, osmdroid, OSRM, Nominatim (no Maps API key) |
| Background | Foreground service, WorkManager |
| Backend | Firebase Auth, Firestore, Cloud Messaging, Crashlytics |
| SMS gateway | Fast2SMS (optional) |
| Dashboard | React 19 + Vite + Firebase (`dashboard/`) |

`minSdk 26` · `targetSdk 36`

## Project structure

```
app/src/main/java/com/example/resqgo/
├── detection/     # RuleBasedDetector, thresholds
├── sensors/       # accelerometer / gyroscope wrapper
├── service/       # RideMonitoringService (foreground)
├── sos/           # SOSManager, SIM + HTTP SMS delivery
├── location/      # LocationTracker
├── navigation/    # OSRM routing, Nominatim search
├── data/          # Room DB, repository, preferences
└── ui/            # onboarding, home, riding, confirmation, SOS, history, contacts, settings
dashboard/         # employer web dashboard (live crash events)
RESQGO - Architecture and Design/   # PRD, architecture, design docs
```

## Getting started

### Android app

1. Clone and open the project in Android Studio.
   ```bash
   git clone https://github.com/ashutoshm0/RESQGO.git
   ```
2. Create a Firebase project, add an Android app with package `com.example.resqgo`, and put
   `google-services.json` in `app/`. This file is gitignored.
3. Optional: to turn on Layer 2 SMS, add your Fast2SMS key to `local.properties`.
   ```properties
   FAST2SMS_API_KEY=your_key_here
   ```
   If you leave it out, that layer is skipped.
4. Run on a real device. Emulators don't produce realistic sensor data or send SMS.

On first launch, grant location (including background), SMS, phone, and notification
permissions, and add at least one emergency contact.

### Dashboard

```bash
cd dashboard
npm install
npm run dev
```

Set the Firebase config in `dashboard/src/firebase.js` to point at the same project.

## Docs

More detail is in [`RESQGO - Architecture and Design/`](RESQGO%20-%20Architecture%20and%20Design/),
including the [PRD](RESQGO%20-%20Architecture%20and%20Design/PRD.md) and
[Architecture](RESQGO%20-%20Architecture%20and%20Design/Architecture.md).

## Roadmap

- On-device ML (TensorFlow Lite) detection
- Voice-triggered SOS
- Per-rider safety score and accident-zone heatmaps
- Fleet analytics on the employer dashboard
- Ambulance / hospital dispatch integration
