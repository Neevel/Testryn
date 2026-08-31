[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupDirectory,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedBackupDirectory = (Resolve-Path -LiteralPath $BackupDirectory).Path
$databaseArchive = Join-Path $resolvedBackupDirectory "database.dump"
$reportsArchive = Join-Path $resolvedBackupDirectory "reports.tar.gz"
$metadataFile = Join-Path $resolvedBackupDirectory "backup.json"

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
}

foreach ($requiredFile in @($databaseArchive, $reportsArchive, $metadataFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required backup file is missing: $requiredFile"
    }
}

$metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
if ($metadata.formatVersion -ne 1) {
    throw "Unsupported backup format version: $($metadata.formatVersion)"
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $databaseArchive).Hash -ne $metadata.databaseSha256) {
    throw "Database backup checksum does not match backup.json."
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $reportsArchive).Hash -ne $metadata.reportsSha256) {
    throw "Report backup checksum does not match backup.json."
}
if (-not $Force) {
    throw "Restore replaces the local Testryn database and report files. Re-run with -Force after verifying the backup path: $resolvedBackupDirectory"
}

Push-Location $repositoryRoot
try {
    Write-Host "Stopping Testryn application containers..."
    & docker compose stop backend frontend
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stop the Testryn application containers."
    }

    Write-Host "Starting PostgreSQL..."
    Invoke-Docker @("compose", "up", "-d", "db")
    $databaseReady = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        & docker compose exec -T db pg_isready --username=testryn --dbname=testryn *> $null
        if ($LASTEXITCODE -eq 0) {
            $databaseReady = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $databaseReady) {
        throw "PostgreSQL did not become ready within 60 seconds."
    }

    $databaseContainer = (& docker compose ps -q db).Trim()
    if ([string]::IsNullOrWhiteSpace($databaseContainer)) {
        throw "Could not resolve the Testryn database container."
    }

    Write-Host "Replacing the local PostgreSQL database..."
    Invoke-Docker @("cp", $databaseArchive, "${databaseContainer}:/tmp/testryn-database.dump")
    Invoke-Docker @("compose", "exec", "-T", "db", "dropdb", "--if-exists", "--force", "--username=testryn", "testryn")
    Invoke-Docker @("compose", "exec", "-T", "db", "createdb", "--username=testryn", "--owner=testryn", "testryn")
    Invoke-Docker @("compose", "exec", "-T", "db", "pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--username=testryn", "--dbname=testryn", "/tmp/testryn-database.dump")
    Invoke-Docker @("compose", "exec", "-T", "db", "rm", "-f", "/tmp/testryn-database.dump")

    Write-Host "Starting the backend and restoring report files..."
    Invoke-Docker @("compose", "up", "-d", "backend")
    $backendContainer = (& docker compose ps -q backend).Trim()
    if ([string]::IsNullOrWhiteSpace($backendContainer)) {
        throw "Could not resolve the Testryn backend container."
    }
    Invoke-Docker @("cp", $reportsArchive, "${backendContainer}:/tmp/testryn-reports.tar.gz")
    Invoke-Docker @("compose", "exec", "-T", "backend", "sh", "-c", "find /data/reports -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -xzf /tmp/testryn-reports.tar.gz -C /data/reports && rm -f /tmp/testryn-reports.tar.gz")

    Write-Host "Starting the complete Testryn stack..."
    Invoke-Docker @("compose", "up", "-d")
    Write-Host "Restore completed. Open http://localhost:3000 and verify the projects and test cases."
}
finally {
    Pop-Location
}
