param([ValidatePattern('^[A-Za-z0-9_]+$')][string]$Label='before_v6')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings = @{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim('"', "'")
    }
}
if (-not $settings['MYSQL_ROOT_PASSWORD']) { throw 'MYSQL_ROOT_PASSWORD missing' }
$schema = if ($settings['MYSQL_DATABASE']) { $settings['MYSQL_DATABASE'] } else { 'duong_an_v11' }
if ($schema -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid schema name' }
$backupDirectory = Join-Path $projectRoot 'database/.backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupFile = Join-Path $backupDirectory ($schema + '_' + $Label + '_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.sql')
Push-Location $projectRoot
try {
    $dump = docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysqldump `
        --user=root --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF `
        --default-character-set=utf8mb4 $schema
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed; no backup file written' }
    [IO.File]::WriteAllText($backupFile, ($dump -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
    Write-Output "Backup: $backupFile"
    Write-Output "SHA256: $((Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash)"
} finally {
    Pop-Location
}
