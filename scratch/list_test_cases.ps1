$dir = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\test-results\testDebugUnitTest"
$files = Get-ChildItem -Path $dir -Filter "*.xml"

foreach ($f in $files) {
    [xml]$xml = Get-Content $f.FullName
    Write-Output "Suite: $($xml.testsuite.name)"
    foreach ($tc in $xml.testsuite.testcase) {
        Write-Output "  - $($tc.name) ($($tc.time)s)"
    }
}
