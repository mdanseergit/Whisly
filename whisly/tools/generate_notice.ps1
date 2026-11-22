# Generates licenses/NOTICE from the canonical inventory in MODELS.md.
# Usage: powershell -ExecutionPolicy Bypass -File tools\generate_notice.ps1
# Do not edit licenses/NOTICE directly.

param(
  [string] $ModelsPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'MODELS.md'),
  [string] $OutputPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'licenses\NOTICE')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ModelsPath)) {
  throw "MODELS.md not found: $ModelsPath"
}

$lines = Get-Content -LiteralPath $ModelsPath
$startIndex = -1
$endIndex = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -eq '<!-- NOTICE-GENERATOR:START -->' -and $startIndex -lt 0) { $startIndex = $i }
  if ($lines[$i] -eq '<!-- NOTICE-GENERATOR:END -->' -and $startIndex -ge 0) { $endIndex = $i; break }
}
if ($startIndex -lt 0 -or $endIndex -lt 0 -or $endIndex -le $startIndex) {
  throw 'NOTICE generator markers not found or malformed in MODELS.md'
}

function Convert-Cell([string] $value) {
  return $value.Trim().Replace('`', '')
}

$rows = New-Object System.Collections.Generic.List[object]
for ($i = $startIndex + 1; $i -lt $endIndex; $i++) {
  $line = $lines[$i].Trim()
  if (-not $line.StartsWith('|') -or -not $line.EndsWith('|')) { continue }
  $cells = @($line.Substring(1, $line.Length - 2).Split('|') | ForEach-Object { Convert-Cell $_ })
  if ($cells.Count -ne 8) { continue }
  if ($cells[0] -eq 'ID' -or $cells[0] -match '^-+$') { continue }
  $rows.Add([pscustomobject]@{
      Id = $cells[0]
      Kind = $cells[1]
      Name = $cells[2]
      Version = $cells[3]
      Source = $cells[4]
      License = $cells[5]
      Status = $cells[6]
      Notes = $cells[7]
    })
}

if ($rows.Count -eq 0) {
  throw 'No inventory rows were parsed from MODELS.md'
}

$out = New-Object System.Collections.Generic.List[string]
$out.Add('# Whisly Third-Party Software, Model and Asset Notices')
$out.Add('')
$out.Add('> GENERATED FILE. Do not edit by hand. Source: `MODELS.md`. Regenerate with `powershell -ExecutionPolicy Bypass -File tools\generate_notice.ps1`.')
$out.Add('')
$out.Add('## 1. License gate')
$out.Add('')
$out.Add('Permitted without additional approval: MIT, Apache-2.0, BSD-3-Clause, CC0-1.0, SIL OFL-1.1, Public Domain, and the ML Kit Terms of Service as declared by Google for the pinned ML Kit artifact.')
$out.Add('')
$out.Add('Blocked from APK distribution without explicit user approval: GPL-2.0, GPL-3.0, LGPL, CC-BY-NC variants, CC-BY-SA variants, gated or terms-acceptance weights, and any model whose license is `CONFLICTING_EVIDENCE` or `UNVERIFIED`.')
$out.Add('')
$out.Add('`EPL-1.0` appears only in test-only JUnit 4 and is not bundled into the shipped app artifact.')
$out.Add('')
$out.Add(('Generated from `MODELS.md` on {0} from {1} canonical inventory rows.' -f (Get-Date -Format 'yyyy-MM-dd'), $rows.Count))
$out.Add('')
$out.Add('## 2. Generated inventory')
$out.Add('')
$out.Add('| ID | Kind | Name | Version / revision | Source URL | License / terms | Status | Notes |')
$out.Add('|---|---|---|---|---|---|---|---|')
foreach ($row in $rows) {
  $out.Add('| ' + ($row.Id, $row.Kind, $row.Name, $row.Version, $row.Source, $row.License, $row.Status, $row.Notes -join ' | ') + ' |')
}
$out.Add('')

$outputDirectory = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDirectory)) {
  New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
Set-Content -LiteralPath $OutputPath -Value $out -Encoding UTF8
Write-Output "Generated $OutputPath from $($rows.Count) rows in $ModelsPath"
