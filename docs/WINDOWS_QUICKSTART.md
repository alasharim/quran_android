# Windows quick start

Copy and paste steps to go from a Windows PC to the app running on a phone. Run every command in
**PowerShell** as your normal user, not as administrator, unless a step says otherwise.

## Where things go

| What | Where | Why |
| --- | --- | --- |
| The code | `X:\dev\quran_android` | X is an NVMe SSD with plenty of free space, and the path is short. Long paths break Windows builds. |
| Gradle's download cache | `X:\dev\gradle-home` | It grows to 10 GB or more. This keeps it off the nearly full C drive. |
| Android SDK | leave it at `%LOCALAPPDATA%\Android\Sdk` | It is already installed there. |

Avoid spinning hard disks such as the M and G drives. Builds run several times slower on them.
To see which drives are SSDs, run:

```powershell
Get-PhysicalDisk | Select-Object FriendlyName, MediaType, Size
```

If you pick a different drive, replace `X:` with that letter everywhere below.

## 1. Install Git, VS Code and JDK 21

```powershell
winget install --id Git.Git -e
winget install --id Microsoft.VisualStudioCode -e
winget install --id EclipseAdoptium.Temurin.21.JDK -e
```

Skip any line for something already installed. JDK 17 may work too, but 21 matches what the
build is tested with more closely. JDK 27 does not work yet.

**Close PowerShell and open a new window** so the new programs are found.

## 2. Set environment variables

Paste this whole block at once:

```powershell
$jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-21*" | Select-Object -First 1).FullName
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk, "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "X:\dev\gradle-home", "User")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*platform-tools*") {
  [Environment]::SetEnvironmentVariable("Path", "$userPath;$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
}
```

**Close PowerShell and open a new window again**, then check:

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java" -version
```

The second line should print a version starting with 21.

## 3. Install the Android 37 platform

The app is compiled against Android 37, which you do not have yet. First see whether the SDK
command line tools are installed:

```powershell
Test-Path "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
```

**If it prints True**, run these two lines and answer `y` to every licence question:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-37" "platform-tools"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

**If it prints False**, open Android Studio and go to More Actions, then SDK Manager.

- On the SDK Platforms tab, tick **Android 37**.
- On the SDK Tools tab, tick **Android SDK Command-line Tools (latest)** and
  **Android SDK Platform-Tools**.
- Click Apply and accept the licences.

Then check that `adb` works:

```powershell
adb version
```

## 4. Download the code

```powershell
git config --global core.longpaths true
New-Item -ItemType Directory -Force X:\dev | Out-Null
cd X:\dev
git clone -b claude/code-review-7yxzd0 https://github.com/alasharim/quran_android.git
cd quran_android
```

If a GitHub sign in window appears, sign in with the account that owns the repository.

## 5. Do the first build

```powershell
.\gradlew.bat assembleMadaniDebug
```

The first build downloads everything it needs and can take 10 to 20 minutes. It is finished when
you see `BUILD SUCCESSFUL`. Later builds take a minute or two.

## 6. Open the project in VS Code

```powershell
code .
```

When VS Code asks whether to install the recommended extensions, click **Install**.

## 7. Connect the Galaxy Z Fold

1. On the phone open Settings, About phone, Software information, and tap **Build number** seven
   times.
2. Go back to Settings, open Developer options, and turn on **USB debugging**.
3. Plug the phone into the PC and tap **Allow** on the phone.
4. In PowerShell run `adb devices`. The phone should be listed with the word `device`.

If the list is empty, Windows is missing the Samsung driver. Install the **Samsung Android USB
Driver** from developer.samsung.com, unplug, plug in again, and retry.

## 8. Run the app

In VS Code press **Ctrl+Shift+P**, type `Run Task`, choose **Tasks: Run Task**, then choose
**Build, install and launch**.

The app opens on the phone as a separate "Quran Madani" debug app, so your normal Quran app is
untouched. On first launch it downloads the page images. Unfold the phone to see two pages side by
side and swipe to turn them.

## Optional: faster builds

Bitdefender scans every file Gradle writes, which slows builds a lot. Adding `X:\dev` to
Bitdefender's exclusions list makes builds noticeably faster.

## When something goes wrong

| Message | Fix |
| --- | --- |
| `SDK location not found` | Step 2 did not stick. Open a new PowerShell window and check `$env:ANDROID_HOME`. |
| `failed to find target with hash string 'android-37'` | Step 3 was skipped. |
| `Unsupported class file major version` | Gradle is using JDK 27. Check `$env:JAVA_HOME`. |
| `adb` is not recognized | Platform tools are missing or PowerShell was not reopened after step 2. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Run `adb uninstall com.quran.labs.androidquran.debug` and try again. |
