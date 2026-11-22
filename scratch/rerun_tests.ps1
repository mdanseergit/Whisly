$env:JAVA_HOME = "C:\Users\danse\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
$env:ANDROID_HOME = "C:\Users\danse\AppData\Local\Android\Sdk"
Set-Location "C:\Users\danse\OneDrive\document\Whisly\whisly"
& ".\gradlew.bat" testDebugUnitTest --rerun-tasks --info --no-daemon
