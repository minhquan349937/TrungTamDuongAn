param([switch]$Apply)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings=@{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $settings[$matches[1]]=$matches[2].Trim('"',"'") }
}
$schema=if($settings['MYSQL_DATABASE']) {$settings['MYSQL_DATABASE']} else {'duong_an_v11'}
if($schema -notmatch '^[A-Za-z0-9_]+$') { throw 'Invalid schema' }
Push-Location $projectRoot
try {
    function Invoke-Db([string]$sql) {
        $result=$sql | docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=$schema --default-character-set=utf8mb4 --batch --skip-column-names
        if($LASTEXITCODE -ne 0) { throw 'Activity migration failed. Inspect schema before retrying.' }
        return $result
    }
    $count=Invoke-Db "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('suggested_activities','plan_goal_activities');"
    if($count -ne '0' -and $count -ne '2') { throw 'V20 is partially applied; review the schema manually.' }
    $linked=Invoke-Db "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suggested_activity_goals';"
    if($count -eq '0' -and $linked -eq '1') { throw 'V21 exists without V20; review the schema manually.' }
    $nullable=if($count -eq '2') { Invoke-Db "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='suggested_activities' AND COLUMN_NAME='goal_id';" } else { 'NO' }
    if($count -eq '2' -and $linked -eq '1' -and $nullable -eq 'YES') { Write-Output 'V20 through V22 already applied; no changes.'; return }
    if(-not $Apply) { Write-Output 'Activity migration preflight complete. Use -Apply to back up and migrate.'; return }
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_activity_library_v22
    if($count -eq '0') { Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V20__suggested_activities.sql') -Raw -Encoding UTF8) }
    if($linked -eq '0') { Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V21__activity_goal_links.sql') -Raw -Encoding UTF8) }
    if($nullable -eq 'NO') { Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V22__global_activity_library.sql') -Raw -Encoding UTF8) }
    Write-Output 'Activity migrations applied. Existing plan goals were not changed.'
} finally { Pop-Location }
