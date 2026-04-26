$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$backendDir = Join-Path $rootDir 'backend'
$javaExe = Join-Path $rootDir '.tools\jdk-17.0.18+8\bin\java.exe'
$jarPath = Join-Path $backendDir 'build\libs\blackcat-v3-backend-3.0.1.jar'
$outLog = Join-Path $rootDir 'backend-live.out.log'
$errLog = Join-Path $rootDir 'backend-live.err.log'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:mysql://127.0.0.1:13307/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true' }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'change-me' }
$env:JWT_SECRET = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'change-me-change-me-change-me-change-me' }
$env:ENCRYPTION_KEY = if ($env:ENCRYPTION_KEY) { $env:ENCRYPTION_KEY } else { 'change-me-change-me-change-me-change-me' }
$env:ADMIN_RESET_PASSWORD_KEY = if ($env:ADMIN_RESET_PASSWORD_KEY) { $env:ADMIN_RESET_PASSWORD_KEY } else { 'change-me' }
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = '8000'

if (Test-Path $localConfig) {
    . $localConfig
}

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
