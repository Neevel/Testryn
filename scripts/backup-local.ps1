[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) ("backups\testryn-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")))
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$databaseArchive = Join-Path $resolvedOutputDirectory "database.dump"
$reportsArchive = Join-Path $resolvedOutputDirectory "reports.tar.gz"
$metadataFile = Join-Path $resolvedOutputDirectory "backup.json"

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
}

Push-Location $repositoryRoot
try {
    New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force | Out-Null

    Write-Host "Starting the database and backend containers if necessary..."
    Invoke-Docker @("compose", "up", "-d", "db", "backend")

    $databaseContainer = (& docker compose ps -q db).Trim()
    $backendContainer = (& docker compose ps -q backend).Trim()
    if ([string]::IsNullOrWhiteSpace($databaseContainer) -or [string]::IsNullOrWhiteSpace($backendContainer)) {
        throw "Could not resolve the Testryn database or backend container."
    }

    Write-Host "Creating PostgreSQL backup..."
    Invoke-Docker @("compose", "exec", "-T", "db", "pg_dump", "--format=custom", "--no-owner", "--no-privileges", "--username=testryn", "--file=/tmp/testryn-database.dump", "testryn")
    Invoke-Docker @("cp", "${databaseContainer}:/tmp/testryn-database.dump", $databaseArchive)
    Invoke-Docker @("compose", "exec", "-T", "db", "rm", "-f", "/tmp/testryn-database.dump")

    Write-Host "Creating report-file backup..."
    Invoke-Docker @("compose", "exec", "-T", "backend", "tar", "-czf", "/tmp/testryn-reports.tar.gz", "-C", "/data/reports", ".")
    Invoke-Docker @("cp", "${backendContainer}:/tmp/testryn-reports.tar.gz", $reportsArchive)
    Invoke-Docker @("compose", "exec", "-T", "backend", "rm", "-f", "/tmp/testryn-reports.tar.gz")

    $metadata = [ordered]@{
        formatVersion = 1
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        databaseFile = [System.IO.Path]::GetFileName($databaseArchive)
        databaseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $databaseArchive).Hash
        reportsFile = [System.IO.Path]::GetFileName($reportsArchive)
        reportsSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $reportsArchive).Hash
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataFile -Encoding UTF8

    Write-Host "Backup completed: $resolvedOutputDirectory"
    Write-Warning "The backup contains private Testryn data. Transfer it securely and never commit it to Git."
}
finally {
    Pop-Location
}
