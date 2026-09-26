# Reloads the build-related environment variables from Windows' saved settings.
#
# VS Code only reads environment variables when it starts, so if it was open
# while JAVA_HOME or ANDROID_HOME were being set up, its tasks would still use
# the old values (for example an older JDK). Dot-source this before running a
# build so tasks always see the current settings:
#
#   . ./tools/vscode/refresh-env.ps1; ./gradlew.bat assembleMadaniDebug

foreach ($name in "JAVA_HOME", "ANDROID_HOME", "GRADLE_USER_HOME") {
  $value = [Environment]::GetEnvironmentVariable($name, "User")
  if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, "Machine") }
  if ($value) { Set-Item -Path "Env:$name" -Value $value }
}

$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = (@($machinePath, $userPath) | Where-Object { $_ }) -join ";"
