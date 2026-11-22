# Move Whisly out of OneDrive

Do this before continuing voice work. The repository is currently at `C:\Users\danse\OneDrive\document\Whisly`, which is inside OneDrive. A verified backup already exists outside OneDrive:

- Path: `C:\dev\Whisly-backups\Whisly_pre-voice-reset_20261122_113141.zip`
- Bytes: `254,493,504`
- SHA-256: `2ED041F15DF8EC92ACE7A77FD26E4A367DCC36D2AC030A001096F86E9BF8793B`
- Archive entries: `2,338`
- Source bytes at backup time: `374,838,773`

Run these commands from PowerShell. Do not delete the old folder until step 7 passes.

## 1. Stop editors and Gradle daemons

Close Android Studio, then run:

```powershell
Set-Location "C:\Users\danse\OneDrive\document\Whisly\whisly"
.\gradlew.bat --stop
```

## 2. Re-verify the backup hash

```powershell
$backup = "C:\dev\Whisly-backups\Whisly_pre-voice-reset_20261122_113141.zip"
Get-FileHash -LiteralPath $backup -Algorithm SHA256 | Format-List
```

Expected SHA-256: `2ED041F15DF8EC92ACE7A77FD26E4A367DCC36D2AC030A001096F86E9BF8793B`.

## 3. Copy to the new location

```powershell
New-Item -ItemType Directory -Force -Path "C:\dev\Whisly" | Out-Null
robocopy "C:\Users\danse\OneDrive\document\Whisly" "C:\dev\Whisly" /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 /XJ /NFL /NDL /NP
```

`robocopy` exit codes `0` through `7` mean success. Codes `8` or higher mean an error; stop and report the output.

## 4. Verify the copy has no missing files

```powershell
$src = "C:\Users\danse\OneDrive\document\Whisly"
$dst = "C:\dev\Whisly"
$srcHash = Get-ChildItem -LiteralPath $src -Recurse -Force -File | Get-FileHash -Algorithm SHA256 | Sort-Object Hash | ForEach-Object Hash
$dstHash = Get-ChildItem -LiteralPath $dst -Recurse -Force -File | Get-FileHash -Algorithm SHA256 | Sort-Object Hash | ForEach-Object Hash
[pscustomobject]@{
  SourceFiles = $srcHash.Count
  DestinationFiles = $dstHash.Count
  HashesEqual = (@(Compare-Object $srcHash $dstHash).Count -eq 0)
} | Format-List
```

`HashesEqual` must be `True`. If it is `False`, stop and report the comparison output.

## 5. Verify git state in the new location

```powershell
git -C "C:\dev\Whisly" log --oneline --decorate -3
git -C "C:\dev\Whisly" status --short
```

Expected HEAD: `033b53f (HEAD -> voice-reset, tag: pre-voice-reset, main) Pre-voice-reset baseline`. Working-tree changes from Step 1 are expected because cleanup is in progress.

## 6. Build and test from the new location

```powershell
Set-Location "C:\dev\Whisly\whisly"
$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

Paste the full command output. It must end with `BUILD SUCCESSFUL`.

## 7. Open the new project

Open `C:\dev\Whisly\whisly` in Android Studio and confirm it loads from the new path. Only after steps 4, 5, 6 and 7 pass, rename the old OneDrive folder so it stops syncing:

```powershell
Rename-Item -LiteralPath "C:\Users\danse\OneDrive\document\Whisly" -NewName "Whisly_onedrive_old"
```

Do not delete `Whisly_onedrive_old` or the ZIP backup until the user explicitly confirms the new location is correct. No push to any remote is performed.
