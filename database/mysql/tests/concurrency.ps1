param(
    [Parameter(Mandatory=$true)][string]$MySqlExe,
    [string]$ServerHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_]+$')][string]$Database,
    [string]$LoginPath
)
$ErrorActionPreference = 'Stop'
# Run once on the disposable database populated by constraints.sql.
# No password is accepted on the command line; use mysql_config_editor if needed.
$outputDirectory = Join-Path $PSScriptRoot ('../../.test-runtime/mysql-concurrency/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$mysqlArguments = @('--no-defaults')
if ($LoginPath) { $mysqlArguments += "--login-path=$LoginPath" }
$mysqlArguments += @('--protocol=TCP', "--host=$ServerHost", "--port=$Port", "--user=$User", '--batch', '--skip-column-names', '--default-character-set=utf8mb4', $Database)
function Quote-Argument([string]$Value) {
    if ($Value.Contains('"')) { throw 'Quotes are not allowed in connection arguments.' }
    return '"' + $Value + '"'
}
$argumentLine = ($mysqlArguments | ForEach-Object { Quote-Argument $_ }) -join ' '
function Start-Sql([string]$Name, [string]$Sql) {
    $sqlPath = Join-Path $outputDirectory "$Name.sql"
    [System.IO.File]::WriteAllText($sqlPath, $Sql, (New-Object System.Text.UTF8Encoding($false)))
    $started = Start-Process -FilePath $MySqlExe -ArgumentList $argumentLine -RedirectStandardInput $sqlPath `
        -RedirectStandardOutput (Join-Path $outputDirectory "$Name.out") `
        -RedirectStandardError (Join-Path $outputDirectory "$Name.err") -WindowStyle Hidden -PassThru
    # Retain the native handle so Windows PowerShell can read ExitCode after exit.
    $null = $started.Handle
    return $started
}
function Wait-Sql($Process) {
    if (-not $Process.WaitForExit(15000)) { Stop-Process -Id $Process.Id; throw 'SQL client timeout.' }
    $Process.Refresh()
    return $Process.ExitCode
}

# Hold the common scheduling mutex while both competing clients are launched.
$blocker = Start-Sql 'blocker' 'START TRANSACTION; SELECT id FROM center WHERE id=1 FOR UPDATE; DO SLEEP(3); COMMIT;'
Start-Sleep -Milliseconds 400
$first = Start-Sql 'first' "CALL save_class_session('race-a','class1','staff1','2026-11-01 01:00:00','2026-11-01 02:00:00','SCHEDULED',NULL,NULL,'admin');"
$second = Start-Sql 'second' "CALL save_class_session('race-b','class2','staff1','2026-11-01 01:30:00','2026-11-01 02:30:00','SCHEDULED',NULL,NULL,'admin');"
$blockerCode = Wait-Sql $blocker
$firstCode = Wait-Sql $first
$secondCode = Wait-Sql $second
if ($blockerCode -ne 0 -or (@($firstCode,$secondCode) | Where-Object { $_ -eq 0 }).Count -ne 1) {
    throw "Expected exactly one successful competing schedule write (blocker=$blockerCode, first=$firstCode, second=$secondCode). See $outputDirectory"
}
$loserName = if ($firstCode -eq 0) { 'second' } else { 'first' }
$failure = Get-Content (Join-Path $outputDirectory "$loserName.err") -Raw
if ($failure -notmatch 'ERROR 1644.*Class or teacher has an overlapping session') {
    throw "Unexpected conflict error: $failure"
}
$verify = Start-Sql 'verify' "SELECT COUNT(*) FROM class_session WHERE id IN ('race-a','race-b'); SELECT COUNT(*) FROM audit_event WHERE entity_id IN ('race-a','race-b');"
if ((Wait-Sql $verify) -ne 0) { throw 'Verification query failed.' }
$counts = @(Get-Content (Join-Path $outputDirectory 'verify.out'))
if ($counts.Count -ne 2 -or $counts[0] -ne '1' -or $counts[1] -ne '1') {
    throw 'Expected exactly one session and one audit event.'
}
Write-Output 'PASS: concurrent overlapping writes produce one session, one audit event, and one overlap rejection.'
Write-Output "Logs: $outputDirectory"
