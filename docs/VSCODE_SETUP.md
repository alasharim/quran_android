# Building and running Quran for Android from VS Code

Android Studio is the officially supported IDE for this project, but everything needed to
build, install, and run the app is plain Gradle and `adb`, so VS Code works well as an editor
plus task runner. This guide gets you from a fresh machine to the app running on a device.

## 1. Install a JDK

Gradle in this repo (9.7.1) runs on JDK 17 through 26. JDK 27 is **not** supported yet and fails
with `Unsupported class file major version 71`. Continuous integration uses JDK 26; a JDK 21
LTS build (for example Eclipse Temurin) is the safest choice.

Check with:

```
java -version
```

If you have several JDKs installed, point Gradle at the right one by adding this line to
`~/.gradle/gradle.properties` (create the file if needed):

```
org.gradle.java.home=/path/to/your/jdk
```

## 2. Install the Android SDK command line tools

You do not need Android Studio. Download the "command line tools only" package from
https://developer.android.com/studio#command-line-tools-only and unpack it so that the layout is:

```
<sdk root>/cmdline-tools/latest/bin/sdkmanager
```

Then install what the build needs and accept the licenses:

```
sdkmanager "platform-tools" "platforms;android-37"
sdkmanager --licenses
```

The Android Gradle Plugin downloads its own build tools on the first build once the licenses are
accepted.

## 3. Tell the build where the SDK is

Either set the environment variable:

```
export ANDROID_HOME=<sdk root>
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

or create `local.properties` in the repository root (it is git ignored):

```
sdk.dir=<sdk root>
```

Use forward slashes or escaped backslashes on Windows, for example `C\:\\Android\\sdk`.

## 4. Open the project in VS Code

Open the repository folder. VS Code will offer the recommended extensions from
`.vscode/extensions.json`:

- **Gradle for Java** runs Gradle tasks from the sidebar.
- **Kotlin** gives syntax highlighting and basic navigation. Full Android aware code completion
  is only available in Android Studio; expect some unresolved symbols for generated code.
- **Language Support for Java** covers the remaining Java files.
- **Android** (adelphes) adds logcat and a debugger that can attach to the running app.

## 5. Build, install, run

The tasks in `.vscode/tasks.json` wrap the commands you would otherwise type. Open the command
palette, choose **Tasks: Run Task**, and pick one:

| Task | What it does |
| --- | --- |
| Build debug APK | `./gradlew assembleMadaniDebug` (also bound to the default build shortcut) |
| Install on connected device | `./gradlew installMadaniDebug` |
| Launch app on device | starts the main activity through `adb` |
| Build, install and launch | the two above in sequence |
| Run unit tests | `./gradlew testMadaniDebugUnitTest` (default test task) |
| Run page turn tests only | just the book page turn geometry tests |
| Lint | `./gradlew lintMadaniDebug` |
| Show app logs (logcat) | streams the app's log output |
| List connected devices | `adb devices -l` |

The first build downloads a lot of dependencies and takes several minutes. Later builds are
incremental.

The debug build installs as `com.quran.labs.androidquran.debug`, so it can live next to the
Play Store version on the same device.

## 6. Connect a Galaxy Z Fold

1. Enable Developer options: Settings → About phone → Software information → tap
   **Build number** seven times.
2. In Settings → Developer options, turn on **USB debugging** (or **Wireless debugging** if you
   prefer no cable).
3. Plug in the phone and accept the "Allow USB debugging" prompt. For wireless debugging, tap
   **Pair device with pairing code** on the phone and run `adb pair <ip>:<port>`, then
   `adb connect <ip>:<port>`.
4. Run the **List connected devices** task and confirm the phone appears as `device`.

Then run **Build, install and launch**.

## 7. Trying the book page turn

On the first launch the app downloads the page images. After that:

- Unfold the phone. Two pages appear side by side with a soft spine shadow in the middle, and
  swiping turns a page over the spine.
- Settings → Dual Page Preferences → **Book page turn** switches the animation off or on.
- **Dual Page Mode** in the same section controls whether two pages are shown when unfolded.

## Troubleshooting

- `SDK location not found`: `ANDROID_HOME` or `local.properties` is missing or wrong.
- `Unsupported class file major version`: Gradle is running on a JDK newer than 26.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an older debug build signed with a different key is
  installed. Run `adb uninstall com.quran.labs.androidquran.debug` and install again.
- Build runs out of memory: raise `-Xmx4G` in `gradle.properties` or close other Gradle daemons
  with `./gradlew --stop`.
