# Let's Build RESQGO 🚀 (Part 2)
### Your Step-by-Step Guide to Phase 3, 4, 5 & 6

Nice — you made it through Phase 0 to 2. That means your app already runs, knows who's using it, and can feel movement. Now comes the exciting part: making the app actually *think*, react, and talk to the outside world. Same rule as last time: go slow, one small step at a time, and it's totally normal to not understand everything on the first try.

---

## 🧠 Phase 3: Teach the App to Recognize an Accident

Right now your app can *see* sensor numbers, but it doesn't know which numbers mean "normal ride" versus "something bad just happened." This phase gives it a brain — a simple one, made of rules.

### Step 1 — Understand "Rule-Based" Thinking
A rule-based system is just a checklist of IF-THEN statements, like a recipe:

> IF speed suddenly drops from fast to zero
> AND there's a big impact jolt
> AND the phone suddenly rotates
> AND nothing moves for a few seconds afterward
> THEN → this is probably an accident

No AI, no magic — just clear conditions checked one after another.

### Step 2 — Turn the Checklist into Code
```kotlin
fun checkForAccident(speed: Float, impactG: Float, rotationDeg: Float, stillFor: Long): Boolean {
    val speedDropped = speed < 5f          // almost stopped
    val bigImpact = impactG > 8f           // hard jolt
    val suddenSpin = rotationDeg > 90f      // phone flipped
    val wentStill = stillFor > 3000         // 3+ seconds no movement

    return speedDropped && bigImpact && suddenSpin && wentStill
}
```
Each variable is just a number your Phase 2 sensors are already giving you. This function simply checks all four boxes — if every single one is true, it returns `true` (possible accident).

### Step 3 — Test It Like a Detective, Not Just a Coder
The tricky part isn't writing the code — it's picking the right numbers (like `8f` for impact). Too sensitive, and it panics every time someone brakes hard at a red light. Too relaxed, and it misses a real crash.

Test with these situations and adjust your numbers until each one behaves correctly:

| Situation | Should it trigger? |
|---|---|
| Riding normally | ❌ No |
| Hard braking at a signal | ❌ No |
| Phone dropped on a pillow | ❌ No |
| Simulated crash (drop phone from waist height onto grass, carefully) | ✅ Yes |

This trial-and-error process is called **tuning**, and every real safety app goes through it — you're doing exactly what professional engineers do.

### ✅ Phase 3 Checkpoint
- [ ] Your `checkForAccident()` function runs continuously while sensors are active
- [ ] It stays quiet during normal riding and braking
- [ ] It triggers `true` during your simulated crash test

---

## ⏱️ Phase 4: Build the "Are You OK?" Screen + Auto-SOS

This is the heart of the whole app — the moment it actually protects someone.

### Step 1 — Build the Countdown Screen
When `checkForAccident()` returns `true`, show a full-screen alert with a 20-second countdown. Android has a built-in tool for this called `CountDownTimer`:

```kotlin
object : CountDownTimer(20000, 1000) {
    override fun onTick(millisUntilFinished: Long) {
        // update the countdown number on screen, e.g. "18... 17... 16"
    }
    override fun onFinish() {
        triggerSOS() // nobody tapped "I'm OK" in time
    }
}.start()
```
`20000` means 20,000 milliseconds (20 seconds), and `1000` means it updates the screen every second.

### Step 2 — Add Sound + Vibration
Someone might not be looking at their screen, so also make noise and vibrate:
```kotlin
val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
```
Pair this with a loud alert sound using `MediaPlayer` so it's impossible to miss, even inside a helmet.

### Step 3 — Give an Easy "I'm OK" Button
One giant button, centered, that cancels the timer:
```kotlin
button.setOnClickListener {
    countDownTimer.cancel()
    // log this as a false alarm, go back to normal monitoring
}
```

