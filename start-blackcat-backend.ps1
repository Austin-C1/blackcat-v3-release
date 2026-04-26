$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$backendDir = Join-Path $rootDir 'backend'
$javaExe = Join-Path $rootDir '.tools\jdk-17.0.18+8\bin\java.exe'
$jarFile = Get-ChildItem -Path (Join-Path $backendDir 'build\libs') -Filter 'blackcat-v3-backend-*.jar' -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$jarPath = if ($jarFile) { $jarFile.FullName } else { Join-Path $backendDir 'build\libs\blackcat-v3-backend.jar' }
$outLog = Join-Path $rootDir 'backend-live.out.log'
$errLog = Join-Path $rootDir 'backend-live.err.log'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:mysql://127.0.0.1:13307/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true' }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'change-me' }
$env:JWT_SECRET = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'change-me-change-me-change-me-change-me' }
$env:ENCRYPTION_KEY = if ($env:ENCRYPTION_KEY) { $env:ENCRYPTION_KEY } else { 'change-me-change-me-change-me-change-me' }
$env:ADMIN_RESET_PASSWORD_KEY = if ($env:ADMIN_RESET_PASSWORD_KEY) { $env:ADMIN_RESET_PASSWORD_KEY } else { 'change-me' }
$env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_ENABLED = if ($env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_ENABLED) { $env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_ENABLED } else { 'true' }
$env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_USERNAME = if ($env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_USERNAME) { $env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_USERNAME } else { '123456' }
$env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_PASSWORD = if ($env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_PASSWORD) { $env:BLACKCAT_PACKAGE_DEFAULT_ADMIN_PASSWORD } else { '123456' }
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = '8000'

if (Test-Path $localConfig) {
    . $localConfig
}

function Set-TrimmedEnv {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($null -ne $value) {
        [Environment]::SetEnvironmentVariable($Name, $value.Trim(), 'Process')
    }
}

@(
    'DB_URL',
    'DB_USERNAME',
    'DB_PASSWORD',
    'JWT_SECRET',
    'ENCRYPTION_KEY',
    'ADMIN_RESET_PASSWORD_KEY',
    'BLACKCAT_PACKAGE_DEFAULT_ADMIN_ENABLED',
    'BLACKCAT_PACKAGE_DEFAULT_ADMIN_USERNAME',
    'BLACKCAT_PACKAGE_DEFAULT_ADMIN_PASSWORD'
) |
    ForEach-Object { Set-TrimmedEnv -Name $_ }

if (-not (Test-Path $javaExe)) {
    throw "Java runtime not found: $javaExe"
}

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found: $jarPath"
}

Push-Location $backendDir
try {
    & $javaExe -jar $jarPath 1>>$outLog 2>>$errLog
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
