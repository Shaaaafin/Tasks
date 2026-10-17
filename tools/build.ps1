param([string[]]$BuildTasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskLocalJava = Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools\java') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\jlink.exe') } | Select-Object -First 1
if ($taskLocalJava) { $env:JAVA_HOME = $taskLocalJava.FullName }
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to a full JDK 21 or install the project-local JDK.' }
Push-Location $taskRoot
try {
    & (Join-Path $taskRoot 'gradlew.bat') @BuildTasks --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
