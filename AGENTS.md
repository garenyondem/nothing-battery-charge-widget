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
- `MainActivity` renders the FACE/GLYPH cells from preferences on a 1 s tick while resumed. The service writes face and charging state there without any broadcast, so no battery event announces a face flip. The tick compares a prefs-only `MonitorKey` and re-renders only on a change; no binder calls while idle. While the monitor is live (heartbeat < 60 s), the cells show the service's persisted decision (`last_charging`), not a local detector evaluation; the local evaluation is only the fallback when the monitor is off or dead. The DETAILS panel adds `svc charging`/`svc idle` next to the local detector dump.
- The footer `DAY`/`NIGHT` label is a text button. It stores `theme_mode` in `settings`, forces the theme through the activity base context, and calls `recreate()`. With no stored value the app follows the system.

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
- **Face-down gate:** the Nothing `screen_upward` sensor is type 65538, wake-up, on-change, no permission. Its values are 1 = screen up, 2 = screen down, 0 = other. Nothing's own GlyphNotification app uses the same sensor. `FaceDownGate` also reads the `glyph_screen_upward_state` global setting as a fallback. Only value 2 starts a check. The check samples `TYPE_GRAVITY` for 2 s and requires a norm near 9.81, `z < -8.5`, `sqrt(x^2 + y^2) < 4.9` and a maximum deviation below 1.2. This accepts up to about 30 degrees of tilt from perfectly flat, so uneven desks and the camera bump do not block the glyph. A 5 s partial wakelock covers the check. The gate retries three times; the 30 s sample also calls `recheck()`.
- **Face gate behavior:** when the phone is not confirmed face-down, the service cancels Live Update 1001 and keeps FGS 1002. The 1002 text shows `Glyph on: phone is face-down`, `Glyph paused: phone is face-up` or `Waiting for charger`. The "Only when face down" switch defaults on; when off, the old charging-only behavior returns.
- **Nothing's renderer is not a face-down guard:** in a test the `GlyphAdapter` rendered progress while `glyph_screen_upward_state=0`. The app must gate the Live Update itself.
- `BootReceiver` starts the monitor and the watchdog on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`; `MainActivity` starts the monitor on launch; the switch stops both. The service uses `stopWithTask="false"`.
- If the monitor does not restart after boot or an update, set the app to **Unrestricted** with the in-app "Background access" row; the row value shows `UNRESTRICTED` or `OPTIMIZED`.
- `ChargingGlyphWorker.kt`, `ChargingScheduler.kt`, `BatteryState.kt` and `PowerReceiver.kt` are dead legacy files. `PowerReceiver` is not in the manifest because `ACTION_POWER_CONNECTED` is not delivered to background apps. Do not edit them expecting behaviour.
- No `com.nothing.ketchum` GDK and no API key. The Live Update path is the production path. Adding the GDK means per-device renderers (36-zone strip, 6-zone bar, Glyph Matrix) and needs explicit approval.
- Git repository on `main`; origin is the public GitHub repository; no PR workflow. `opencode.json` lists a `DEPLOYMENT.md` that does not exist; do not assume deployment docs.
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

## Release

### Release flow

1. A maintainer pushes a tag in the form `v<major>.<minor>.<patch>`.
2. GitHub runs `.github/workflows/release.yml` on `ubuntu-24.04` with JDK 17.
3. The workflow decodes the release keystore from repository secrets.
4. It runs `:app:assembleRelease` with the tag version.
5. It checks the signature, version and debug flag.
6. It attaches the APK and `SHA256SUMS.txt` to the GitHub Release.
7. If the release exists, the workflow uploads the files to it. It does not create a duplicate.

### Version rules

- The tag sets `versionName`. `v0.1.0` gives `0.1.0`.
- The tag sets `versionCode`. The formula is `major * 10000 + minor * 100 + patch`.
- `v0.1.0` gives `100`. `v1.2.3` gives `10203`.
- Keep `minor` and `patch` below 100.
- Use a higher version for each release.
- A pre-release tag such as `v0.2.0-rc.1` is allowed. It uses the same `versionCode` as `v0.2.0`.

### Release steps

1. Merge the release changes to `main`.
2. Set the version defaults in `app/build.gradle.kts` to the release values, for example `0.1.0` and `100`. Commit this change. A tagged commit must build its own version without `-P` flags; F-Droid builds tags that way.
3. Confirm that the local branch matches `origin/main`.
4. Create the tag: `git tag v0.1.0`.
5. Push the tag: `git push origin v0.1.0`.
6. Open the `Release` workflow run in the Actions tab.
7. Confirm that the release has `GlyphCharge-0.1.0.apk` and `SHA256SUMS.txt`.
8. Download the APK and `SHA256SUMS.txt`.
9. Verify the download: `sha256sum -c SHA256SUMS.txt`.
10. Install the APK and test it on the phone.

### Manual run

1. Create and push the tag first. The manual input does not create a tag.
2. Open Actions > Release > Run workflow.
3. Enter the existing tag, for example `v0.1.0`.
4. Start the run.

### Repository secrets

The workflow needs four repository secrets. Set them in Settings > Secrets and variables > Actions > Secrets.

- `KEYSTORE_BASE64`: the Base64 form of the release keystore.
- `KEYSTORE_PASSWORD`: the keystore password.
- `KEY_ALIAS`: the key alias.
- `KEY_PASSWORD`: the key password.

To set the secrets with the GitHub CLI:

1. Encode the keystore: `base64 -w 0 <path-to-keystore> > keystore.base64`.
2. Run `gh secret set KEYSTORE_BASE64 < keystore.base64`.
3. Run `gh secret set KEYSTORE_PASSWORD`.
4. Run `gh secret set KEY_ALIAS`.
5. Run `gh secret set KEY_PASSWORD`.
6. Delete `keystore.base64`.

The last three commands prompt for the value. Do not write secret values in this file, the repository, logs, issues or pull requests.
The workflow uses `GITHUB_TOKEN` for the release. Do not create a personal access token for normal releases.

### Release keystore

- Keep the keystore outside the repository. The expected folder is `%USERPROFILE%\.glyphcharge`.
- The folder holds the keystore, `keystore.properties` and a local README.
- Never commit the keystore, `keystore.properties` or the encoded file.
- Back up the folder offline. A lost key blocks updates for existing installs.
- A leaked key lets another person sign updates. Rotate the key only with a new application ID or a user uninstall.
- The keystore is PKCS12 with an RSA 4096 key. The Gradle signing config expects PKCS12.
- To update `KEYSTORE_BASE64`, encode the keystore with `base64 -w 0` and set the secret again. Do not commit the encoded file.

### Local signed build

1. Set `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` to the local values.
2. Run `./gradlew :app:assembleRelease -PversionName=0.1.0 -PversionCode=100`.
3. Find the APK at `app/build/outputs/apk/release/app-release.apk`.

Without the four variables, `assembleRelease` is unsigned. The debug build does not need the variables.

### First release install

- The release key differs from the debug key.
- Uninstall `com.garen.glyphcharge` before the first release install.
- The uninstall clears app settings. Re-enable the monitor and background access.
- Later releases install over earlier release builds.

### Release checks

- `Tag must look like v1.2.3`: use three numeric parts in the tag.
- `KEYSTORE_BASE64 is empty or invalid`: set the secret again with a complete Base64 file.
- The APK is unsigned or `apksigner` fails: check all four secrets. The signing config needs all four values.
- `Keystore file not set`: check `KEYSTORE_PATH` and the keystore file.
- `403` on release creation: check Settings > Actions > General > Workflow permissions. Allow read and write, or check the organization policy.
- The workflow did not run: confirm the tag starts with `v` and the workflow file is on the tagged commit.
- The release has no APK: open the failed run and re-run it. The upload step is safe to repeat.
- The release has the wrong APK: delete the asset and re-run the workflow. Do not reuse a version code for different code.
- To delete a bad release and its tag: `gh release delete v0.1.0 --yes --cleanup-tag`. Use this only before users install the APK.

## F-Droid

- The distribution plan is the **f-droid.org main repository**, following the developer-binary model used by `com.gymmane.app`. F-Droid downloads the signed APK from the GitHub release (`binary:`), verifies it against the pinned certificate fingerprint (`AllowedAPKSigningKeys`), and publishes it. The Gradle recipe stays in the metadata as the fallback source build. GitHub releases stay the primary channel; F-Droid is a second front, not a replacement.
- **Signatures:** on the binary path the F-Droid APK is our own APK, so GitHub and F-Droid installs share a signature and update into each other. If F-Droid falls back to a source build and re-signs, the fronts split: installs cannot cross-update, and switching requires an uninstall that clears app settings. Either outcome is accepted; do not build a migration.
- `AllowedAPKSigningKeys` pins the release certificate fingerprint `a2ad5f9f15f0e0fee08caab7a86d7db210fec0e2a22c420dfa6b752570170bdf`. If the release keystore is ever rotated, the fdroiddata metadata must be updated first, and existing users must reinstall.
- `fastlane/metadata/android/en-US/` holds the upstream metadata F-Droid reads automatically: `short_description.txt` (max 80 characters, no trailing dot), `full_description.txt`, `title.txt`, `images/icon.png` (512 px), `images/phoneScreenshots/`, and `changelogs/<versionCode>.txt`. Update these with each release.
- A tagged commit must build its own `versionName` and `versionCode` with a plain `assembleRelease` and no `-P` flags. That is why step 2 of the release steps commits the version defaults. Without the four keystore env vars the release build is unsigned, which is exactly what F-Droid builds and signs.
- Submission is a merge request to the `fdroiddata` repository on GitLab: fork, branch `com.garen.glyphcharge`, add `metadata/com.garen.glyphcharge.yml` (MIT license, GitHub `SourceCode` and `IssueTracker`, one `Builds` entry with plain Gradle, `UpdateCheckMode: Tags`, `AutoUpdateMode: Version`, `Ignore` for `-rc` tags). The fork's CI lints the file. After the merge the app appears within 24 to 48 hours, and future tags flow in through F-Droid's update automation.
- Review answers to have ready: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` exists so the watchdog can restart the face-down charging meter from the background; the user triggers it from the in-app Background access row. The `specialUse` foreground service samples battery signals and posts the Live Update; it is the documented core of the app. `minSdk 36` matches Nothing OS, the only phones with a Glyph Interface.
- IzzyOnDroid stays a zero-effort fallback: it serves the existing signed GitHub release APKs as-is. Use it only if f-droid.org inclusion stalls.
