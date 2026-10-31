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
        if($LASTEXITCODE -ne 0) { throw 'V12 operation failed; inspect schema before retrying' }
        return $result
    }
    $columns=Invoke-Db "SELECT CONCAT(TABLE_NAME,':',COLUMN_NAME,':',COLUMN_TYPE,':',COLLATION_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='learning_plans' AND COLUMN_NAME='id') OR (TABLE_NAME='app_user' AND COLUMN_NAME='id')) ORDER BY TABLE_NAME;"
    $expected=@('app_user:id:char(36):ascii_bin','learning_plans:id:char(36):ascii_bin')
    if(@(Compare-Object $columns $expected).Count -ne 0) { throw "Unexpected FK column types: $columns" }
    $exists=Invoke-Db "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plan_evaluations';"
    if($exists -eq '1') { Write-Output 'V12 already applied; no changes.'; return }
    if($exists -ne '0') { throw 'Unexpected plan_evaluations state' }
    Invoke-Db "SELECT 'student',COUNT(*) FROM student UNION ALL SELECT 'learning_plans',COUNT(*) FROM learning_plans UNION ALL SELECT 'plan_goals',COUNT(*) FROM plan_goals UNION ALL SELECT 'goal_evaluations',COUNT(*) FROM goal_evaluations;"
    if(-not $Apply) { Write-Output 'V12 preflight complete. Use -Apply to back up and migrate.'; return }
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_plan_evaluations_v12
    $sql=Get-Content (Join-Path $PSScriptRoot 'migrations/V12__plan_evaluations.sql') -Raw -Encoding UTF8
    Invoke-Db $sql
    Invoke-Db "SHOW CREATE TABLE plan_evaluations;"
    Write-Output 'V12 applied.'
} finally { Pop-Location }
