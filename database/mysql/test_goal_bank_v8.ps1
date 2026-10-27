param([switch]$DevelopmentEmptyDatabase)
$ErrorActionPreference='Stop'
if(-not $DevelopmentEmptyDatabase) { throw 'V8 tests require the explicitly reset development database. Pass -DevelopmentEmptyDatabase; no other schema is created or dropped.' }
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$variables=@('DB_URL','DB_USERNAME','DB_PASSWORD','GOAL_CATALOG_TEST_DB_URL','GOAL_CATALOG_SMOKE_DB_URL')
$saved=@{}
foreach($name in $variables) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
$settings=@{}
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {if($_ -match '^([^#=]+)=(.*)$') {$settings[$matches[1]]=$matches[2].Trim('"',"'")}}
$oldEncoding=$OutputEncoding
Push-Location $projectRoot
try {
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    $mysql=docker compose ps --format json | ConvertFrom-Json | Where-Object Service -eq 'mysql'
    if($mysql.Name -ne 'duong-an-mysql-1' -or -not($mysql.Publishers | Where-Object {$_.PublishedPort -eq 3308 -and $_.URL -eq '127.0.0.1'})) { throw 'Wrong development container/port' }
    function Invoke-Db([string]$sql) {
        $sql | docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql -uroot -Dduong_an_v11 --default-character-set=utf8mb4 --batch --skip-column-names
        if($LASTEXITCODE -ne 0) {throw 'MySQL verification failed'}
    }
    $identity=Invoke-Db 'SELECT CONCAT(DATABASE(),CHAR(58),@@hostname);'
    if($identity -ne "duong_an_v11:$($mysql.ID)") { throw 'Wrong database identity' }
    if((Invoke-Db 'SELECT COUNT(*) FROM information_schema.innodb_trx;') -ne '0') { throw 'Close open transactions first' }
    $empty='SELECT (SELECT COUNT(*) FROM programs)+(SELECT COUNT(*) FROM student)+(SELECT COUNT(*) FROM staff)+(SELECT COUNT(*) FROM guardian)+(SELECT COUNT(*) FROM learning_plans)+(SELECT COUNT(*) FROM plan_goals)+(SELECT COUNT(*) FROM progress_records);'
    if((Invoke-Db $empty) -ne '0') {throw 'This test runner requires an empty development catalog and profiles; nothing was deleted'}
    $accounts=Invoke-Db 'SELECT SHA2(GROUP_CONCAT(CONCAT(id,email,password_hash,role) ORDER BY id),256) FROM app_user;'
    python database/mysql/tools/verify_imports_v8.py
    if($LASTEXITCODE -ne 0) {throw 'Could not prepare import verification'}
    Invoke-Db (Get-Content target/v8_verify_imports.sql -Raw -Encoding UTF8)
    $env:DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
    $env:DB_USERNAME='root';$env:DB_PASSWORD=$settings['MYSQL_ROOT_PASSWORD']
    $env:GOAL_CATALOG_TEST_DB_URL=$env:DB_URL;$env:GOAL_CATALOG_SMOKE_DB_URL=$env:DB_URL
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & .\mvnw.cmd -q '-Dlogging.level.root=WARN' '-Ddebug=false' test
        $testExit=$LASTEXITCODE
    } finally { $ErrorActionPreference=$previousPreference }
    if((Invoke-Db $empty) -ne '0') {throw 'Test fixtures remain; inspect failed tests. The runner will not delete unknown data.'}
    if((Invoke-Db 'SELECT SHA2(GROUP_CONCAT(CONCAT(id,email,password_hash,role) ORDER BY id),256) FROM app_user;') -ne $accounts) {throw 'Login account integrity check failed'}
    Invoke-Db 'UPDATE catalog_id_sequences SET next_value=1;'
    if($testExit -ne 0) {throw 'Maven tests failed'}
    Write-Output 'V8 import and backend tests passed. Business data empty, account hashes unchanged, six ID counters reset to 1.'
} finally {
    foreach($name in $variables) {[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    $OutputEncoding=$oldEncoding
    Pop-Location
}
