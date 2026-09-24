$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $workspace
# Uses current user's Gradle cache, or explicit $env:GSON_JAR; no fixed account paths.
& python (Join-Path $PSScriptRoot 'build_offline.py')
if ($LASTEXITCODE -ne 0) { throw 'Offline compilation failed; see the error above.' }
& python (Join-Path $PSScriptRoot 'preview_server.py') @args
