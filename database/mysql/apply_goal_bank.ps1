param([switch]$ApplyAfterConfirmation, [switch]$WritersStopped)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings = @{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $settings[$matches[1]]=$matches[2].Trim('"', "'") }
}
if (-not $settings['MYSQL_ROOT_PASSWORD']) { throw 'MYSQL_ROOT_PASSWORD missing' }
$schema = if ($settings['MYSQL_DATABASE']) { $settings['MYSQL_DATABASE'] } else { 'duong_an_v11' }
if ($schema -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid schema name' }
Push-Location $projectRoot
try {
    Get-Content (Join-Path $PSScriptRoot 'preflight_goal_bank.sql') -Raw -Encoding UTF8 |
        docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql `
            --user=root --database=$schema --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw 'Preflight failed' }
    if (-not $ApplyAfterConfirmation) {
        Write-Output 'Read-only report complete. No schema or data changed.'
        return
    }
    if (-not $WritersStopped) { throw 'Stop backend/import writers before applying, then explicitly pass -WritersStopped' }
    $legacyCount = docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql `
        --user=root --database=$schema --batch --skip-column-names `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='skill_levels'"
    if ($LASTEXITCODE -ne 0) { throw 'Schema detection failed' }
    $idLength = docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql `
        --user=root --database=$schema --batch --skip-column-names `
        --execute="SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='goals' AND column_name='id'"
    if ($LASTEXITCODE -ne 0) { throw 'ID type detection failed' }
    if ([int]$idLength -eq 8) { Write-Output 'Short IDs already present; no migration executed.'; return }
    if ([int]$idLength -ne 36) { throw 'Unsupported schema; inspect manually' }
    & (Join-Path $PSScriptRoot 'backup_database.ps1')
    $migrations = if ([int]$legacyCount -eq 1) {
        @('V5__goal_bank_hierarchy.sql','V6__catalog_short_ids.sql')
    } else { @('V6__catalog_short_ids.sql') }
    foreach ($migration in $migrations) {
        Get-Content (Join-Path $PSScriptRoot "migrations/$migration") -Raw -Encoding UTF8 |
            docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql `
                --user=root --database=$schema --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw "Migration stopped at $migration. Keep writers stopped and inspect/restore the full backup; do not rerun blindly." }
        Write-Output "Applied: $migration"
    }
    Write-Output 'Migration complete. Verify catalog and historical rows before restarting writers.'
} finally { Pop-Location }
