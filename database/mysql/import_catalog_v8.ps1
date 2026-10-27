param([Parameter(Mandatory=$true)][ValidateSet('denver','vb')][string]$Program)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $projectRoot
$oldEncoding=$OutputEncoding
try {
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    $settings=@{}
    Get-Content .env | ForEach-Object { if($_ -match '^([^#=]+)=(.*)$') {$settings[$matches[1]]=$matches[2].Trim('"',"'")} }
    $mysql=docker compose ps --format json | ConvertFrom-Json | Where-Object Service -eq 'mysql'
    if($mysql.Name -ne 'duong-an-mysql-1' -or -not($mysql.Publishers | Where-Object {$_.PublishedPort -eq 3308 -and $_.URL -eq '127.0.0.1'})) {throw 'Wrong development MySQL container/port'}
    function Invoke-Db([string]$sql) {
        $sql | docker compose exec -T -e "MYSQL_PWD=$($settings['MYSQL_ROOT_PASSWORD'])" mysql mysql -uroot -Dduong_an_v11 --default-character-set=utf8mb4 --batch --skip-column-names
        if($LASTEXITCODE -ne 0) {throw 'MySQL import failed; the unfinished transaction was rolled back when the connection closed'}
    }
    if((Invoke-Db 'SELECT CONCAT(DATABASE(),CHAR(58),@@hostname);') -ne "duong_an_v11:$($mysql.ID)") {throw 'Wrong database identity'}
    if((Invoke-Db 'SELECT COUNT(*) FROM information_schema.innodb_trx;') -ne '0') {throw 'Close existing transactions before importing'}
    if((Invoke-Db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='program_level_domains';") -ne '1') {throw 'Apply V8 first'}
    $code=if($Program -eq 'denver') {'ESDM'} else {'VB'}
    if((Invoke-Db "SELECT COUNT(*) FROM programs WHERE code='$code';") -ne '0') {throw 'Program already exists; do not run an import twice'}
    Invoke-Db (Get-Content "database/mysql/imports/v8/${Program}_v8.sql" -Raw -Encoding UTF8)
    Write-Output "Imported $Program into development duong_an_v11 on Docker localhost:3308."
} finally {$OutputEncoding=$oldEncoding;Pop-Location}
