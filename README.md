# Waypoint Voice — Android app + website

One GitHub repository gives you both:
- **The website**, free on GitHub Pages at `https://YOURNAME.github.io/waypoint-voice/`
- **The Android app**, which loads that website, so website updates reach the app automatically. It adds background GPS (keeps navigating with the screen off), YouTube Music / Spotify controls, and directions that lower your music instead of talking over it.

GitHub builds everything for you. You never install developer tools.

## One-time setup (on your PC)

1. **Unzip** `waypoint-voice-android.zip` somewhere easy, like your Desktop.
2. Go to **github.com**, sign in, click **+** (top right) → **New repository**.
   - Name: `waypoint-voice`
   - Choose **Public** (free GitHub Pages needs this; nothing private is in the code — your ElevenLabs key stays on your phone)
   - Leave everything else alone → **Create repository**
3. On the new empty repo page, click the link **"uploading an existing file"**.
4. Open the unzipped `waypoint-voice-android` folder. Select **everything inside it** (Ctrl+A), including the `.github` folder, and drag it onto the GitHub page.
   - Don't see `.github`? In File Explorer: View → Show → Hidden items.
5. Scroll down → **Commit changes**.
6. Turn on the website: repo **Settings** → **Pages** (left side) → under "Build and deployment", set **Source** to **GitHub Actions**.
7. Click the **Actions** tab. Two jobs run:
   - **Publish website** — if it shows a red X, that's just because step 6 came after the upload. Click it → **Re-run all jobs**.
   - **Build app** — takes about 5–10 minutes.
   - Any other red X? Click it, click the failed step, copy the red error text, and paste it to Claude.

## Install on your phone

1. On your phone, open your repo on github.com → **Releases** (right side, or under "About") → newest **Waypoint Voice build**.
2. Tap the **WaypointVoice-N.apk** file to download.
3. Open the download. Android asks to allow installs from Chrome → **Settings → Allow from this source** → back → **Install**.
4. Open **Waypoint Voice**. Allow **Location** ("While using the app") and **Notifications**.
5. Your ElevenLabs key and settings don't carry over from the old Netlify site. Tap the **S** avatar → paste your key → Load → pick your voice.

## Music controls

Start any route. A card at the bottom asks to **Show music controls** → **Allow** → turn on **Waypoint Voice** in the list → back to the app. Play something in YouTube Music and it appears with skip / pause buttons.

## Live traffic (optional, free)

1. Go to **developer.tomtom.com**, sign up (no credit card), and copy the API key from your dashboard.
2. In the app: avatar → **Live traffic** → paste the key.

You get traffic-aware routes, traffic colors on the map, fastest / fuel-efficient / shortest route choices, carpool and FasTrak options (route options ⋮ on the directions screen), and "Faster route found" alerts while driving.

## Talk-to assistant (optional, pay-as-you-go)

1. Go to **console.anthropic.com**, sign up, add a few dollars under **Billing**, then **API keys → Create key**.
2. In the app: avatar → **Assistant** → paste the key. Optionally give her a name and some notes about you.
3. While driving, tap the round **talk button** on the right (or tap your buddy) and ask away.

Costs roughly 1–2¢ per question when she searches the web, less when she doesn't, plus ElevenLabs credit for the spoken answer.

## Updating

**Most updates (anything Claude sends as `index.html`) — about 2 minutes:**
1. In your repo, open `app` → `src` → `main` → `assets`.
2. **Add file → Upload files**, drop in the new `index.html`, **Commit changes**. (Works from your phone's browser too.)
3. Wait a minute or two, then close and reopen the app. It loads the new version. The website updates too.

**App-level updates (Claude will say when; rare) — about 10 minutes:**
Upload the changed files Claude gives you. Wait for **Build app** to finish under Actions, then install the newest release on your phone. It installs over the old one and keeps your saved places and settings.

## No signal?

If the app can't reach your website when it opens, it uses the copy built into the app, so it still works.

## Sharing with friends

Send them the .apk from Releases. Each person enters their own ElevenLabs key (or yours, if you're fine paying for their voice lines).
