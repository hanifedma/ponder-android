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

4. **Debug signing certificate SHA-1** — paste the fingerprint of the debug
   keystore on this machine:

   ```
   3D:29:58:C6:75:80:7A:A5:49:1F:A4:D2:56:1F:FB:8B:BB:2B:0D:A6
   ```

   > Building on a different computer later? Get that machine's fingerprint with
   > `./gradlew signingReport` and add it as an extra SHA-1 on the same app.

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

## Releasing to the Play Store

The release build is signed with the **debug** key by default so that
`./gradlew assembleRelease` produces something you can install and test
immediately. Before publishing:

1. Create a release keystore and add a real `signingConfig` in
   `app/build.gradle.kts`, replacing
   `signingConfig = signingConfigs.getByName("debug")`.
2. Add that keystore's SHA-1 **and** the Play App Signing SHA-1 (Play Console →
   *Setup → App signing*) to the Firebase Android app, otherwise sign-in will
   fail only for installs from the Play Store.
3. Download the refreshed `google-services.json` and replace the one in `app/`.

A 512×512 Play Store icon is generated alongside the launcher icons; see
`docs/` in the build output or regenerate it from `app/src/main/res` artwork.

---

## Troubleshooting

| What you see | What it means |
|---|---|
| "This app build isn't registered in Firebase yet" | The package name or SHA-1 in the console doesn't match this build. Check both, then re-download `google-services.json`. |
| "Google sign-in isn't enabled in Firebase yet" | Step 3 above. |
| "No Google account is available on this device" | Add a Google account in Android Settings. Emulators without Play Services can't do Google sign-in at all. |
| "Couldn't load your data" | The Firestore rules aren't published, or there's no connection. Entries you already have stay readable from the cache. |
| Sign-in works, but no entries appear | You signed in with a different Google account than the one used on the web. |

## Is `google-services.json` safe to commit?

Yes — like the web app's `firebase-config.js`, it holds public project
identifiers, not secrets. Your data is protected by the Firestore security rules
and by Google sign-in. It's currently untracked, so add it deliberately if you
want it in version control.
