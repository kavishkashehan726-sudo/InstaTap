# InstaTap

A lightweight Android utility that taps one point on the screen repeatedly, using the
`AccessibilityService` gesture API and a small floating overlay.

- Package: `com.shehan.instatap`
- Android 7.0 (API 24) or newer
- Kotlin, no third-party libraries, no internet permission. Nothing on screen is read or stored.

## Getting the APK

Every push builds a debug APK on GitHub Actions.

1. Open the repo's **Actions** tab and pick the latest green **Build APK** run.
2. Download **InstaTap-debug-apk** under *Artifacts* and unzip it to get `app-debug.apk`.
3. Copy it to the phone and open it. Allow "Install unknown apps" for your file manager or browser when asked.

## First-time setup on the phone

1. Open InstaTap and tap **Open accessibility settings**. Find InstaTap and turn it on.
2. On Android 13 or newer the switch may be greyed out for sideloaded apps. Tap **Open App info**,
   then the ⋮ menu, choose **Allow restricted settings**, and go back to step 1.
3. Once the service is on, the floating controls appear.

## Using it

- Drag the red crosshair so its centre is on the button you want tapped.
- Press **Start**. The crosshair fades and becomes click-through while tapping runs.
- Press **Stop** to stop. Tapping also stops when the screen turns off, and after the auto-stop timer.
- Drag the control bar by its ⠿ handle. **✕** hides the overlay; **Show overlay** in the app brings it back.

## Settings (in the app)

| Setting | Default | Meaning |
| --- | --- | --- |
| Extra delay between taps | 0 ms | 0 taps as fast as Android accepts gestures |
| Auto-stop after | 60 min | 0 never stops automatically |

## Building locally

Open the folder in Android Studio, or run `./gradlew assembleDebug` with the Android SDK installed.
The APK lands in `app/build/outputs/apk/debug/`.
