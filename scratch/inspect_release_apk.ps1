Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\release\app-release.apk"
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
Write-Output "APK Total Length: $((Get-Item $apk).Length) bytes"
$groups = $zip.Entries | Group-Object { ($_.FullName -split '/')[0] }
foreach ($g in $groups) {
    $comp = ($g.Group | Measure-Object -Property CompressedLength -Sum).Sum
    $uncomp = ($g.Group | Measure-Object -Property Length -Sum).Sum
    [PSCustomObject]@{
        Folder = $g.Name
        Count = $g.Count
        CompressedMB = [math]::Round($comp / 1MB, 2)
        UncompressedMB = [math]::Round($uncomp / 1MB, 2)
    }
}
Write-Output "`nTop 10 largest files in release APK:"
$zip.Entries | Sort-Object Length -Descending | Select-Object -First 10 | ForEach-Object {
    [PSCustomObject]@{
        File = $_.FullName
        CompressedKB = [math]::Round($_.CompressedLength / 1KB, 1)
        UncompressedKB = [math]::Round($_.Length / 1KB, 1)
    }
}
$zip.Dispose()
