Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\release\app-release.apk"
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$zip.Entries | Where-Object { $_.FullName -like 'lib/*' } | Select-Object FullName, @{N='SizeKB';E={[math]::Round($_.Length / 1KB, 1)}} | Format-Table -AutoSize
$zip.Dispose()
