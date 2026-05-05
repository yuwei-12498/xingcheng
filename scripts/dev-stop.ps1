$ErrorActionPreference = 'Stop'

function Write-Info {
  param([string]$Message)
  Write-Host "[dev-stop] $Message"
}

function Get-CityTripBackendProcesses {
  param([string]$RepoRoot)

  $backendDir = Join-Path $RepoRoot 'backend'
  $escapedBackendDir = [Regex]::Escape($backendDir)

  Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
      $_.Name -in @('java.exe', 'cmd.exe') -and
      $_.CommandLine -and (
        $_.CommandLine -match 'com\.citytrip\.CityTripApplication' -or
        $_.CommandLine -match 'spring-boot:run' -or
        $_.CommandLine -match $escapedBackendDir
      )
    } |
    Sort-Object ProcessId -Unique
}

function Stop-IfRunning {
  param([Nullable[int]]$ProcessId)

  if ($null -eq $ProcessId) {
    return
  }

  Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue

  $deadline = (Get-Date).AddSeconds(10)
  while ((Get-Date) -lt $deadline) {
    $stillRunning = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $stillRunning) {
      return
    }
    Start-Sleep -Milliseconds 300
  }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $repoRoot 'artifacts\dev-runtime\processes.json'

if (-not (Test-Path -LiteralPath $processFile)) {
  $staleProcesses = @(Get-CityTripBackendProcesses -RepoRoot $repoRoot)
  if ($staleProcesses.Count -eq 0) {
    Write-Info "No runtime file found at $processFile"
    exit 0
  }

  foreach ($process in $staleProcesses) {
    Stop-IfRunning -ProcessId $process.ProcessId
  }

  Write-Info "No runtime file found, but stopped stale CityTrip backend processes: $($staleProcesses.ProcessId -join ', ')"
  exit 0
}

$state = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json

Stop-IfRunning -ProcessId $state.frontendPid
Stop-IfRunning -ProcessId $state.backendPid
Stop-IfRunning -ProcessId $state.frontendShellPid
Stop-IfRunning -ProcessId $state.backendShellPid

$staleProcesses = @(Get-CityTripBackendProcesses -RepoRoot $repoRoot)
foreach ($process in $staleProcesses) {
  Stop-IfRunning -ProcessId $process.ProcessId
}

Remove-Item -LiteralPath $processFile -Force -ErrorAction SilentlyContinue

Write-Info "Stopped recorded dev processes, cleaned stale backend processes, and removed runtime file."
