# Gradle launcher with corrected environment (machine env vars point to a missing D: drive).
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = "$env:USERPROFILE\.android"
& "$PSScriptRoot\gradlew.bat" @args
exit $LASTEXITCODE
