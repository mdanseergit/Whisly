$env:JAVA_HOME = "C:\Users\danse\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
& "C:\Users\danse\AppData\Local\Android\Sdk\build-tools\36.1.0\aapt2.exe" dump badging "C:\Users\danse\OneDrive\document\Whisly\deliverables\whisly-v1.0.1-splash-fixed-debug.apk" | Select-String -Pattern "package|application-label|launchable-activity"
