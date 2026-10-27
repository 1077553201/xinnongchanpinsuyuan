[CmdletBinding()]
param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$releaseRoot = $PSScriptRoot
$configurationPath = Join-Path $releaseRoot '.env'
$jarPath = Join-Path $releaseRoot 'backend/agri-trace-backend-1.0.0.jar'
if (-not (Test-Path -LiteralPath $configurationPath)) { throw 'Create .env from .env.example and fill local configuration first.' }
foreach ($line in Get-Content -LiteralPath $configurationPath -Encoding UTF8) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $settingName = $Matches[1]
        $settingValue = $Matches[2].Trim()
        if ($settingValue.Length -ge 2 -and (($settingValue.StartsWith('"') -and $settingValue.EndsWith('"')) -or ($settingValue.StartsWith("'") -and $settingValue.EndsWith("'")))) {
            $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($settingName, $settingValue, 'Process')
    }
}
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Release JAR was not found.' }
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaPath = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { Join-Path $env:JAVA_HOME 'bin/java.exe' } elseif ($javaCommand) { $javaCommand.Source } else { throw 'Java 11 was not found. Configure JAVA_HOME.' }
if ($CheckOnly) { Write-Output 'Release configuration, JAR and Java found. No service started; credentials not displayed.'; return }
Push-Location $releaseRoot
try {
    & $javaPath -jar $jarPath
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
