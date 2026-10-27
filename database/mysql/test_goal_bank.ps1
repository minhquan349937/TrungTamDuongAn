param([switch]$DevelopmentEmptyDatabase)
& (Join-Path $PSScriptRoot 'test_goal_bank_v8.ps1') -DevelopmentEmptyDatabase:$DevelopmentEmptyDatabase
