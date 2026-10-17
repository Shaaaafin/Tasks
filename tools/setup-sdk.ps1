$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskTools = Join-Path $taskRoot '.tools'
$taskSdk = Join-Path $taskTools 'sdk'
New-Item -ItemType Directory -Force -Path $taskTools,$taskSdk | Out-Null
$taskZip = Join-Path $taskTools 'platform-37.0_r02.zip'
if (!(Test-Path -LiteralPath $taskZip)) {
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-37.0_r02.zip' -OutFile $taskZip
}
if ((Get-FileHash -LiteralPath $taskZip -Algorithm SHA1).Hash -ne 'ED8EBF7F8822A4DE5686D427F237D2FA30FF7410') { throw 'SDK checksum mismatch' }
$taskPlatform = Join-Path $taskSdk 'platforms\android-37.0'
if (!(Test-Path -LiteralPath $taskPlatform)) {
    $taskExpanded = Join-Path $taskTools 'platform-expanded'
    Expand-Archive -LiteralPath $taskZip -DestinationPath $taskExpanded -Force
    $taskSource = Get-ChildItem -LiteralPath $taskExpanded -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force -Path (Split-Path $taskPlatform -Parent) | Out-Null
    Copy-Item -LiteralPath $taskSource.FullName -Destination $taskPlatform -Recurse
}
$taskExisting = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
foreach ($taskPart in @('build-tools\36.0.0','licenses')) {
    $taskDest = Join-Path $taskSdk $taskPart
    if (!(Test-Path -LiteralPath $taskDest)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $taskDest -Parent) | Out-Null
        Copy-Item -LiteralPath (Join-Path $taskExisting $taskPart) -Destination $taskDest -Recurse
    }
}
('sdk.dir=' + $taskSdk.Replace('\','/').Replace(':','\:')) | Set-Content -LiteralPath (Join-Path $taskRoot 'local.properties') -NoNewline
Write-Output "Android SDK ready: $taskSdk"
