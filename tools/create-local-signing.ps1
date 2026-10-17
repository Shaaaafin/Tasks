$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskTools = Join-Path $taskRoot '.tools'
$taskProperties = Join-Path $taskTools 'local-signing.properties'
$taskKeyStore = Join-Path $taskTools 'tasks-release.keystore'
if ((Test-Path -LiteralPath $taskProperties) -or (Test-Path -LiteralPath $taskKeyStore)) { throw 'Local signing files already exist; preserve them for APK updates.' }
$taskJava = Get-ChildItem -LiteralPath (Join-Path $taskTools 'java') -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\keytool.exe') } | Select-Object -First 1
if (!$taskJava) { throw 'A full project-local JDK is required.' }
$env:TASKS_SIGNING_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
try {
    & (Join-Path $taskJava.FullName 'bin\keytool.exe') -genkeypair -keystore $taskKeyStore -alias tasks -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Tasks Local Build' -storepass:env TASKS_SIGNING_PASSWORD -keypass:env TASKS_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed.' }
    @('storeFile=.tools/tasks-release.keystore', 'keyAlias=tasks', "storePassword=$env:TASKS_SIGNING_PASSWORD", "keyPassword=$env:TASKS_SIGNING_PASSWORD") | Set-Content -LiteralPath $taskProperties
    Write-Output 'Local signing key created. Back up .tools/tasks-release.keystore and .tools/local-signing.properties privately.'
} finally { Remove-Item Env:\TASKS_SIGNING_PASSWORD }
