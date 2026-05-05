$ErrorActionPreference = 'Stop'

function Write-Info {
  param([string]$Message)
  Write-Host "[dev-start] $Message"
}

function Read-EnvFile {
  param([string]$Path)

  $result = @{}
  if (-not (Test-Path -LiteralPath $Path)) {
    return $result
  }

  Get-Content -LiteralPath $Path | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      $name = $matches[1]
      $value = $matches[2].Trim()
      $result[$name] = $value
    }
  }

  return $result
}

function Apply-ProcessEnvironment {
  param([hashtable]$Variables)

  if ($null -eq $Variables) {
    return
  }

  foreach ($entry in $Variables.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace($entry.Key)) {
      continue
    }
    Set-Item -Path ("Env:{0}" -f $entry.Key) -Value ([string]$entry.Value)
  }
}

function Get-ListeningProcessId {
  param([int]$Port)

  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1

  if ($null -eq $connection) {
    return $null
  }

  return [int]$connection.OwningProcess
}

function Wait-ForListeningPort {
  param(
    [int]$Port,
    [int]$TimeoutSeconds = 90
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $listeningProcessId = Get-ListeningProcessId -Port $Port
    if ($null -ne $listeningProcessId) {
      return $listeningProcessId
    }
    Start-Sleep -Milliseconds 500
  }

  throw "Port $Port did not enter LISTEN state within $TimeoutSeconds seconds."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$rootEnvPath = Join-Path $repoRoot '.env'
$rootEnvMap = Read-EnvFile -Path $rootEnvPath
Apply-ProcessEnvironment -Variables $rootEnvMap

$backendPort = 8082
if ($rootEnvMap.ContainsKey('SERVER_PORT')) {
  if ($rootEnvMap['SERVER_PORT'] -notmatch '^\d+$') {
    throw ".env SERVER_PORT is invalid: $($rootEnvMap['SERVER_PORT'])"
  }
  $backendPort = [int]$rootEnvMap['SERVER_PORT']
} elseif ($rootEnvMap.ContainsKey('DEV_BACKEND_PORT')) {
  if ($rootEnvMap['DEV_BACKEND_PORT'] -notmatch '^\d+$') {
    throw ".env DEV_BACKEND_PORT is invalid: $($rootEnvMap['DEV_BACKEND_PORT'])"
  }
  $backendPort = [int]$rootEnvMap['DEV_BACKEND_PORT']
}

$frontendPort = 3000
$runtimeDir = Join-Path $repoRoot 'artifacts\dev-runtime'
$backendLog = Join-Path $runtimeDir 'backend.log'
$frontendLog = Join-Path $runtimeDir 'frontend.log'
$processFile = Join-Path $runtimeDir 'processes.json'
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

if (Test-Path -LiteralPath $processFile) {
  throw "Existing runtime file found: $processFile. Run scripts/dev-stop.ps1 first."
}

$occupiedFrontendPid = Get-ListeningProcessId -Port $frontendPort
if ($null -ne $occupiedFrontendPid) {
  throw "Frontend port $frontendPort is already in use by PID $occupiedFrontendPid."
}

$occupiedBackendPid = Get-ListeningProcessId -Port $backendPort
if ($null -ne $occupiedBackendPid) {
  throw "Backend port $backendPort is already in use by PID $occupiedBackendPid."
}

Write-Info "Starting backend on http://127.0.0.1:$backendPort ..."
$backendCommand = "mvn spring-boot:run > `"$backendLog`" 2>&1"
$backendShell = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $backendCommand -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru

try {
  $backendPid = Wait-ForListeningPort -Port $backendPort -TimeoutSeconds 120
} catch {
  Stop-Process -Id $backendShell.Id -Force -ErrorAction SilentlyContinue
  throw "Backend failed to listen on port $backendPort. Check log: $backendLog"
}

Write-Info "Starting frontend on http://127.0.0.1:$frontendPort ..."
$frontendCommand = "npm run dev > `"$frontendLog`" 2>&1"
$frontendShell = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $frontendCommand -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru

try {
  $frontendPid = Wait-ForListeningPort -Port $frontendPort -TimeoutSeconds 60
} catch {
  Stop-Process -Id $frontendShell.Id -Force -ErrorAction SilentlyContinue
  Stop-Process -Id $backendPid -Force -ErrorAction SilentlyContinue
  Stop-Process -Id $backendShell.Id -Force -ErrorAction SilentlyContinue
  throw "Frontend failed to listen on port $frontendPort. Check log: $frontendLog"
}

$state = [ordered]@{
  backendPort      = $backendPort
  frontendPort     = $frontendPort
  backendPid       = $backendPid
  frontendPid      = $frontendPid
  backendShellPid  = $backendShell.Id
  frontendShellPid = $frontendShell.Id
  backendLog       = $backendLog
  frontendLog      = $frontendLog
}

$state | ConvertTo-Json | Set-Content -LiteralPath $processFile -Encoding UTF8

Write-Info "Frontend: http://127.0.0.1:$frontendPort"
Write-Info "Backend:  http://127.0.0.1:$backendPort"
Write-Info "Runtime file: $processFile"
Write-Info "Logs: backend=$backendLog frontend=$frontendLog"
