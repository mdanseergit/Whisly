$dir = "C:\Users\danse\AppData\Local\Temp\whisly_build\app\test-results\testDebugUnitTest"
$files = Get-ChildItem -Path $dir -Filter "*.xml"
$totalTests = 0
$totalFailures = 0
$totalErrors = 0
$totalSkipped = 0

foreach ($f in $files) {
    [xml]$xml = Get-Content $f.FullName
    $tests = [int]$xml.testsuite.tests
    $failures = [int]$xml.testsuite.failures
    $errors = [int]$xml.testsuite.errors
    $skipped = [int]$xml.testsuite.skipped
    $time = $xml.testsuite.time
    $name = $xml.testsuite.name

    $totalTests += $tests
    $totalFailures += $failures
    $totalErrors += $errors
    $totalSkipped += $skipped

    [PSCustomObject]@{
        Suite = $name
        Tests = $tests
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
        DurationSec = $time
    }
}

Write-Output "--------------------------------------------------"
Write-Output "TOTALS: Tests: $totalTests, Failures: $totalFailures, Errors: $totalErrors, Skipped: $totalSkipped"
