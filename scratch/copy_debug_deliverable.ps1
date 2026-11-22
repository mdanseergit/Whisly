$src = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\debug\app-debug.apk"
$dest = "C:\Users\danse\OneDrive\document\Whisly\deliverables\whisly-v1.0.3-debug.apk"
Copy-Item -Path $src -Destination $dest -Force
$sha = (Get-FileHash -Path $dest -Algorithm SHA256).Hash.ToLower()
$shaFile = "$dest.sha256"
"$sha  whisly-v1.0.3-debug.apk" | Out-File -FilePath $shaFile -Encoding ascii
Write-Output "Copied to $dest"
Write-Output "Size: $((Get-Item $dest).Length) bytes"
Write-Output "SHA-256: $sha"
