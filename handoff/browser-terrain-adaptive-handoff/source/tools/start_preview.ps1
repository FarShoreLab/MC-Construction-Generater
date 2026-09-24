$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $workspace
$python = (Get-Command python).Source
$javac = (Get-Command javac).Source
$classes = Join-Path $workspace 'core-planner/build/classes/java/main'
$gsonCandidates = Get-ChildItem -Path 'C:/Users/Construct/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1' -Recurse -Filter 'gson-2.10.1.jar' -File -ErrorAction SilentlyContinue
if (-not $gsonCandidates) { throw 'Gson 2.10.1 was not found in the local Gradle cache.' }
$gson = $gsonCandidates[0].FullName
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem -Path (Join-Path $workspace 'core-planner/src/main/java') -Recurse -Filter '*.java' -File | Select-Object -ExpandProperty FullName
& $javac '-cp' $gson '-d' $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
& $python (Join-Path $workspace 'tools/preview_server.py') @args
