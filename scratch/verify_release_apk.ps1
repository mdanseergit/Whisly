$apk = "C:\Users\danse\OneDrive\document\Whisly\deliverables\whisly-v1.0.3-release.apk"
$aapt2 = "C:\Users\danse\AppData\Local\Android\Sdk\build-tools\36.1.0\aapt2.exe"
$apksigner = "C:\Users\danse\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat"
$java = "C:\Users\danse\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin\java.exe"

$env:JAVA_HOME = "C:\Users\danse\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

Write-Output "=== AAPT2 BADGING DUMP ==="
& $aapt2 dump badging $apk | Select-String -Pattern "package|application-label|launchable-activity|sdkVersion|targetSdkVersion"

Write-Output "`n=== APKSIGNER VERIFY ==="
& $apksigner verify --verbose $apk
