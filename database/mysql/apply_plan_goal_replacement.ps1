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
        if($LASTEXITCODE -ne 0) { throw 'V13 operation failed; inspect trigger before retrying' }
        return $result
    }
    if((Invoke-Db "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plan_evaluations';") -ne '1') { throw 'Apply V12 first' }
    $done=Invoke-Db "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME='plan_goals_snapshot_insert' AND ACTION_STATEMENT LIKE '%Replacement must follow%';"
    if($done -eq '1') { Write-Output 'V13 already applied; no changes.'; return }
    $old=Invoke-Db "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME='plan_goals_snapshot_insert' AND ACTION_STATEMENT LIKE '%Use a new evaluation attempt%';"
    if($old -ne '1') { throw 'Unexpected plan_goals_snapshot_insert state' }
    Invoke-Db "SELECT 'plan_goals',COUNT(*) FROM plan_goals UNION ALL SELECT 'goal_evaluations',COUNT(*) FROM goal_evaluations;"
    if(-not $Apply) { Write-Output 'V13 preflight complete. Use -Apply to back up and migrate.'; return }
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_plan_goal_replacement_v13
    Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V13__replace_plan_goal.sql') -Raw -Encoding UTF8)
    if((Invoke-Db "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME='plan_goals_snapshot_insert' AND ACTION_STATEMENT LIKE '%Replacement must follow%';") -ne '1') { throw 'V13 verification failed' }
    Write-Output 'V13 applied.'
} finally { Pop-Location }
