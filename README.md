# Shotgun

**Your navigator rides shotgun.** A Google Maps–style navigation app for Android where your navigator is a character you choose, speaking in a custom ElevenLabs voice.

It looks and works like Google Maps: search, saved places, directions, live traffic and turn-by-turn navigation. The difference is who's riding along. Pick any ElevenLabs voice (including one you made), give your buddy a name, a personality and an animated avatar, and talk to it hands-free while you drive.

It's a personal app for you and a few friends. It isn't on the Play Store; you install it from this repo's **Releases** page.

---

## Features

### Maps & search
- Clean, Google-style map (with a TomTom key), plus Satellite, Terrain and Detailed views
- Search with instant suggestions: your labeled places first (most used on top), then saved places and recent searches
- Category chips (Restaurants, Gas, Coffee…) that widen the search in remote areas until they find something
- Place pages with hours, phone, website, photos and a Wikipedia summary
- Businesses and points of interest show up as you zoom in
- Saved places with Home, Work and your own labels ("Alex's House"), plus lists. You can nudge a pin onto the exact spot

### Directions & navigation
- Driving, walking and cycling, with stops along the way
- Live traffic, with Fastest, Fuel-efficient and Shortest route choices, plus carpool and FasTrak options
- "Faster route found" alerts while you drive
- Turn-by-turn voice in your chosen voice. It lowers your music instead of talking over it
- Lane arrows for highway exits and big intersections
- Speed limit and speedometer, with an optional warning when you're well over
- Speed camera alerts, plus live accident, closure, hazard and road work alerts
- Keeps navigating with the screen off or another app on top
- YouTube Music / Spotify controls on the navigation screen
- Offline maps for a saved route

### Your navigator
- **Any ElevenLabs voice**, with Stability, Similarity, Style and Speed sliders
- **Personality:** Standard (plain directions) or Chill (low-key and laid back), plus your own custom lines
- **Pronouns:** Neutral, She, He or They; menus and the assistant follow along
- **Animated avatar:** upload your own art and it lip-syncs to the voice and reacts (happy, surprised, worried). It can stay on the map when you're not navigating
- **Road-trip chatter and fun facts** about places you pass, if you want them
- **Characters:** save a whole setup (name, voice, personality, art, theme) and switch in one tap

### Talk to your buddy (assistant)
- Ask anything out loud: trivia about where you're going, events this weekend, random questions
- Control the trip by voice: "take me home", "add a stop at a Starbucks", "find gas ahead", "avoid highways", "end navigation"
- "Where's the cheapest gas ahead?" finds stations on your route and looks up prices
- **Language:** Clean, Casual (matches how you talk) or Unhinged (swears freely, roasts traffic; no slurs)
- **Tone detection** (optional): hears *how* you said it, like whispering, sarcasm or laughing, and reacts to match

### Hands-free & car
- **Wake phrase:** say "hey Saba" (or your own phrase) instead of tapping. Listening for it happens on the phone
- **Aux cable fix:** asks whether to use the phone's mic when a cable is plugged in, because car aux cables usually can't hear you
- **Car Bluetooth:** opens the app (or shows a tap-to-open notification) when your car connects
- **Share your trip:** send a link and friends watch your ETA live until you arrive

### Look & feel
- Themes: Google, Saba (sky blue and gold) or your own color, each with light and dark mode
- App icon styles: Classic, Saba, Midnight and Sakura, or a home-screen icon from your own picture
- Your own profile photo
- Tablet and landscape layout with a side panel, like Google Maps on a tablet
- Trip stats and badges

---

## What you need

| | What it's for | Cost |
|---|---|---|
| **ElevenLabs** API key | Your navigator's voice | Your ElevenLabs plan. Lines are saved and reused, so a typical drive uses very little |
| **TomTom** API key *(optional)* | Clean map, live traffic, faster-route alerts, road alerts | Free tier, no credit card |
| **Claude** API key *(optional)* | Talking to your buddy | Pay-as-you-go, roughly 1–2¢ per question when it searches the web |
| **Gemini** API key *(optional)* | Tone detection | Free tier |

Without any keys the app still works as a map with the phone's built-in voice. All keys are saved only on your phone and are never put in this code.

---

## Quick start (about 10 minutes)

### 1. Install
1. On your phone, open this repo → **Releases** → the newest **Shotgun build** → tap the **.apk** file.
2. Open the download. If Android blocks it: **Settings → Allow from this source** → back → **Install**.
3. Open the app and allow **Location** and **Notifications**.

> "App not installed"? Turn off Play Protect scanning for a moment (Play Store → profile → Play Protect → gear). On Samsung, also turn off Auto Blocker (Settings → Security). If an older copy came from somewhere else, uninstall it first.

