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
        if($LASTEXITCODE -ne 0) { throw 'V18 operation failed. Inspect schema before retrying.' }
        return $result
    }
    $exists=Invoke-Db "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='phone';"
    if($exists -eq '1') {
        $complete=Invoke-Db "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND CONSTRAINT_NAME IN ('uq_user_phone','ck_user_login_contact','ck_user_phone');"
        if($complete -ne '3') {throw 'V18 is partially applied; review the schema manually.'}
        Write-Output 'V18 already applied; no changes.'; return
    }
    if(-not $Apply) {Write-Output 'V18 preflight complete. Use -Apply to back up and migrate.';return}
    & (Join-Path $PSScriptRoot 'backup_database.ps1') -Label before_phone_login_v18
    Invoke-Db (Get-Content (Join-Path $PSScriptRoot 'migrations/V18__phone_login.sql') -Raw -Encoding UTF8)
    Write-Output 'V18 applied. Existing passwords, roles and guardian links preserved.'
} finally {Pop-Location}
