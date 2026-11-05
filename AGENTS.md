# AGENTS.md

## What this is

- Kotlin Android app `com.garen.glyphcharge` for Nothing phones: shows the battery percentage as Glyph progress while charging.
- The app shows the Glyph only when the charger is connected and the phone lies face-down and flat.
- No Compose, AndroidX, WorkManager or Nothing GDK. Platform APIs only. `minSdk 36`, `targetSdk 36`, `compileSdk 37`.
- Test device: Nothing Phone (3a) Pro `A059P`, Nothing OS 5.0 / Android 17. Also targets Nothing OS 4.1 / Android 16.

## UI

- The screen follows Nothing OS 5.0. It is monochrome, uses the device typefaces, and moves in short ease-out steps. `docs/DESIGN.md` has the tokens and rules. `docs/research/2026-ui-landscape.md` has the platform and library research.
- `ui/GlyphTokens.kt` holds colors, type, spacing and easing. `ui/GlyphGaugeView.kt` draws the twelve-dot ring. `ui/NothingSwitch.kt` is the toggle. `ui/NothingUi.kt` builds labels, rows, sections and status cells.
- The app requests `sans-serif`, `geist-mono` and fixed Geist weights from the system. Nothing OS 5.0 maps `sans-serif` to Geist. No font files ship in the APK.
- Do not use NDot55 for interface text. The brand rules reserve it for the logotype and product names.
- No spring or bounce. No parallax. No dependency for the motion. `ValueAnimator` and `PathInterpolator` cover it.
- The raw detector dump sits behind the `DETAILS` row. Keep it there; do not put it back on the main screen.

## Build

- There is no `java` on PATH. Set `JAVA_HOME` to Android Studio's JBR:
  `export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`
