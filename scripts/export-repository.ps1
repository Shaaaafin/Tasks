param(
    [string]$OutputDirectory = 'dist/github-ready',
    [string]$ZipPath = 'dist/Tasks-github-ready.zip'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$distributionRoot = Join-Path $repositoryRoot 'dist'

function Resolve-ExportPath([string]$Value) {
    $candidate = if ([System.IO.Path]::IsPathRooted($Value)) { $Value } else { Join-Path $repositoryRoot $Value }
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if (!$resolved.StartsWith($distributionRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Repository exports must be written to a new path inside dist.'
    }
    return $resolved
}

$exportRoot = Resolve-ExportPath $OutputDirectory
$archivePath = Resolve-ExportPath $ZipPath
if ($archivePath.StartsWith($exportRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'The archive must be outside the exported source directory.'
}
if ((Test-Path -LiteralPath $exportRoot) -or (Test-Path -LiteralPath $archivePath)) {
    throw 'The export directory or archive already exists. Choose new output paths.'
}

$folders = @('.github', 'app/src', 'gradle', 'tools', 'scripts', 'docs', 'fastlane', 'packaging', 'build-support')
$rootFiles = @('.gitignore', '.gitattributes', '.editorconfig', 'LICENSE', 'NOTICE.md', 'README.md', 'CONTRIBUTING.md', 'SECURITY.md', 'CHANGELOG.md', 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts', 'app/proguard-rules.pro')
$privatePattern = '(^|/)(\.git|\.gradle|\.kotlin|\.tools|build)/|\.(jks|keystore|p12|pem|apk|aab|log)$|(^|/)(local\.properties|local-signing\.properties|\.env(?:\..*)?)$'
$referenceImagePattern = '^docs/reference/.*\.(png|jpe?g|webp|gif)$'
$files = @($folders | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $repositoryRoot $_) -File -Force -Recurse }) + @($rootFiles | ForEach-Object { Get-Item -LiteralPath (Join-Path $repositoryRoot $_) -Force })

foreach ($file in $files) {
    $relative = $file.FullName.Substring($repositoryRoot.Length + 1).Replace('\', '/')
    if ($relative -match '(^|/)__pycache__/|\.pyc$') { continue }
    if ($relative -match $referenceImagePattern) { continue }
    if ($relative -match $privatePattern) { throw "Private or generated file rejected: $relative" }
    $destination = Join-Path $exportRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $destination
}

New-Item -ItemType Directory -Force -Path (Split-Path $archivePath -Parent) | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($archivePath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $exportRoot -File -Force -Recurse) {
        $relative = $file.FullName.Substring($exportRoot.Length + 1).Replace('\', '/')
        $entry = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
        if ($relative -eq 'gradlew') { $entry.ExternalAttributes = [Convert]::ToInt32('81ED0000', 16) }
    }
} finally { $archive.Dispose() }

Write-Output "Source directory: $exportRoot"
Write-Output "Source archive: $archivePath"
