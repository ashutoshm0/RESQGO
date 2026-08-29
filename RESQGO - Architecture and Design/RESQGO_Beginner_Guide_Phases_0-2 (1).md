# Let's Build RESQGO 🚀
### Your Step-by-Step Guide to Phase 0, Phase 1 & Phase 2

Hey! So you're building an app that can automatically tell someone's family if they've crashed on a delivery ride. That's a genuinely awesome project — it could actually save someone's life one day. Don't worry if some of the words below sound confusing at first. Every single professional app developer started exactly where you are right now. We're going to go slow, one small step at a time.

---

## Before We Start: What Are We Actually Building?

Your app watches a delivery rider's phone while they're working. If the phone suddenly stops moving after a big jolt (like a crash), the app asks "Are you OK?" If nobody answers in 20 seconds, it calls for help automatically.

To build that, we need to do 3 things first:
1. **Phase 0** — Build the "workshop" (set up the tools and the empty project).
2. **Phase 1** — Teach the app to recognize *who* is using it (login + basic info).
3. **Phase 2** — Give the app "senses" so it can feel movement (sensors).

Let's go one at a time.

---

## 🧰 Phase 0: Set Up Your Workshop

Before you can build anything, you need tools. Think of this phase like setting up a workbench before building a treehouse — you can't hammer a nail without a hammer.

### Step 1 — Install Android Studio
This is the program you'll use to write and test your app. It's free.
- Search "Android Studio download" and get it from the official Google site.
- Install it like any normal program (Next → Next → Finish).
- It might take a while to download — that's normal, it's a big toolbox.

### Step 2 — Create Your Project
1. Open Android Studio.
2. Click **New Project**.
3. Choose the template called **Empty Views Activity** (this just means "a blank app with nothing in it yet, built the traditional Kotlin + XML way" — a blank canvas). Android Studio also shows a similarly-named "Empty Activity" — that one uses a newer style called Jetpack Compose, which looks different from the code examples in this guide, so stick with **Empty Views Activity**.
4. Name it `RESQGO`.
5. Make sure the language is set to **Kotlin**. (Kotlin is the language we're using to "talk" to the phone and tell it what to do — like giving instructions in English, but the phone's version of English.)
6. Click **Finish** and wait for it to load.

**Cool fact:** the moment you click that green ▶️ Run button and see a blank app open on your phone or the on-screen emulator, you have officially built your first app. Everyone remembers that moment.

### Step 3 — Set Up Firebase (Your App's Helper in the Cloud)
Firebase is like a free helper that lives on the internet. It can:
- Remember who signed up (Authentication)
- Store information like emergency contacts (Firestore — think of it as a giant, organized filing cabinet in the cloud)
- Send notifications to phones (Cloud Messaging)

To set it up:
1. Go to the Firebase website and sign in with a Google account.
2. Click **Create a Project**, name it `RESQGO`.
3. Inside the project, click **Add app** → choose **Android**.
4. It'll ask for your app's "package name" — use `com.resqgo.app`.
5. It gives you a file called `google-services.json`. Download it and drag it into your project's `app/` folder in Android Studio.

### ✅ Phase 0 Checkpoint
You're done with Phase 0 when:
- [ ] Android Studio opens without errors
- [ ] Your blank RESQGO app runs on an emulator or real phone
- [ ] Your Firebase project exists and is connected (the `google-services.json` file is in your project)

If all three are checked, take a break and feel good — that's genuinely the hardest part for most beginners, and you already did it.

---

## 👤 Phase 1: Teach the App Who's Using It

Right now your app doesn't know anything about anyone. This phase is about building the "front door" — login and basic setup.

### Step 1 — Turn On Phone Number Login
Instead of usernames and passwords (easy to forget), riders will just type their phone number and get a text with a code — like how banking apps often work.
1. In the Firebase console, go to **Authentication** → **Sign-in method**.
2. Turn on **Phone**.
3. In Android Studio, you'll write a simple screen with one text box (phone number) and one button ("Send Code"), then a second screen for entering the code that arrives by SMS.