### 2. Give it a voice
1. Tap your **profile circle** (top right) → **Voice**.
2. Paste your ElevenLabs key (elevenlabs.io → Developers → API keys) → **Load** → pick a voice.
3. Tap **Test voice**. Model **Flash** is the best choice for driving.

### 3. Make it yours
- **Personality:** choose Standard or Chill, pick pronouns, and optionally write custom lines.
- **Buddy avatar:** upload a mouth-closed and a mouth-open picture (PNG with a see-through background works best). Blink, happy, surprised and worried frames are optional extras.
- **Look & app icon:** pick a theme and an icon.
- **Characters:** once you like it, save it as a character.

### 4. Optional extras
- **Live traffic:** sign up free at developer.tomtom.com, copy the key → **Live traffic** → paste.
- **Assistant:** console.anthropic.com → add a few dollars under Billing → API keys → Create key → **Assistant** → paste. Give your buddy a name.
- **Tone detection:** get a key at aistudio.google.com/apikey → **Assistant → Tone detection** → paste.
- **Hands-free:** **Hands-free & car** → turn on the wake phrase (downloads about 40 MB once), and pick your car's Bluetooth.

### 5. Try a drive
Search a place → **Directions** → choose a route → **Start**. Want to see it without moving? Tap **Test drive** on the directions screen.

---

## Everyday use

**Search:** tap the search bar and start typing. Your labeled places pop up first. Tap a chip like **Gas** for nearby options.

**Save a place:** open it → **Save** → Home, Work, a label or a list. If a pin is a bit off, use **Fix pin** to drag it exactly.

**While navigating**, the buttons on the right are:
- 🧭 north-up / turn-with-me
- 🔊 mute
- 🔍 search along the route
- 📍 share your trip live
- 🎙️ talk to your buddy

**Things to say** (after the wake phrase, or after tapping the talk button):
- "Take me home" / "Navigate to the nearest Target"
- "Find something to eat ahead" → "Take me to the second one"
- "Add a stop at a gas station" / "Remove the stop"
- "Avoid highways" / "Avoid tolls"
- "Where's the cheapest gas ahead?"
- "What's there to do in Vegas this weekend?"
- "End navigation"

**Offline maps:** on the directions screen, tap **Save offline** before a trip through areas with no signal.

**Share your trip:** tap 📍 while navigating and send the link. Your friend sees your position and ETA update about once a minute until you arrive or tap 📍 again. Anyone with the link can see it, so only send it to people you trust.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| The voice sounds robotic | That's the phone's backup voice. Check your ElevenLabs key and voice under **Voice**, and your ElevenLabs credits |
| "Didn't catch that" | Wait for the navigator to finish talking, then speak. With an aux cable, choose **Phone mic** under **Hands-free & car** |
| The wake phrase doesn't trigger | Make sure the offline speech download finished. Try a phrase with clear words ("hey buddy") |
| No traffic colors | Add a TomTom key under **Live traffic** |
| Search finds nothing out in the desert | Tap a category chip. It widens the search automatically |
| The map is blank | No signal. Saved offline routes still work |
| Music controls missing | Start a route and tap **Show music controls** → turn on Shotgun |

---

## Privacy

- Everything you save (places, keys, settings, characters) stays on your phone.
- The wake phrase is detected on the phone. Audio is only sent out after you talk to the assistant: to Gemini if tone detection is on, otherwise turned into text on the phone or by Android, and only the words go to Claude.
- Map searches go to OpenStreetMap services; traffic and routes go to TomTom when you've added a key.
- Trip sharing sends your position through the free ntfy.sh relay to whoever has the link.

---

## For the repo owner: building & updating

This repo builds everything with GitHub Actions; no developer tools needed.

- **Website:** published from `app/src/main/assets` to GitHub Pages (`https://YOURNAME.github.io/waypoint-voice/`). The app loads this site, so website changes reach every phone on the next app restart.
- **App:** each push builds a signed APK and posts it under **Releases**.

### First-time setup
1. Create a **public** repo named `waypoint-voice` (the repo keeps its original name; the app finds its website by it, so don't rename it) and upload everything in this folder, including `.github/workflows`.
2. Repo **Settings → Pages →** Source: **GitHub Actions**.
3. Check the **Actions** tab: **Publish website** and **Build app** should both go green. If **Publish website** failed because Pages wasn't on yet, re-run it.

### Updating
- **Most updates** (`index.html` or `share.html`): upload to `app/src/main/assets` → Commit. Live in a minute or two; close and reopen the app.
- **App-level updates** (Java files, `AndroidManifest.xml`, `build.gradle`, icons): upload, wait for **Build app** to go green, then install the newest release over the old one. Your data is kept.
- Red X? Open the run → the failed step → copy the red error text.

### Sharing with friends
Send them the .apk from Releases. Each person enters their own keys, or yours if you're fine paying for their usage.
