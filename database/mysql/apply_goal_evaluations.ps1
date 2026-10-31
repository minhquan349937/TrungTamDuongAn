param([switch]$Apply)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings=@{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $settings[$matches[1]]=$matches[2].Trim('"',"'") }
}
$schema=if($settings['MYSQL_DATABASE']) {$settings['MYSQL_DATABASE']} else {'duong_an_v11'}
if($schema -notmatch '^[A-Za-z0-9_]+$') { throw 'Invalid schema' }
$previousOutputEncoding=$OutputEncoding
$previousConsoleEncoding=[Console]::OutputEncoding
Push-Location $projectRoot
try {
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
    function Invoke-Db([string]$sql) {
        $sql | docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=$schema --default-character-set=utf8mb4 --batch --skip-column-names
        if($LASTEXITCODE -ne 0) { throw 'V11 operation failed. Inspect the schema before retrying; MySQL DDL is not transactional.' }
    }
    $hasNew=Invoke-Db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='goal_evaluations';"
    $old=Invoke-Db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('student_goal_bank','goal_criteria','progress_records');"
    if($hasNew -eq '1' -and $old -eq '0') { Write-Output 'V11 already applied; no changes.'; return }
    if($hasNew -ne '0' -or $old -ne '3') { throw 'Unexpected/partial schema. Inspect before applying V11.' }
    Invoke-Db "SELECT 'student',COUNT(*) FROM student UNION ALL SELECT 'learning_plans',COUNT(*) FROM learning_plans UNION ALL SELECT 'plan_goals',COUNT(*) FROM plan_goals UNION ALL SELECT 'student_goal_bank',COUNT(*) FROM student_goal_bank UNION ALL SELECT 'goal_criteria',COUNT(*) FROM goal_criteria UNION ALL SELECT 'progress_records',COUNT(*) FROM progress_records;"
    Invoke-Db "SELECT status,COUNT(*) FROM plan_goals GROUP BY status;"
    if(-not $Apply) { Write-Output 'Preflight complete. Stop application writers, then use -Apply to back up and migrate.'; return }
    if((Invoke-Db 'SELECT COUNT(*) FROM information_schema.innodb_trx;') -ne '0') { throw 'Close active transactions before migration.' }
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_goal_evaluations_v11
    Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V11__plan_goal_evaluations.sql') -Raw -Encoding UTF8)
    Invoke-Db "SELECT 'student',COUNT(*) FROM student UNION ALL SELECT 'learning_plans',COUNT(*) FROM learning_plans UNION ALL SELECT 'plan_goals',COUNT(*) FROM plan_goals UNION ALL SELECT 'goal_evaluations',COUNT(*) FROM goal_evaluations;"
    Write-Output 'V11 applied. Catalog and plan identities preserved. Review migration_v11_archive for legacy metadata.'
} finally {
    $OutputEncoding=$previousOutputEncoding
    [Console]::OutputEncoding=$previousConsoleEncoding
    Pop-Location
}