### Step 2 — Build the Welcome Screens
After someone logs in for the first time, walk them through 3 quick questions:
- What's your emergency contact's name and phone number?
- What hours do you usually work? (so the app only watches you during rides, not your whole life)
- A quick "Allow permissions" screen (next step explains this).

Keep each screen to *one question*. Nobody wants to fill out a 10-question form on their phone while standing outside a restaurant waiting for an order.

### Step 3 — Ask for Permission the Polite Way
Your app needs to ask the phone for permission to:
- See the location (GPS)
- Use motion sensors
- Send a text message / make a call (for the SOS)

Android will pop up a system box asking "Allow RESQGO to access your location?" This is normal — every app that uses your phone's sensors has to ask first, kind of like how a guest asks before opening your fridge. Your job is just to explain *why* you're asking, in plain words, right before the popup appears: something like *"RESQGO needs your location so we can send help if something happens to you."*

### ✅ Phase 1 Checkpoint
- [ ] You can type a phone number, get a code by text, and log in
- [ ] After logging in, you can enter an emergency contact and working hours
- [ ] The app asks for location and sensor permission, and you can tap "Allow"

---

## 📡 Phase 2: Give Your App "Senses"

This is the phase where your app starts to feel like magic — it's when the phone starts noticing movement.

### Step 1 — Meet the 3 Sensors (in plain English)

| Sensor | Think of it like... | What it tells us |
|---|---|---|
| **Accelerometer** | Your inner ear when you're on a rollercoaster | Sudden speeding up, slowing down, or impact |
| **Gyroscope** | A spinning top | If the phone suddenly flips or rotates |
| **GPS** | A treasure map with a moving dot | How fast you're going and where you are |

None of these are magic — every smartphone already has all three built in. We're just going to ask the phone to tell us what they're reading.

### Step 2 — Read Sensor Data in Code
In Android, there's a built-in tool called `SensorManager` whose whole job is to hand you sensor readings. A simplified version looks like this:

```kotlin
val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

val listener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // These 3 numbers describe motion in 3 directions
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
```

Don't worry about memorizing this — the important idea is: **register a listener, and the phone will keep handing you numbers** every time it senses movement.

### Step 3 — Keep It Running in the Background
Here's a tricky part: Android tries to save battery by shutting down apps you're not actively looking at. But we *need* RESQGO to keep watching even when the rider's phone is in their pocket. For that, we use something called a **foreground service** — think of it like a security guard who clocks in for their shift (when the rider taps "Go Online") and doesn't clock out until the shift ends ("Go Offline"). Android shows a small permanent notification so the rider always knows it's active — that's normal and required, not a bug.

### Step 4 — Test It Like a Scientist
1. Run the app on a real phone (sensors don't work well on the emulator).
2. Print the sensor numbers on screen or in Android Studio's **Logcat** window.
3. Walk around, shake the phone gently, and watch the numbers change in real time.
4. Try dropping the phone on a soft pillow (not the floor!) and see how the numbers spike for a moment.

That spike you just saw with your own eyes? That's the exact signal your Phase 3 detection logic will later look for.

### ✅ Phase 2 Checkpoint
- [ ] You can see live accelerometer/gyroscope numbers changing as you move the phone
- [ ] The app keeps reading sensors even when you lock the screen
- [ ] You've seen what a "normal" reading looks like vs. a "sudden jolt" reading

---

## 🎉 You Did It — What's Next?

If you've checked off all the boxes above, you now have a real, working app that:
- Runs on a phone
- Knows who's using it
- Can feel movement through sensors

That's a legitimate foundation most beginners never get past — genuinely, be proud of this. Phase 3 (teaching the app to recognize an *actual* accident from that sensor data) is next, and it'll make a lot more sense now that you've seen real sensor numbers with your own eyes.

## 🆘 If You Get Stuck
- Error messages look scary but they're usually just the computer being very specific about what's wrong. Copy the exact error text and search it — almost every error you'll hit, another beginner has already hit and solved.
- The official Android Developers website has free step-by-step tutorials ("codelabs") for exactly this kind of thing.
- It's completely normal for Phase 0 to take a few days the first time. Professional developers still Google basic stuff constantly — that's not a weakness, it's just how coding works.

You've got this. One small step at a time. 💪
