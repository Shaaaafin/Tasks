$ErrorActionPreference = 'Stop'
$taskRef = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs\reference'
$taskSources = Get-Content -LiteralPath (Join-Path $taskRef 'sources.json') -Raw | ConvertFrom-Json
foreach ($taskSource in $taskSources) {
    $taskDestination = Join-Path $taskRef $taskSource.file
    if (!(Test-Path -LiteralPath $taskDestination)) {
        try {
            Invoke-WebRequest -Uri $taskSource.url -OutFile $taskDestination -TimeoutSec 45
            Write-Output "Saved $($taskSource.file)"
        } catch { Write-Output "FAILED $($taskSource.file): $($_.Exception.Message)" }
    }
}
