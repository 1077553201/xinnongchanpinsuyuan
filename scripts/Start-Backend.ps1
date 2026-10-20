[CmdletBinding()]
param(
    [string]$MavenPath,
    [switch]$EnableMqtt,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$configurationPath = Join-Path $repositoryRoot '.env'
if (-not (Test-Path -LiteralPath $configurationPath)) {
    throw 'Create .env from .env.example and fill local configuration first.'
}

foreach ($configurationLine in Get-Content -LiteralPath $configurationPath) {
    if ($configurationLine -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $configurationKey = $Matches[1]
        $configurationValue = $Matches[2].Trim()
        if ($configurationValue.Length -ge 2 -and (
            ($configurationValue.StartsWith('"') -and $configurationValue.EndsWith('"')) -or
            ($configurationValue.StartsWith("'") -and $configurationValue.EndsWith("'"))
        )) {
            $configurationValue = $configurationValue.Substring(1, $configurationValue.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($configurationKey, $configurationValue, 'Process')
    }
}

if (-not $MavenPath) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) { $MavenPath = $mavenCommand.Source }
    else {
        $mavenCache = Join-Path $env:USERPROFILE '.m2/wrapper/dists'
        if (Test-Path -LiteralPath $mavenCache) {
            $distribution = Get-ChildItem -LiteralPath $mavenCache -Filter mvn.cmd -Recurse -File |
                Sort-Object FullName -Descending | Select-Object -First 1
            if ($distribution) { $MavenPath = $distribution.FullName }
        }
    }
}
if (-not $MavenPath -or -not (Test-Path -LiteralPath $MavenPath)) {
    throw 'Maven not found. Install Maven or provide -MavenPath with the full path to mvn.cmd.'
}
if ($CheckOnly) {
    Write-Output 'Local environment loaded; Maven located. No service started and no credentials displayed.'
    return
}

$runArguments = @('spring-boot:run')
if ($EnableMqtt) { $runArguments += '-Dspring-boot.run.arguments=--trace.mqtt.enabled=true' }
Push-Location (Join-Path $repositoryRoot 'backend')
try {
    & $MavenPath @runArguments
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
