param([switch]$Headless, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$backendDirectory = Split-Path -Parent $PSScriptRoot
$workspaceDirectory = Split-Path -Parent $backendDirectory
$monitorDirectory = Join-Path $workspaceDirectory 'monitoramento-vagas'
$frontendDirectory = Join-Path $workspaceDirectory 'view-parking-fag'
$runtimeDirectory = Join-Path $backendDirectory 'runtime/stack'
New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
foreach ($port in @(8080,4173)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Porta $port em uso. Encerre a execução anterior antes de iniciar." }
}
if (-not (Test-Path -LiteralPath (Join-Path $monitorDirectory '.venv/Scripts/python.exe'))) { throw 'Prepare o ambiente Python .venv conforme o README.' }
if (Test-Path -LiteralPath (Join-Path $backendDirectory '.env')) {
    foreach ($line in Get-Content -LiteralPath (Join-Path $backendDirectory '.env')) {
        if ($line -match '^\s*([^#=\s]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim().Trim('"').Trim("'"), 'Process')
        }
    }
}
# Every child inherits the same secret; it is never written to logs or the frontend build.
if ([string]::IsNullOrWhiteSpace($env:INGEST_API_KEY)) {
    $keyBytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($keyBytes) } finally { $generator.Dispose() }
    $env:INGEST_API_KEY = [Convert]::ToBase64String($keyBytes)
}
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
$env:JAVA_API_URL = 'http://127.0.0.1:8080'
$env:JAVA_PROXY_URL = 'http://127.0.0.1:8080'
$env:PUBLIC_BIND = '127.0.0.1'
$env:PUBLIC_PORT = '4173'
$env:SYNC_SECONDS = '15'
$env:RETRY_MAX_SECONDS = '30'
$env:HTTP_TIMEOUT_SECONDS = '3'
if (-not $SkipBuild) {
    Push-Location $backendDirectory
    try { & .\mvnw.cmd -q package -DskipTests; if ($LASTEXITCODE -ne 0) { throw 'Falha no build Java' } } finally { Pop-Location }
    Push-Location $frontendDirectory
    try {
        & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Falha no npm ci' }
        & npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'Falha no build React' }
    } finally { Pop-Location }
}
$tracked = @()
function Start-Tracked($Executable, $Arguments, $Directory, $Name) {
    $process = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtimeDirectory "$Name.log") -RedirectStandardError (Join-Path $runtimeDirectory "$Name-error.log")
    return $process
}
try {
    $tracked += Start-Tracked 'java' @('-jar','target/orchestrador-parking-fag-1.0.0.jar') $backendDirectory 'java'
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($tracked[0].HasExited) { throw 'Java encerrou. Confira runtime/stack/java-error.log.' }
        try { $health = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2; if ($health.status -eq 'UP') { $ready = $true; break } } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Java/MySQL não ficou disponível em tempo hábil.' }
    $monitorMode = if ($Headless) { '--headless' } else { '--preview' }
    $tracked += Start-Tracked (Join-Path $monitorDirectory '.venv/Scripts/python.exe') @('-m','parking_monitor.main',$monitorMode) $monitorDirectory 'python'
    $tracked += Start-Tracked 'node' @('scripts/serve.mjs') $frontendDirectory 'frontend'
    $tracked | ForEach-Object { @{id=$_.Id;name=$_.ProcessName;started=$_.StartTime.ToUniversalTime().ToString('o')} } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeDirectory 'processes.json')
    Write-Host 'Totem: http://127.0.0.1:4173/totem'
    Write-Host 'Celular: use o endereço HTTPS do túnel com /mobile.'
    Write-Host 'Em outro terminal: cloudflared tunnel --url http://127.0.0.1:4173'
    Write-Host 'Ctrl+C encerra os três serviços. Encerre também o túnel no outro terminal.'
    while ($true) {
        foreach ($process in $tracked) { if ($process.HasExited) { throw "Serviço encerrou: PID $($process.Id). Confira runtime/stack/." } }
        Start-Sleep -Seconds 2
    }
} finally {
    foreach ($process in $tracked) { if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
    Remove-Item -LiteralPath (Join-Path $runtimeDirectory 'processes.json') -ErrorAction SilentlyContinue
}
