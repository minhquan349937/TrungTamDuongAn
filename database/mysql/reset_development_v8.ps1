param([switch]$Execute)
$ErrorActionPreference='Stop'
if(-not $Execute) { throw 'Destructive development reset. Use -Execute only with explicit authorization.' }
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $projectRoot
try {
    $settings=@{}
    Get-Content .env | ForEach-Object { if($_ -match '^([^#=]+)=(.*)$') { $settings[$matches[1]]=$matches[2].Trim('"',"'") } }
    $service=docker compose ps --format json | ConvertFrom-Json
    $mysql=$service | Where-Object Service -eq 'mysql'
    if(-not $mysql -or $mysql.Name -ne 'duong-an-mysql-1' -or $mysql.Health -ne 'healthy' -or
       -not ($mysql.Publishers | Where-Object { $_.PublishedPort -eq 3308 -and $_.URL -eq '127.0.0.1' })) {
        throw 'Not the verified development MySQL on localhost:3308'
    }
    function Invoke-Db([string]$sql) {
        $sql | docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql --user=root --database=duong_an_v11 --default-character-set=utf8mb4 --batch --skip-column-names
        if($LASTEXITCODE -ne 0) { throw 'MySQL operation failed; stop and inspect the schema before retrying.' }
    }
    $oldEncoding=$OutputEncoding
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    try {
        $identity=Invoke-Db 'SELECT CONCAT(DATABASE(),CHAR(58),@@hostname);'
        if($identity -ne "duong_an_v11:$($mysql.ID)") { throw 'Database/container identity mismatch' }
        $oldColumns=Invoke-Db "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='domains' AND column_name='program_level_id') OR (table_name='goals' AND column_name='domain_id'));"
        if($oldColumns -ne '2') { throw 'V8 reset/migration is a one-time upgrade from V7; schema already changed. Nothing was deleted.' }
        $transactions=@(Invoke-Db 'SELECT trx_mysql_thread_id FROM information_schema.innodb_trx;')
        if($transactions.Count) { throw 'Open transactions detected. Close the development writers before resetting.' }
        $accountsBefore=Invoke-Db 'SELECT SHA2(GROUP_CONCAT(CONCAT(id,email,password_hash,role) ORDER BY id),256) FROM app_user;'
        $tables=@(Invoke-Db "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name NOT IN ('app_user','center','catalog_id_sequences','migration_v6_catalog_ids') ORDER BY table_name;")
        if($tables.Count -lt 25) { throw 'Unexpected schema; refuse reset' }
        $deletes=($tables | ForEach-Object { if($_ -notmatch '^[a-z_]+$') {throw 'Unexpected table name'}; "DELETE FROM ``$_``;" }) -join "`n"
        Invoke-Db "SET SESSION lock_wait_timeout=5; SET SESSION innodb_lock_wait_timeout=5; SET FOREIGN_KEY_CHECKS=0; START TRANSACTION; $deletes UPDATE catalog_id_sequences SET next_value=1; COMMIT; SET FOREIGN_KEY_CHECKS=1;"
        $accountsAfter=Invoke-Db 'SELECT SHA2(GROUP_CONCAT(CONCAT(id,email,password_hash,role) ORDER BY id),256) FROM app_user;'
        if($accountsBefore -ne $accountsAfter) { throw 'Account integrity check failed' }
        Invoke-Db (Get-Content database/mysql/migrations/V8__shared_program_domains.sql -Raw -Encoding UTF8)
        Invoke-Db 'SELECT entity_name,next_value FROM catalog_id_sequences ORDER BY entity_name; SELECT role,COUNT(*) FROM app_user GROUP BY role; SELECT COUNT(*) FROM programs; SELECT COUNT(*) FROM student;'
        Write-Output 'V8 applied. Login accounts preserved; business/catalog data empty. No database or volume was dropped.'
    } finally { $OutputEncoding=$oldEncoding }
} finally { Pop-Location }
