# Digital Wellbeing-style app (personal use, OnePlus 10R)

Kotlin, no third-party libraries. minSdk 31 (Android 12), targets Android 14.

Screens: Home, Dashboard (week charts for screen time / notifications / unlocks, Categories toggle, View all),
Weekly report (daily average + goal line, most used category, swipeable peak-usage heatmap, most used apps),
Screen time goal (4-week calendar rings + goal picker), and a home-screen widget.

## Build the APK (no Android Studio, no PC software)
1. Make a free account at github.com and create a new **private** repository.
2. Upload **everything inside the `ScreenTime` folder** (including the hidden `.github` folder) to it.
3. Open the repo's **Actions** tab, choose **Build APK**, press **Run workflow**.
4. After ~5 minutes open the finished run, download the **ScreenTime-apk** artifact, unzip it -> `app-debug.apk`.
5. Move the APK to your phone and install it.

## Or build with Android Studio
Open this folder in Android Studio, let it sync, plug in the phone with USB debugging on (or start an emulator, Pixel 6 / API 34), press Run.

## First launch on the phone
- Home screen -> **Open settings** -> pick the app -> turn on **Permit usage access**.
- Notification counts: Home -> ⋮ -> **Notification access** -> enable. (If it is greyed out: Settings -> Apps -> this app -> ⋮ -> **Allow restricted settings**, then try again.)
- Widget: long-press home screen -> Widgets -> **Screen Time**.
- Battery: Settings -> Battery -> App battery management -> this app -> **Unrestricted** (keeps the widget updating).

## Notes
Android only keeps about a week of detailed usage events. The app saves each finished day on the phone, so older weeks and the goal calendar fill in from the day you install it.
