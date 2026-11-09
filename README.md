# Koora ⚽ — football scores app for MENA + France

Native Android app (Kotlin + Jetpack Compose). Launch competitions:
**Champions League + Ligue 1** (free football-data.org data). **Botola Pro**
is added later via API-Football — the free tier of football-data.org does
not include it.

Languages built in: **English / Français / العربية** (RTL supported).
Follows the Study → Build → Check → Ready method (from the craft-anything repo).

---

## 1. Get the installable APK — GitHub builds it for you (no Android Studio needed)

The project contains `.github/workflows/build.yml`. GitHub's free servers
compile the app every time you upload/push code, and give you back an
**APK file you can install on any Android phone**.

**Step by step:**

1. Create a free account at https://github.com (if you don't have one).
2. Click **+** (top right) → **New repository** → name it `koora-app` →
   keep it **Public** (free unlimited builds) → **Create repository**.
3. On the new repo page click **"uploading an existing file"** (or *Add file → Upload files*).
4. Open the `koora-app.zip` on your computer, unzip it once,
   then **drag ALL the files and folders from inside the unzipped `koora-app` folder**
   (you should see folders like `app`, `gradle`, `.github`, `store-assets` and
   files like `build.gradle.kts`, `settings.gradle.kts`, `gradlew`) into the GitHub upload area.
   ⚠️ Drag the **contents**, not the `koora-app` folder itself.
5. Click **Commit changes** and wait ~5–10 minutes.
6. Open the **Actions** tab in your repo → click the latest run
   ("Build Koora APK") → if there is a green ✓, scroll down to **Artifacts** →
   download **`Koora-APK`** → unzip it → you get **`app-debug.apk`**.

If the run fails with a red ✗, click it, screenshot the error, and ask me.

## 2. Install the APK on your phone

1. Send `app-debug.apk` to your phone (WhatsApp to yourself, Google Drive, USB — any way).
2. Tap the file → Android asks to allow "install unknown apps" for that app → allow → install.
3. Open **Koora** → you see demo matches immediately.

## 3. Make the matches real (free, 5 minutes)

1. Register free at https://www.football-data.org (free tier = 12 competitions).
2. Copy your API key.
3. In your GitHub repo: **Settings → Secrets and variables → Actions → New repository secret** →
   Name: `FOOTBALL_DATA_TOKEN`, Value: your key → Save.
4. Re-run the build (Actions tab → latest run → **Re-run all jobs**, or push any small change).
   The new APK now loads **real Champions League + Ligue 1** data.

(If you use Android Studio locally instead: put the key in `local.properties` — same effect.)

## 4. Optional: Android Studio route (for development later)

1. Install **Android Studio**: https://developer.android.com/studio
2. **Open** the project folder → let Gradle sync finish → plug phone with USB debugging → ▶ Run.

## 5. Build the release file (AAB) for Google Play

1. In Android Studio: **Build → Generate Signed App Bundle / APK → Android App Bundle**.
2. First time: click *Create new…* keystore. **Save this keystore + passwords forever** — losing it means losing the app on Play.
3. Result: `app/release/app-release.aab` → the file you upload to Play.

## 6. Google Play launch path (the real bottleneck)

| Step | Detail |
|---|---|
| Play Console account | one-time $25 |
| Create app | name *Koora*, category Sports, default language French or Arabic |
| **Closed testing** | upload the AAB on the *Closed testing* track, create a list of **at least 12 testers** (friends/family emails), they must opt in and use the app for **14 continuous days** — Google's requirement for new personal developer accounts before production access |
| Store listing | use `store-assets/play-icon-512.png`; screenshots from your phone; short description (Arabic + French) |
| Data safety form | app collects: advertising ID (for AdMob); no data sold |
| Content rating | questionnaire, Sports app, no violence |
| Production | after the 14-day closed test is validated, submit for review |

## 7. Before publishing — replace the test ad IDs

| Where | What | Replace with |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | `ca-app-pub-3940256099942544~3347511713` | your real **AdMob App ID** |
| `AdsManager.kt` | `TEST_REWARDED_UNIT` | your real **rewarded ad unit** |

Also: create an AdMob account, link the app, and add a **privacy policy URL**
(required by Play and by AdMob for EU users). The GDPR consent banner
(Google UMP) is already wired in `MainActivity`.

## 8. Compliance red lines (do not break these)

- ❌ No live video streams or TV channel links (instant Play rejection / ban)
- ❌ No club or league logos in the app (IP — use plain text team names)
- ❌ No scraping sites — only licensed APIs (football-data.org / API-Football)
- ✅ Ads: rewarded video = the format with the best MENA revenue

## 9. Roadmap after launch

1. **v1.0** — CL + Ligue 1, demo → real data, rewarded ads (this project)
2. **v1.1** — Botola Pro + CAF Champions League via API-Football (~$19/mo, the main running cost) — add it only when ad revenue covers it
3. **v1.2** — "Remove ads" in-app purchase: $1.99 MENA / €3.49 France
4. **v1.3** — push notifications for goals of favorite teams

---

### Troubleshooting

- **Actions run fails at "Build debug APK"**: open the run, expand the failed step, screenshot it and ask me — usually a typo from a manual upload (a folder uploaded at the wrong level is the #1 cause).
- **Uploaded files ended up in a subfolder** (e.g. `koora-app/app/...` inside the repo): the build fails because `settings.gradle.kts` must sit at the repo root. Fix: move everything one level up.
- **Missing API level 36 SDK on your PC**: Android Studio will prompt to install it — accept.
