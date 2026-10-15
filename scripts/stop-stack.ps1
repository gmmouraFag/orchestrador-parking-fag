$ErrorActionPreference = 'Stop'
$registryPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'runtime/stack/processes.json'
if (-not (Test-Path -LiteralPath $registryPath)) { Write-Host 'Nenhuma execução rastreada.'; return }
foreach ($entry in @(Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    $expectedStart = ([datetime]$entry.started).ToUniversalTime()
    if ($process -and $process.ProcessName -eq $entry.name -and [Math]::Abs(($process.StartTime.ToUniversalTime() - $expectedStart).TotalMilliseconds) -lt 1) {
        Stop-Process -Id $process.Id
    }
}
Write-Host 'Serviços rastreados encerrados. Encerre também o túnel no terminal correspondente.'
