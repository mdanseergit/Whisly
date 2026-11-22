$releaseApkSource = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\release\app-release.apk"
$debugApkSource = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\debug\app-debug.apk"

$deliverablesDir = "C:\Users\danse\OneDrive\document\Whisly\deliverables"
$releaseApkTarget = Join-Path $deliverablesDir "whisly-v1.0.4-release.apk"
$debugApkTarget = Join-Path $deliverablesDir "whisly-v1.0.4-debug.apk"

$aapt2 = "C:\Users\danse\AppData\Local\Android\Sdk\build-tools\36.1.0\aapt2.exe"
$apksigner = "C:\Users\danse\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat"
$env:JAVA_HOME = "C:\Users\danse\.antigravity-ide\extensions\redhat.java-1.58.0-win32-x64\jre\25.0.4.1-win32-x86_64"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

# Copy to deliverables
Copy-Item -Path $releaseApkSource -Destination $releaseApkTarget -Force
Copy-Item -Path $debugApkSource -Destination $debugApkTarget -Force

# Generate SHA256
$releaseHash = (Get-FileHash -Path $releaseApkTarget -Algorithm SHA256).Hash
$debugHash = (Get-FileHash -Path $debugApkTarget -Algorithm SHA256).Hash

Set-Content -Path (Join-Path $deliverablesDir "whisly-v1.0.4-release.apk.sha256") -Value $releaseHash
Set-Content -Path (Join-Path $deliverablesDir "whisly-v1.0.4-debug.apk.sha256") -Value $debugHash

# Report sizes
$releaseSizeMB = [math]::Round((Get-Item $releaseApkTarget).Length / 1MB, 2)
$debugSizeMB = [math]::Round((Get-Item $debugApkTarget).Length / 1MB, 2)

Write-Output "=== DELIVERABLE METRICS ==="
Write-Output "Release APK: $releaseApkTarget ($releaseSizeMB MB)"
Write-Output "Release SHA256: $releaseHash"
Write-Output "Debug APK: $debugApkTarget ($debugSizeMB MB)"
Write-Output "Debug SHA256: $debugHash"

Write-Output "`n=== AAPT2 BADGING DUMP ==="
& $aapt2 dump badging $releaseApkTarget | Select-String -Pattern "package|application-label|launchable-activity|sdkVersion|targetSdkVersion"

Write-Output "`n=== APKSIGNER VERIFY RELEASE ==="
& $apksigner verify --verbose $releaseApkTarget

Write-Output "`n=== APKSIGNER VERIFY DEBUG ==="
& $apksigner verify --verbose $debugApkTarget