- Build: `cmd //c "gradlew.bat :app:assembleDebug --no-daemon --console=plain"`
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `local.properties` must exist with `sdk.dir=C\:\\Users\\garen\\AppData\\Local\\Android\\Sdk` (it is gitignored). The SDK has `platforms/android-37.0` and `build-tools/36.0.0`.
- Gradle 9.8.2 + AGP 9.5 use **built-in Kotlin**. Do not add `org.jetbrains.kotlin.android`; applying it fails because AGP already registers Kotlin.
- Keep `-Xmx1024m` and one worker in `gradle.properties`. A 2 GB heap crashed the Gradle daemon with a native OOM on this machine.
- No tests, lint, CI or formatter. Build plus device logs are the verification.
- No release signing, AAB or Play config yet. A Play release will need the `specialUse` FGS declaration and a review of `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Device

- USB serial `00170158Q001883`; Wi-Fi adb last seen at `192.168.0.47:5555` (DHCP; confirm with `adb devices`).
- After `adb tcpip 5555`, accept the RSA prompt on the phone; both transports can show `unauthorized` until then.
- Install: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch: `adb shell am start -n com.garen.glyphcharge/.MainActivity` (also starts the monitor if enabled).
- Grant notifications: `adb shell pm grant com.garen.glyphcharge android.permission.POST_NOTIFICATIONS`
- `adb logcat -s ChargingMonitor` = weighted detector and service state.
- `adb logcat -s FaceGate` = face-down sensor and gravity confirmation.
- `adb logcat -s MonitorWatchdog` = watchdog and restart events.
- `adb logcat -s GlyphAdapter` = Nothing's renderer.
- `adb shell settings get global glyph_screen_upward_state` = 1 screen up, 2 screen down, 0 other.
- `dumpsys battery` status: 2 charging, 3 discharging, 4 not charging, 5 full. Ignore vendor `Charging state: 0`; it stays 0 even while the level rises.
- Nothing Glyph app: `adb shell am start -n com.nothing.glyph/.MainActivity`. `GlyphProgressActivity` is protected by `com.nothing.glyph.permission.OPEN_ROUTE` and cannot be started by adb.

## Hard-won gotchas

- **`BatteryManager.isCharging()` and the JobScheduler/WorkManager `CHARGING` constraint are broken on this firmware.** They stay false even on a 50 W charger while `status=2` and the level rises. Never reintroduce a `requiresCharging` trigger; it will not fire. The cause is `BatteryController` → `JobSchedulerService.isBatteryCharging()` → `BatteryStateTracker.isConsideredCharging()`, which returns false because the system never sends `ACTION_CHARGING` and the adaptive-charging fallback does not match. The only exception is JobScheduler's top-app exemption, which uses `powerConnected` instead; it needs a running foreground service, so it cannot replace one. A `requiresCharging` job did fire 178 ms after a plug-in once, but only because the app had an FGS and the top bias.
- The app runs a **persistent foreground service** (`ChargingMonitorService`, `specialUse`, notification ID 1002) and samples battery signals. It keeps a minimal notification in the shade; the app is not fully closed while enabled. This is deliberate; do not remove it without discussing the trade-off.
- `ChargingDetector` normalizes over available weights: status 0.40, isCharging 0.15, currentNow 0.15, currentAverage 0.10, ETA 0.05, trend 0.15; threshold 0.50. Trend is recomputed every 60 s from the charge counter, else the level. `ChargingSignals` reads the sticky `ACTION_BATTERY_CHANGED` intent plus `BatteryManager`.
- Two notification IDs, deliberately: **1001** is the Live Update (`charging` channel, `ProgressStyle`, `EXTRA_REQUEST_PROMOTED_ONGOING`; this is what Nothing's listener renders). **1002** is the monitor service (`charging_service`, created at `IMPORTANCE_MIN`, which the OS raises to `LOW` because it is an FGS notification: `dumpsys notification` shows `mOriginalImp=1, mImportance=2`). Never merge them: WorkManager used to re-post the FGS notification and leave a stale Live Update.
- **The service notification is dismissible.** Since Android 14 the user can swipe an ongoing FGS notification away, and the service keeps running. `ChargingNotification.buildService` sets a `deleteIntent` to `ServiceNotificationDismissedReceiver`. The receiver sets an in-memory flag, and `updateServiceNotification` stops re-posting the notification for the rest of that service instance. The notification returns on the next service start: reboot, app update, watchdog restart, or the app launching while the service is dead.
- Nothing OS renders the Live Update through `com.nothing.glyphnotification`; the app appears under Glyph Progress in the Nothing Glyph app and is enabled by default. The Live Update must be ongoing, have a title, use a channel above MIN, and have no custom view.
- **Background survival:** a running FGS survives a recents swipe on this device. A cached process dies on recents removal. `onTaskRemoved` schedules a 2 s restart through `MonitorRestartReceiver`. A persisted `JobScheduler` job (`MonitorWatchdogJob`, ID 1004) runs every 15 min as a fallback. The service writes a heartbeat every 30 s; the job restarts the service when the heartbeat is older than 2 min. A force stop cancels the service, alarms and jobs; the app cannot restart until the user launches it.
- **Battery exemption:** the app declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the "Background access" button opens the system dialog. The test device is `Unrestricted` (user whitelist). Without it, the watchdog cannot start the FGS from the background on Android 12+. On Play, this permission is restricted; use the manual settings link for a release.
- **Face-down gate:** the Nothing `screen_upward` sensor is type 65538, wake-up, on-change, no permission. Its values are 1 = screen up, 2 = screen down, 0 = other. Nothing's own GlyphNotification app uses the same sensor. `FaceDownGate` also reads the `glyph_screen_upward_state` global setting as a fallback. Only value 2 starts a check. The check samples `TYPE_GRAVITY` for 2 s and requires a norm near 9.81, `z < -9.0`, `sqrt(x^2 + y^2) < 2.0` and a maximum deviation below 0.8. A 5 s partial wakelock covers the check. The gate retries three times; the 30 s sample also calls `recheck()`.
- **Face gate behavior:** when the phone is not confirmed face-down, the service cancels Live Update 1001 and keeps FGS 1002. The 1002 text shows `Glyph on: phone is face-down`, `Glyph paused: phone is face-up` or `Waiting for charger`. The "Only when face down" switch defaults on; when off, the old charging-only behavior returns.
- **Nothing's renderer is not a face-down guard:** in a test the `GlyphAdapter` rendered progress while `glyph_screen_upward_state=0`. The app must gate the Live Update itself.
- `BootReceiver` starts the monitor and the watchdog on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`; `MainActivity` starts the monitor on launch; the switch stops both. The service uses `stopWithTask="false"`.
- If the monitor does not restart after boot or an update, set the app to **Unrestricted** with the in-app "Background access" row; the row value shows `UNRESTRICTED` or `OPTIMIZED`.
- `ChargingGlyphWorker.kt`, `ChargingScheduler.kt`, `BatteryState.kt` and `PowerReceiver.kt` are dead legacy files. `PowerReceiver` is not in the manifest because `ACTION_POWER_CONNECTED` is not delivered to background apps. Do not edit them expecting behaviour.
- No `com.nothing.ketchum` GDK and no API key. The Live Update path is the production path. Adding the GDK means per-device renderers (36-zone strip, 6-zone bar, Glyph Matrix) and needs explicit approval.
- Git repository on `main`; no remote or PR workflow. `opencode.json` lists a `DEPLOYMENT.md` that does not exist; do not assume deployment docs.
- Root `build.log`, `hs_err_pid*.log` and `replay_pid*.log` are generated crash noise, not config.

## Verify a charge

- Build and install; `adb logcat -s ChargingMonitor -s FaceGate -s MonitorWatchdog` must show the service, sensor and watchdog events.
- Face-up: `glyph_screen_upward_state` is 0 or 1, `confirmed=false`, no Live Update 1001, and 1002 says `Glyph paused: phone is face-up`.
- Face-down flat: the state is 2; after about 2 s `FaceGate` logs `confirmation result=true` and `confirmed=true`, then `ChargingMonitor` logs `glyph on level=...` and `GlyphAdapter progress = <level>`.
- Pick up: `confirmed=false`, `ChargingMonitor` logs `glyph off`, Live Update 1001 is cancelled, and 1002 says `Glyph paused: phone is face-up`.
- Unplug or charge limit: score < 0.50 -> Live Update cancelled, Glyph dark, service notification 1002 still present.
- Switch off "Only when face down": the glyph follows charging only, and 1002 says `Glyph on: always while charging`.
- Background: swipe the task away; a running service stays and `onTaskRemoved` logs the restart. Kill the process to test `START_STICKY`. Force the watchdog with `adb shell cmd jobscheduler run -f com.garen.glyphcharge 1004`.
- Battery: the "Background access" row shows `UNRESTRICTED`; `adb shell dumpsys deviceidle whitelist | grep glyphcharge` shows `user,com.garen.glyphcharge`.
- The `DETAILS` panel has no trend history; trust the service log for the real decision.
