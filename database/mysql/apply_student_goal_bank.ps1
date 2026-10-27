param([switch]$Apply)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$settings=@{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$'){ $settings[$matches[1]]=$matches[2].Trim('"',"'") }
}
if(-not $settings['MYSQL_ROOT_PASSWORD']){ throw 'MYSQL_ROOT_PASSWORD missing' }
$schema=if($settings['MYSQL_DATABASE']){ $settings['MYSQL_DATABASE'] }else{ 'duong_an_v11' }
if($schema -notmatch '^[a-zA-Z0-9_]+$'){ throw 'Invalid schema name' }
Push-Location $projectRoot
try {
    $exists=docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=$schema `
        --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='student_goal_bank'"
    if($LASTEXITCODE -ne 0){ throw 'Cannot inspect goal bank schema' }
    if([int]$exists -eq 1){ Write-Output 'student_goal_bank exists; migration not rerun.'; return }
    $imports=docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=$schema `
        --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.innodb_trx t JOIN performance_schema.processlist p ON p.id=t.trx_mysql_thread_id WHERE p.db=DATABASE() AND t.trx_rows_modified>0"
    if($LASTEXITCODE -ne 0){ throw 'Cannot inspect open transactions' }
    if([int]$imports -gt 0){ throw 'An import/write transaction is still open. Finish or roll back it in its original Workbench tab before applying V7.' }
    if(-not $Apply){ Write-Output 'Ready for additive V7. Pass -Apply to back up and create the student goal bank.'; return }
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_v7
    $previousOutputEncoding=$OutputEncoding
    try {
        $OutputEncoding=[Text.UTF8Encoding]::new($false)
        Get-Content (Join-Path $PSScriptRoot 'migrations/V7__student_goal_bank.sql') -Raw -Encoding UTF8 |
            docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=$schema `
                --default-character-set=utf8mb4 '--init-command=SET SESSION lock_wait_timeout=5'
        if($LASTEXITCODE -ne 0){ throw 'V7 could not be applied. Inspect the error; no catalog import is modified by this migration.' }
    } finally { $OutputEncoding=$previousOutputEncoding }
    Write-Output 'Applied V7: student_goal_bank created. Existing catalog and plan data unchanged.'
} finally { Pop-Location }
