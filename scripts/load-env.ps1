# Dot-source this before starting trade-api or executor, from the repo root:
#   . .\scripts\load-env.ps1
#
# Reads the root .env (single source of truth) into this shell session as real
# environment variables, so trade-api (no dotenv support) and executor (its own
# .env only holds the DB password / Fauxnance key) both pick up the same values
# without any file duplicating them.

$envFile = Join-Path $PSScriptRoot "..\.env"

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    if ($line -match '^\s*([^=]+?)\s*=\s*(.*)\s*$') {
        $name = $matches[1]
        # Drop inline comments (" # ..."); a '#' not preceded by whitespace is part of the value.
        $value = ($matches[2] -replace '\s+#.*$', '').Trim()
        Set-Item -Path "env:$name" -Value $value
    }
}

# Remove legacy shared SERVER_PORT if present; services now use dedicated variables.
if (Test-Path Env:SERVER_PORT) {
    Remove-Item Env:SERVER_PORT
}

# .env sets SPRING_PROFILES_ACTIVE=platform for Docker; local runs use each service's
# default "local" profile instead.
if (Test-Path Env:SPRING_PROFILES_ACTIVE) {
    Remove-Item Env:SPRING_PROFILES_ACTIVE
}

# Derive the bootstrap-servers string both Spring services expect from the
# single KAFKA_HOST value, instead of storing "host:port" redundantly.
$env:KAFKA_BOOTSTRAP_SERVERS = "$($env:KAFKA_HOST):8085"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = $env:KAFKA_BOOTSTRAP_SERVERS

Write-Host "Loaded env from .env - KAFKA_BOOTSTRAP_SERVERS=$env:KAFKA_BOOTSTRAP_SERVERS"
Write-Host "Loaded env from .env - SPRING_KAFKA_BOOTSTRAP_SERVERS=$env:SPRING_KAFKA_BOOTSTRAP_SERVERS"
