$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^\s*([^#=\s]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
}
& .\mvnw.cmd -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Falha no build do backend' }
& java -jar target/orchestrador-parking-fag-1.0.0.jar
