# Design.md — Visual & UX Guidelines

### 1. Design Philosophy
This app is used by riders while moving, often in direct sunlight, wearing gloves, and under stress during an actual emergency. Design must prioritize:
- **Clarity over decoration** — big text, high contrast, minimal cognitive load.
- **One-handed, glance-based use** — most interactions happen while riding or immediately after a fall.
- **Calm-but-urgent tone** — everyday UI should feel reassuring; the emergency confirmation screen should feel unmistakably urgent.

### 2. Color Palette

| Role | Color | Hex | Use |
|---|---|---|---|
| Primary (trust/safety) | Deep Teal | `#0E7C7B` | Nav bar, primary buttons, branding |
| Secondary | Soft Blue | `#3B82F6` | Links, secondary actions, map elements |
| Alert / Emergency | Signal Red | `#E63946` | Confirmation countdown, SOS button, active alerts |
| Warning | Amber | `#F4A300` | Risk zones, battery/permission warnings |
| Success | Green | `#2E9E5B` | "I'm OK" confirmed, ride completed safely |
| Background (light) | Off-white | `#F7F8FA` | Default background |
| Background (dark) | Near-black | `#121417` | Dark mode default (recommended for night riders) |
| Text primary | Charcoal | `#1C1F23` | Body text on light background |
| Text on dark | Off-white | `#F1F1F1` | Body text on dark background |

### 3. Typography
- **Font:** Inter or Roboto (system default on Android — avoid custom fonts that add load time/complexity for MVP).
- **Scale:** Minimum body text 16sp; countdown screen numbers at 48–64sp for glanceability.
- **Weight:** Bold for anything safety-critical (SOS button label, countdown, alerts); Regular for informational text.

### 4. Key Screens
1. **Onboarding** — Minimal steps, large tap targets, progress indicator (3–4 steps max).
2. **Home / Ride Status** — Big "Go Online / Go Offline" toggle, current ride duration, safety score badge.
3. **Confirmation ("I'm OK") Screen** — Full-screen red/amber alert, large countdown ring, one giant "I'M OK" button, siren sound + vibration.
4. **SOS Sent Screen** — Confirms what was sent (call placed, location shared, employer notified) with a "Cancel / False Alarm" option still available for a short grace window.
5. **Settings** — Emergency contacts, working hours, sensitivity tuning (if exposed), permissions status.
6. **Employer Dashboard (web)** — List/map view of active riders, status badges (Online / Alert / Offline), event history log.

### 5. Iconography
- Use simple, filled (not thin-line) icons for outdoor sunlight legibility — Material Icons "Filled" set is a safe default.
- Reserve a shield/guardian icon as the core brand mark across app icon, splash screen, and dashboard.

### 6. Accessibility & Context-of-Use Notes
- All primary actions must have a minimum 48x48dp touch target (rider may be wearing gloves).
- The confirmation screen must work with sound AND vibration AND visual cues — riders may be wearing a helmet and unable to hear clearly.
- Support voice output for critical alerts (text-to-speech reading "Accident detected, tap I'm OK or say Help") for hands-free confirmation.
- Dark mode should be the default during night hours (auto-switch based on time), since most riders operate in low light.

### 7. Tone of Voice (Copy Guidelines)
- Short, direct, non-alarming in normal states: "You're online. Stay safe out there."
- Urgent, clear, low-jargon in emergency states: "Possible accident detected. Tap I'M OK or we'll call for help in 20s."
- Never use humor or a casual tone on safety-critical screens.
