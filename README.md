# InstaTap

A lightweight Android utility that taps one point on the screen repeatedly, using the
`AccessibilityService` gesture API and a small floating overlay.

- Package: `com.shehan.instatap`
- Android 7.0 (API 24) or newer
- Kotlin, no third-party libraries, no internet permission. Screen text is only checked on the
  phone to find the configured button; nothing is stored or sent.

## Getting the APK

**Releases (recommended).** Open **Releases → Draft a new release**, create a tag like `v0.2.0`, and
publish. GitHub Actions builds a signed APK named `InstaTap-v0.2.0.apk` and attaches it to the release
within a few minutes. The version name comes from the tag.

**Any push.** Every push also builds an APK under **Actions → Build APK → Artifacts** (a zip containing
the APK). It's release-signed when the signing secrets are set, otherwise debug-signed (`-debug.apk`).

Release builds are signed with the same key every time, so a new version installs as an update. Moving
from a debug build to a release build needs one uninstall first.

### Signing secrets

The workflow reads four repository secrets: `SIGNING_KEYSTORE_BASE64` (the `.jks` keystore, base64),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Never commit the keystore.

### Installing

Because InstaTap uses an accessibility service, Google Play Protect may block installs from a
browser or file manager ("App blocked to protect your device"). Either install from a computer with
`adb install InstaTap-v<version>.apk`, or turn off *Play Store → profile → Play Protect → ⚙️ →
Scan apps with Play Protect* for the install and turn it back on afterwards.

## First-time setup on the phone

1. Open InstaTap and tap **Open accessibility settings**. Find InstaTap and turn it on.
2. On Android 13 or newer the switch may be greyed out for sideloaded apps. Tap **Open App info**,
   then the ⋮ menu, choose **Allow restricted settings**, and go back to step 1.
3. Once the service is on, the floating controls appear.

## Using it

InstaTap has two modes, picked in the app under **Settings**.

**Wait for hire (default).** Press **Start** on the floating bar; it turns orange (*Waiting…*).
Nothing is tapped while waiting, so the phone works normally. The moment a button whose label exactly
matches the configured text (default `චාරිකාව ලබාගන්න, Accept`: PickMe's Sinhala "accept trip" button plus English; case-insensitive, several allowed with commas) appears
in any app, InstaTap taps it once and stops.

**Continuous.** Drag the red crosshair onto the spot, press **Start**, and it taps that point over
and over. While it runs the screen may not respond to your fingers.

Either mode stops with **Volume Down**, the floating Stop button, screen off, or the auto-stop timer.
Drag the control bar by its ⠿ handle. **✕** hides the overlay; **Show overlay** in the app brings it back.

## Settings (in the app)

| Setting | Default | Meaning |
| --- | --- | --- |
| Mode | Wait for hire | Tap the named button once, or tap the target continuously |
| Button text | චාරිකාව ලබාගන්න, Accept | Exact button label(s) to look for, comma-separated |
| Extra delay between taps | 0 ms | 0 taps as fast as Android accepts gestures |
| Auto-stop after | 60 min | 0 never stops automatically |

## Building locally

Open the folder in Android Studio, or run `./gradlew assembleDebug` with the Android SDK installed.
The APK lands in `app/build/outputs/apk/debug/`.
