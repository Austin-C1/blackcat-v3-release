$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$backendScript = Join-Path $rootDir 'start-blackcat-backend.ps1'
$frontendDir = Join-Path $rootDir 'frontend'
$frontendUrl = 'http://127.0.0.1:18880'
$frontendLoginUrl = "$frontendUrl/login"
$databasePort = 13307
$databaseContainerName = 'blackcat-v1-mysql'
$databaseImage = 'mysql:8.1'
$databaseVolumeName = 'blackcat-v1-mysql-data'
$databaseName = 'blackcat_v1'
$databasePassword = 'change-me'
$dockerDesktopExe = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
$backendPort = 8000
$backendReadyUrl = "http://127.0.0.1:$backendPort/api/auth/check-first-use"
$frontendPort = 18880
$backendStartupTimeoutSeconds = 180
$frontendOutLog = Join-Path $rootDir 'frontend-live.out.log'
$frontendErrLog = Join-Path $rootDir 'frontend-live.err.log'
$frontendDistDir = Join-Path $frontendDir 'dist'
$frontendDistMarker = Join-Path $frontendDistDir '.desktop-runtime.json'
$frontendStaticServerScript = Join-Path $rootDir 'scripts\serve-blackcat-frontend.ps1'
$powershellExe = Join-Path $PSHOME 'powershell.exe'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

if (Test-Path $localConfig) {
    . $localConfig
}

function Test-PortListening {
    param(
        [int]$Port
    )

    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Wait-PortListening {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Wait-BackendReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest `
                -Uri $Url `
                -Method Post `
                -Body '{}' `
                -ContentType 'application/json' `
                -UseBasicParsing `
                -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Get-NewestWriteTime {
    param(
        [string[]]$Paths
    )

    $latest = Get-Date '2000-01-01'
    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            continue
        }

        $item = Get-Item $path
        if ($item.PSIsContainer) {
            $candidate = Get-ChildItem -Path $item.FullName -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
            if ($candidate -and $candidate.LastWriteTime -gt $latest) {
                $latest = $candidate.LastWriteTime
            }
            continue
        }

        if ($item.LastWriteTime -gt $latest) {
            $latest = $item.LastWriteTime
        }
    }

    return $latest
}

