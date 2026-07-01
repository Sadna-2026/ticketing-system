# Usage:
#   .\scripts\run.ps1                         -> interactive menu
#   .\scripts\run.ps1 -Target cloud-split -Mode first
#   .\scripts\run.ps1 -Target local
param(
    [ValidateSet("local","cloud","cloud-split")] [string]$Target,
    [ValidateSet("first","normal","initial")]    [string]$Mode
)

# --- FILL THESE IN (or set as env vars before running) ----------------------
$DB_HOST     = if ($env:DB_HOST)     { $env:DB_HOST }     else { "CHANGE_ME_HOST" }
$DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "CHANGE_ME" }
$DB_USER     = "ticketing"
$INSTANCE    = "ticketing-db"
# ----------------------------------------------------------------------------

function Choose($title, $options) {
    Write-Host "`n$title" -ForegroundColor Cyan
    for ($i = 0; $i -lt $options.Count; $i++) { Write-Host "  [$($i+1)] $($options[$i])" }
    do { $sel = Read-Host "Choose 1-$($options.Count)" } until ($sel -match '^\d+$' -and [int]$sel -ge 1 -and [int]$sel -le $options.Count)
    return $options[[int]$sel - 1]
}

if (-not $Target) { $Target = Choose "Where should it run?" @("local","cloud","cloud-split") }
if (-not $Mode)   { $Mode   = Choose "Run mode?"            @("first","normal","initial") }

# Clear any DB_* env vars left over from a previous run in this shell
Get-ChildItem Env: | Where-Object { $_.Name -like "DB_URL*" -or $_.Name -eq "DB_DRIVER" -or $_.Name -eq "DB_DIALECT" } | ForEach-Object { Remove-Item "Env:$($_.Name)" -ErrorAction SilentlyContinue }

switch ($Target) {
    "local" {
        $env:TICKETING_PERSISTENCE = "memory"
        Write-Host "Target: LOCAL (in-memory, no database)" -ForegroundColor Yellow
    }
    "cloud" {
        if ($DB_PASSWORD -eq "CHANGE_ME") { Write-Error "Set DB_PASSWORD (and DB_HOST) first."; exit 1 }
        $env:TICKETING_PERSISTENCE = "jpa"
        $env:DB_URL       = "jdbc:postgresql://${DB_HOST}:5432/ticketing"
        $env:DB_DRIVER    = "org.postgresql.Driver"
        $env:DB_USERNAME  = $DB_USER
        $env:DB_PASSWORD  = $DB_PASSWORD
        $env:DB_DIALECT   = "org.hibernate.dialect.PostgreSQLDialect"
        $env:H2_CONSOLE_ENABLED = "false"
        Write-Host "Target: CLOUD single DB -> $($env:DB_URL)" -ForegroundColor Yellow
    }
    "cloud-split" {
        if ($DB_PASSWORD -eq "CHANGE_ME") { Write-Error "Set DB_PASSWORD (and DB_HOST) first."; exit 1 }
        $env:TICKETING_PERSISTENCE = "jpa"
        $env:DB_URL_OPERATIONAL = "jdbc:postgresql://${DB_HOST}:5432/ticketing"
        $env:DB_URL_CONFIG      = "jdbc:postgresql://${DB_HOST}:5432/ticketing_cfg"
        $env:DB_DRIVER    = "org.postgresql.Driver"
        $env:DB_USERNAME  = $DB_USER
        $env:DB_PASSWORD  = $DB_PASSWORD
        $env:DB_DIALECT   = "org.hibernate.dialect.PostgreSQLDialect"
        $env:H2_CONSOLE_ENABLED = "false"
        Write-Host "Target: CLOUD split DBs (ticketing + ticketing_cfg)" -ForegroundColor Yellow
        Write-Host "  (first time only: gcloud sql databases create ticketing_cfg --instance=$INSTANCE)" -ForegroundColor DarkGray
    }
}

$runArgs = $null
switch ($Mode) {
    "first"   { $env:DB_DDL_AUTO = if ($Target -eq "local") { "none" } else { "update" };   Write-Host "Mode: FIRST RUN (create/update schema)" -ForegroundColor Cyan }
    "normal"  { $env:DB_DDL_AUTO = if ($Target -eq "local") { "none" } else { "validate" }; Write-Host "Mode: NORMAL (validate schema)" -ForegroundColor Cyan }
    "initial" {
        $env:DB_DDL_AUTO = if ($Target -eq "local") { "none" } else { "create" }
        $runArgs = "-Dspring-boot.run.arguments=--ticketing.bootstrap.dataset=initial-state-file --ticketing.seed.enabled=false --ticketing.initial-state.file=classpath:initial-state/final-v3-scenario.txt"
        Write-Host "Mode: INITIAL STATE (wipe + load final-v3-scenario.txt)" -ForegroundColor Magenta
    }
}

if ($runArgs) { mvn spring-boot:run $runArgs } else { mvn spring-boot:run }

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n----------------------------------------------------------" -ForegroundColor Red
    Write-Host "The app did not start cleanly." -ForegroundColor Red
    Write-Host "Open logs\error.log - if it's a DB problem you'll see a framed" -ForegroundColor Red
    Write-Host "'DATABASE CONNECTION ERROR' block explaining the likely cause." -ForegroundColor Red
    Write-Host "Common Cloud SQL fixes: instance RUNNABLE? your IP in Authorized" -ForegroundColor Red
    Write-Host "networks? DB_PASSWORD correct? ticketing_cfg created (split mode)?" -ForegroundColor Red
    Write-Host "----------------------------------------------------------" -ForegroundColor Red
}