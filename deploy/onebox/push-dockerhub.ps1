param(
    [string]$Image = "",
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"

if (-not $Image) {
    if (Test-Path ".env") {
        $envVars = Get-Content ".env" | Where-Object { $_ -match "^[A-Za-z_][A-Za-z0-9_]*=" }
        foreach ($line in $envVars) {
            $name, $value = $line -split "=", 2
            [Environment]::SetEnvironmentVariable($name, $value)
        }
        $Image = "$($env:DOCKERHUB_IMAGE)"
        if ($env:IMAGE_TAG) {
            $Tag = $env:IMAGE_TAG
        }
    }
}

if (-not $Image) {
    throw "请先在 .env 中设置 DOCKERHUB_IMAGE，或通过 -Image 显式传入，例如 yourname/citytrip-allinone"
}

Write-Host "准备构建并推送镜像: $Image`:$Tag"
docker login
docker build -t "$Image`:$Tag" .
docker push "$Image`:$Tag"