function Test-DesktopFrontendBuildAvailable {
    param(
        [int]$BackendPort
    )

    if (
        -not (Test-Path $frontendDistDir) `
        -or -not (Test-Path $frontendDistMarker) `
        -or -not (Test-Path $frontendStaticServerScript) `
        -or -not (Test-Path (Join-Path $frontendDistDir 'index.html'))
    ) {
        return $false
    }

    try {
        $marker = Get-Content -Path $frontendDistMarker -Raw | ConvertFrom-Json
    }
    catch {
        return $false
    }

    if ($marker.apiUrl -ne "http://127.0.0.1:$BackendPort" -or $marker.wsUrl -ne "ws://127.0.0.1:$BackendPort") {
        return $false
    }

    $sourceLatest = Get-NewestWriteTime -Paths @(
        (Join-Path $frontendDir 'src'),
        (Join-Path $frontendDir 'public'),
        (Join-Path $frontendDir 'index.html'),
        (Join-Path $frontendDir 'package.json'),
        (Join-Path $frontendDir 'package-lock.json'),
        (Join-Path $frontendDir 'vite.config.ts')
    )
    $buildLatest = Get-NewestWriteTime -Paths @($frontendDistDir)

    return $buildLatest -ge $sourceLatest
}

function Test-DockerAvailable {
    try {
        docker version --format '{{.Server.Version}}' 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

function Wait-DockerAvailable {
    param(
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerAvailable) {
            return $true
        }
        Start-Sleep -Seconds 5
    }

    return $false
}

function Ensure-DatabaseContainer {
    param(
        [string]$ContainerName,
        [int]$Port,
        [string]$Image,
        [string]$RootPassword,
        [string]$DatabaseName,
        [string]$VolumeName
    )

    $databaseContainerExists = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}"
    if ($databaseContainerExists -contains $ContainerName) {
        $databaseContainerRunning = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}"
        if (-not ($databaseContainerRunning -contains $ContainerName)) {
            docker start $ContainerName | Out-Null
        }
        return
    }

    docker run -d `
        --name $ContainerName `
        --restart unless-stopped `
        -p "${Port}:3306" `
        -e "TZ=Asia/Shanghai" `
        -e "MYSQL_ROOT_PASSWORD=$RootPassword" `
        -e "MYSQL_DATABASE=$DatabaseName" `
        -v "${VolumeName}:/var/lib/mysql" `
        $Image `
        --character-set-server=utf8mb4 `
        --collation-server=utf8mb4_unicode_ci | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Database container creation failed: $ContainerName"
    }
}

function Wait-DatabaseReady {
    param(
        [string]$ContainerName,
        [string]$RootPassword,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            docker exec $ContainerName sh -lc "mysqladmin ping -h 127.0.0.1 -p$RootPassword --silent" 1>$null 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

if (-not (Test-Path $backendScript)) {
    throw "Backend start script not found: $backendScript"
}

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

$frontendMode = if (Test-DesktopFrontendBuildAvailable -BackendPort $backendPort) { 'static' } else { 'dev' }

if (-not (Test-PortListening -Port $databasePort)) {
    if (-not (Test-DockerAvailable)) {
        if (-not (Test-Path $dockerDesktopExe)) {
            throw "Docker Desktop not found: $dockerDesktopExe"
        }

        Start-Process -FilePath $dockerDesktopExe | Out-Null
    }

    if (-not (Wait-DockerAvailable -TimeoutSeconds 180)) {
        throw 'Docker did not become available.'
    }

    Ensure-DatabaseContainer `
        -ContainerName $databaseContainerName `
        -Port $databasePort `
        -Image $databaseImage `
        -RootPassword $databasePassword `
        -DatabaseName $databaseName `
        -VolumeName $databaseVolumeName

    if (-not (Wait-PortListening -Port $databasePort -TimeoutSeconds 90)) {
        throw "Database did not start on port $databasePort."
    }

    if (-not (Wait-DatabaseReady -ContainerName $databaseContainerName -RootPassword $databasePassword -TimeoutSeconds 120)) {
        throw "Database did not become ready inside container $databaseContainerName."
    }
}

if (-not (Test-PortListening -Port $backendPort)) {
    Start-Process -FilePath $powershellExe `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backendScript) `
        -WorkingDirectory $rootDir `
        -WindowStyle Hidden | Out-Null
}

if (-not (Test-PortListening -Port $frontendPort)) {
    if ($frontendMode -eq 'static') {
        Start-Process -FilePath $powershellExe `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $frontendStaticServerScript, '-Root', $frontendDistDir, '-ListenHost', '127.0.0.1', '-Port', $frontendPort.ToString()) `
            -WorkingDirectory $rootDir `
            -RedirectStandardOutput $frontendOutLog `
            -RedirectStandardError $frontendErrLog `
            -WindowStyle Hidden | Out-Null
    }
    else {
        $npmCmd = (Get-Command 'npm.cmd' -ErrorAction Stop).Source
        Start-Process -FilePath $npmCmd `
            -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '18880') `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput $frontendOutLog `
            -RedirectStandardError $frontendErrLog `
            -WindowStyle Hidden | Out-Null
    }
}

if (-not (Wait-PortListening -Port $frontendPort -TimeoutSeconds 60)) {
    throw "Frontend did not start on port $frontendPort."
}

if (-not (Wait-BackendReady -Url $backendReadyUrl -TimeoutSeconds $backendStartupTimeoutSeconds)) {
    throw "Backend did not become ready at $backendReadyUrl."
}

if (-not (Wait-HttpReady -Url $frontendLoginUrl -TimeoutSeconds 60)) {
    throw "Frontend page did not become available at $frontendLoginUrl."
}

Start-Process $frontendLoginUrl | Out-Null