### Step 4 — Build the Automatic SOS
If nobody taps the button in time, `triggerSOS()` should:
1. **Call the emergency contact** automatically using an `Intent` (Android's way of asking another app — here, the Phone app — to do something for you).
2. **Send an SMS** with a Google Maps link to the current location, using `SmsManager`.
3. Save the event so Phase 5 can pick it up and alert everyone else too.

```kotlin
fun triggerSOS(phoneNumber: String, mapsLink: String) {
    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
    startActivity(callIntent)

    val smsManager = SmsManager.getDefault()
    smsManager.sendTextMessage(phoneNumber, null, "Possible accident detected! Location: $mapsLink", null, null)
}
```

### ✅ Phase 4 Checkpoint
- [ ] The countdown screen appears the instant `checkForAccident()` returns true
- [ ] Sound + vibration play immediately
- [ ] Tapping "I'm OK" cancels everything cleanly
- [ ] Letting the timer run out places a real call and sends a real SMS (test this with your own number first!)

---

## ☁️ Phase 5: Give Your App a Backend (So Others Get Notified Too)

Phase 4 handles things locally on the rider's own phone. Phase 5 is about telling *everyone else* — family, employer — through the internet.

### Step 1 — Understand What a "Backend" Actually Is
A backend is just another computer program, except it runs on a server 24/7 instead of on someone's phone. Think of it like a receptionist at a front desk: your app "calls" the receptionist whenever something important happens, and the receptionist decides who else needs to know.

### Step 2 — Design Your Firestore "Filing Cabinet"
Firestore stores data in collections, kind of like folders full of index cards:
```
riders/          (one card per rider: name, emergency contact)
rides/           (one card per ride: start time, status)
events/          (one card per accident event: location, time, resolved?)
```
When an accident happens, your Phase 4 code writes one new "index card" into `events/`.

### Step 3 — Write a Simple Backend Route
Using Node.js + Express, a route just means "a specific web address your app can send information to":
```javascript
app.post('/sos-event', async (req, res) => {
    const { riderId, location, timestamp } = req.body;

    await db.collection('events').add({ riderId, location, timestamp });

    // Now tell the family + employer
    await sendPushNotification(riderId);
    await sendSMS(riderId, location);

    res.status(200).send('SOS handled');
});
```
Your app sends a small package of info (rider ID, location, time) to this address, and the backend takes care of alerting everyone.

### Step 4 — Send Notifications
- **Push notification** to the employer's dashboard app, using Firebase Cloud Messaging (FCM) — it's like the backend "shouting" a message that any connected device can hear.
- **SMS** to family, using Twilio — sign up for a free trial account, and Twilio gives you a tiny bit of code to send a text from your server instead of from a phone.

### ✅ Phase 5 Checkpoint
- [ ] Manually triggering a test SOS event creates a new entry in Firestore
- [ ] A push notification arrives somewhere (even just a test device)
- [ ] A real SMS is sent through Twilio's free trial

---

## 📊 Phase 6: Build the Employer/Family Dashboard

Last phase in this batch — a simple webpage where an employer can see, at a glance, whether their riders are safe.

### Step 1 — Understand What a Dashboard Is
It's just a webpage that shows *live* information, like a scoreboard at a sports game that updates automatically instead of you refreshing it yourself.

### Step 2 — Start a Simple React Project
```bash
npm create vite@latest resqgo-dashboard -- --template react
cd resqgo-dashboard
npm install
npm run dev
```
This gives you a basic webpage you can build on, running on your own computer to test.

### Step 3 — Show Live Data from Firestore
Firestore has a special feature called `onSnapshot` that automatically updates your webpage the instant new data arrives — no refresh button needed:
```javascript
import { onSnapshot, collection } from "firebase/firestore";

onSnapshot(collection(db, "riders"), (snapshot) => {
    const riders = snapshot.docs.map(doc => doc.data());
    setRiders(riders); // updates what's shown on screen instantly
});
```
So the moment a rider goes online, has an event, or goes offline, the webpage updates itself in real time — like magic, except it's just `onSnapshot` doing its job.

### Step 4 — Design the Simplest Useful View
For a first version, just show a list:
- Rider name
- Status badge: 🟢 Online / 🔴 Alert / ⚪ Offline
- Last known location

You can make it prettier later — the goal right now is just: does the badge change color the instant something happens?

### ✅ Phase 6 Checkpoint
- [ ] The dashboard webpage loads and shows a list of riders
- [ ] Going "online" in the app updates the badge on the dashboard without refreshing
- [ ] Triggering a test SOS event turns that rider's badge red on the dashboard

---

## 🎉 Look at What You've Built

By the end of this batch, RESQGO can now: detect a likely accident, ask the rider if they're OK, automatically call and text for help if they don't respond, tell the backend, and show it live on a dashboard. That is a genuinely complete safety pipeline, start to finish — most hackathon teams don't get this far. Take a proper break before Phase 7.

## 🆘 If You Get Stuck
- If a call/SMS test doesn't work, double check you're testing on a *real phone*, not the emulator — calling and texting don't work on emulators.
- If Firestore doesn't update live, make sure your Firebase config keys match on both the app and the dashboard.
- Twilio's free trial only sends SMS to phone numbers you've "verified" first inside the Twilio console — that's normal for trial accounts, not a bug in your code.

You're building something real. Keep going. 💪
