# Connecting the app to your Firebase project

The app **works right now with no setup** — entries are saved on the device.
This page is only for turning on **Google sign-in and sync with the same
Firestore database the web app uses** (`ponder-63191`).

Everything below happens once, in the Firebase console, and takes about 5 minutes.

---

## Why this step can't be skipped

The web app only needs the public keys in `firebase-config.js`, which are already
in the repo. Android is different: Google sign-in is tied to **your app's package
name and signing certificate**, so Google will only issue a token to a build that
has been registered. There is no way to register an Android app from code — it has
to be done in the console, by you, as the owner of the project.

Until then the app runs in device-only mode and shows a note saying so.

---

## 1. Register the Android app

1. Open <https://console.firebase.google.com> → project **ponder-63191**.
2. ⚙️ **Project settings** → **Your apps** → **Add app** → the **Android** icon.
3. **Android package name** — this must match exactly:

   ```
   com.hanifedma.ponder
   ```

4. **SHA-1 certificate fingerprint** — Google only issues a sign-in token to a
   build signed by a certificate you have registered, so **add both** of these.
   One app entry can hold as many fingerprints as you like ("Add fingerprint").

   | Which build | SHA-1 |
   |---|---|
   | **Release** (`assembleRelease`, the APK you install on your phone) | `BE:9E:69:9A:26:A0:DF:3D:41:38:98:D0:37:F3:0C:94:6E:83:16:F8` |
   | **Debug** (`installDebug`, from Android Studio) | `3D:29:58:C6:75:80:7A:A5:49:1F:A4:D2:56:1F:FB:8B:BB:2B:0D:A6` |

   The release fingerprint belongs to `ponder-release.jks` — see
   [Your release signing key](#your-release-signing-key) below.

   > Building on a different computer later? Its *debug* key is different again.
   > Get that machine's fingerprint with `./gradlew signingReport` and add it as
   > an extra SHA-1 on the same app. The release key is a file, so it travels
   > with you and its fingerprint never changes.

5. **Register app** → **Download `google-services.json`**.

## 2. Drop the file in

Put it here — the exact path matters:

```
app/google-services.json
```

That is all the wiring there is. The build script notices the file and switches
Firebase on; without it the project still builds and runs, just device-only.

## 3. Enable Google sign-in

**Build → Authentication → Sign-in method → Google → Enable** → pick a support
email → **Save**. (If you already did this for the web app, it's done.)

## 4. Publish the security rules

**Build → Firestore Database → Rules** must contain the rules from the web repo's
`firestore.rules` — each person can only read and write their own `/users/{uid}`
documents. If the web app already works, this is done.

## 5. Rebuild

```bash
./gradlew installDebug
```

Sign in with the same Google account you use on the web, and your quotes,
thoughts and healthy tips are all there.

---

## Your release signing key

`./gradlew assembleRelease` signs with a real key, not the debug one. Two
untracked files at the repo root make that work:

| File | What it is |
|---|---|
| `ponder-release.jks` | the keystore itself — a PKCS#12 file holding one RSA-2048 key, alias `ponder`, valid until 2054 |
| `keystore.properties` | where the build reads its path and passwords from (`keystore.properties.example` shows the format) |

Both are in `.gitignore` and **neither is in the repository**, on purpose:
anyone holding them can publish an update that Android accepts as genuinely
yours.

> **Back them up now**, somewhere private — a password manager, an encrypted
> drive. Android identifies an app by its signing certificate, so if you lose
> this key you can never ship an update that installs over the current one; the
> only way forward is a new app id and everyone reinstalling from scratch.

Its fingerprint, the one to register in Firebase:

```
SHA-1   BE:9E:69:9A:26:A0:DF:3D:41:38:98:D0:37:F3:0C:94:6E:83:16:F8
SHA-256 F6:1D:41:28:74:DA:FC:8B:3F:4A:3E:29:79:D9:94:FC:22:B1:7A:9F:4A:33:D3:A6:C1:98:DB:C0:0F:10:78:ED
```

Read them back at any time with:

```bash
keytool -list -v -keystore ponder-release.jks -alias ponder
```

Delete `keystore.properties` and release builds fall back to the debug key —
still installable, just not something to publish, and matching the *debug* SHA-1
in Firebase rather than the release one.

### Releasing to the Play Store

1. Build an App Bundle rather than an APK: `./gradlew bundleRelease`.
2. Play re-signs your upload with its own key. Add the **Play App Signing** SHA-1
   (Play Console → *Setup → App signing*) to the Firebase Android app too,
   otherwise sign-in works everywhere except installs from the Play Store.
3. Download the refreshed `google-services.json` and replace the one in `app/`.
4. Two things in this build need a word in the Play Console listing: the
   `specialUse` foreground service (declare it under *App content → Foreground
   service permissions*, describing it as keeping a user-authored quote in the
   notification shade) and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. If either is
   more trouble than it is worth for a personal build, turn **Keep running in the
   background** off in ⚙️ Settings and drop both from the manifest — the
   notification itself keeps working without them.

A 512×512 Play Store icon is generated alongside the launcher icons; see
`docs/` in the build output or regenerate it from `app/src/main/res` artwork.

---

## Turning on the notification thought

Nothing to configure in a console for this one — it is all on the phone, and the
app asks for what it needs. Three things are worth knowing:

1. **The notification permission.** On Android 13 and newer the app asks once, on
   first launch. Say yes. Said no by accident? ⚙️ Settings → *Thought in the
   notification bar* shows the problem and takes you to the system screen.
2. **Battery.** ⚙️ Settings → *Battery* → **Allow background activity**. Android
   otherwise puts unused apps to sleep, which can leave the shade empty for a
   while after you swipe. The row says *"Background activity is allowed ✓"* once
   it is done.
3. **Aggressive OEM battery managers.** Samsung, Xiaomi, Oppo, Vivo, Huawei and
   others kill background apps beyond what stock Android does, and they ignore
   the setting above. On those phones also open **Settings → Apps → Ponder** and
   set battery usage to *Unrestricted*, and add Ponder to any "protected" or
   "auto-start" allow-list the phone offers. <https://dontkillmyapp.com> has the
   exact steps per manufacturer.

None of this is required for the feature to work — the notification re-posts
itself from a broadcast receiver, which needs no running process — but it is what
makes it instant rather than occasionally delayed.

The one thing no app can work around: if you **force-stop** Ponder from system
settings, Android blocks all of its receivers until you open it again. That is by
design and applies to every app on the phone.

---

## Adding the home-screen widget

Nothing to set up. Long-press an empty part of the home screen → **Widgets** →
**Ponder**, and drag it where you want it.

- **The shuffle button** in its top corner draws another quote, and grows with the widget. Nothing else
  changes it — resizing, rotating, restarting the phone and switching theme all
  keep the one you were on.
- **Tapping the card** opens the app.
- **Drag its edges** to resize. It adapts as it grows: a small one is the quote
  and the button, a larger one adds the section name, the tag and the source, and
  fits more lines. Text scales with both the widget and the length of the entry,
  so short quotes are set large and long ones shrink to fit rather than being cut.
- **Add more than one.** Each keeps its own quote, so two widgets show two
  different entries.
- **⚙️ Settings → Home screen widget** picks which section they draw from. The row
  only appears once you have placed a widget.

The widget reads the same on-device snapshot the notification does, so it works
with no connection and without being signed in. It refreshes whenever the app
loads entries — so an entry added on another device shows up here after you next
open Ponder.

---

## Troubleshooting

| What you see | What it means |
|---|---|
| "This build has no Google client ID" | `app/google-services.json` is missing, or the `default_web_client_id` string it generates was stripped from the build. `app/src/main/res/raw/keep.xml` exists to stop the release resource shrinker removing it — don't delete it. |
| "This app build isn't registered in Firebase yet" | The package name or SHA-1 in the console doesn't match this build. Check both, then re-download `google-services.json`. |
| "Google sign-in isn't enabled in Firebase yet" | Step 3 above. |
| "No Google account is available on this device" | Add a Google account in Android Settings. Emulators without Play Services can't do Google sign-in at all. |
| "Couldn't load your data" | The Firestore rules aren't published, or there's no connection. Entries you already have stay readable from the cache. |
| Sign-in works, but no entries appear | You signed in with a different Google account than the one used on the web. |
| "This app build isn't registered", but only on the APK you sideloaded | You registered the *debug* SHA-1 and not the release one, or the other way round. Both are in step 1 above; add whichever is missing. |
| No thought in the notification shade | Open ⚙️ Settings. It tells you which of the three it is: notifications blocked by Android, the toggle off, or no entries yet to draw from. |
| The next thought is slow to appear after a swipe | Battery optimisation. See *Turning on the notification thought* above — and if it is a Samsung/Xiaomi/Oppo/Vivo/Huawei, the OEM battery manager as well. |
| The thought stopped changing altogether | Ponder was force-stopped (system settings, or "close" from a task killer). Open the app once and it resumes. |
| It shows in the shade but not on the lock screen | Check **Settings → Notifications → Notifications on lock screen** and pick the option that *includes silent notifications*. Xiaomi/HyperOS, Redmi and POCO hide the silent section there by default, where stock Android and Samsung One UI show it. Ponder's channel sits at normal importance precisely so it isn't caught by that filter, so if this still happens the phone-level setting is the place to look. |
| A permanent "Ponder is running" entry you'd rather not have | ⚙️ Settings → *Keep running in the background* → off. The thought and its swipe-for-the-next-one keep working; it just loses the safety net on phones that kill background apps. |
| The widget says "Nothing saved here yet" | Either there are no entries at all, or ⚙️ Settings → *Home screen widget* is pointed at a section you have not put anything in yet. Tapping the widget opens the app so you can add one. |
| The widget is stuck on one quote | That is deliberate: it changes when you press its shuffle button, and otherwise stays put. Resizing it, restarting the phone and switching theme all keep the quote you were on. |
| The widget's text is cut off | Drag it larger, or shorten the entry. The type shrinks and the line count grows as the widget does, but there is a floor below which it would stop being readable. |

## Checking a release build

Release builds run R8 and the resource shrinker, so it's worth confirming the
Google client ID actually survived into the APK:

```bash
$ANDROID_HOME/build-tools/*/aapt2 dump resources \
  app/build/outputs/apk/release/app-release.apk | grep -c default_web_client_id
```

`1` means it's there. `0` means it was shrunk out and sign-in will fail with a
configuration error that looks exactly like the app was never registered —
check that `app/src/main/res/raw/keep.xml` is still present.

Debug and release are signed with *different* keys, so confirm which certificate
actually signed an APK before blaming Firebase:

```bash
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs <apk>
```

The SHA-1 it prints has to be one of the fingerprints registered on the Firebase
Android app. A release APK should print `be9e699a…`.

---

## Is `google-services.json` safe to commit?

Yes — like the web app's `firebase-config.js`, it holds public project
identifiers, not secrets. Your data is protected by the Firestore security rules
and by Google sign-in. It's currently untracked, so add it deliberately if you
want it in version control.
