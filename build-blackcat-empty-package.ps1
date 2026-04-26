$ErrorActionPreference = 'Stop'

$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$desktopDir = [Environment]::GetFolderPath('Desktop')
$frontendBuildScript = Join-Path $rootDir 'build-blackcat-desktop-frontend.ps1'
$backendDir = Join-Path $rootDir 'backend'
$gradleCmd = Join-Path $backendDir 'gradlew.bat'
$javaHome = Join-Path $rootDir '.tools\jdk-17.0.18+8'
$frontendPackageJsonPath = Join-Path $rootDir 'frontend\package.json'
$backendBuildFilePath = Join-Path $backendDir 'build.gradle.kts'
$packageTemplateDir = Join-Path $rootDir 'packaging\blackcat-empty-package'
$readmeTemplatePath = Join-Path $packageTemplateDir 'README-template.txt'
$dockerGuideTemplatePath = Join-Path $packageTemplateDir 'docker-install-template.md'
$usageGuideTemplatePath = Join-Path $packageTemplateDir 'package-start-template.md'
$packageDatabasePort = 23307
$packageDatabasePassword = $null
$packageDatabaseName = 'blackcat_v1'

function Get-VersionFromBuildFiles {
    if (-not (Test-Path $frontendPackageJsonPath)) {
        throw "Frontend package.json not found: $frontendPackageJsonPath"
    }

    if (-not (Test-Path $backendBuildFilePath)) {
        throw "Backend build.gradle.kts not found: $backendBuildFilePath"
    }

    $frontendVersion = (Get-Content -Path $frontendPackageJsonPath -Raw | ConvertFrom-Json).version
    $backendBuildFileContent = Get-Content -Path $backendBuildFilePath -Raw
    $backendVersionMatch = [regex]::Match($backendBuildFileContent, '(?m)^version\s*=\s*"([^"]+)"')
    if (-not $backendVersionMatch.Success) {
        throw "Unable to read backend version from $backendBuildFilePath"
    }

    $backendVersion = $backendVersionMatch.Groups[1].Value.Trim()
    if ([string]::IsNullOrWhiteSpace($backendVersion)) {
        throw "Backend version is empty in $backendBuildFilePath"
    }

    if (-not [string]::IsNullOrWhiteSpace($frontendVersion) -and $frontendVersion.Trim() -ne $backendVersion) {
        Write-Warning "Frontend version '$frontendVersion' does not match backend version '$backendVersion'. Packaging uses backend version."
    }

    return $backendVersion
}

function New-RandomHex {
    param(
        [int]$Length = 32
    )

    $alphabet = '0123456789abcdef'.ToCharArray()
    return -join (1..$Length | ForEach-Object { $alphabet[(Get-Random -Minimum 0 -Maximum $alphabet.Length)] })
}

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Replace-ExactText {
    param(
        [string]$Path,
        [hashtable]$Replacements
    )

    $content = Get-Content -Path $Path -Raw
    foreach ($key in $Replacements.Keys) {
        if (-not $content.Contains($key)) {
            throw "Expected text not found while updating $Path : $key"
        }
        $content = $content.Replace($key, $Replacements[$key])
    }

    Write-Utf8File -Path $Path -Content $content
}

function Render-TemplateFile {
    param(
        [string]$TemplatePath,
        [string]$OutputPath,
        [hashtable]$Variables
    )

    if (-not (Test-Path $TemplatePath)) {
        throw "Template file not found: $TemplatePath"
    }

    $content = Get-Content -Path $TemplatePath -Raw
    foreach ($key in $Variables.Keys) {
        $content = $content.Replace($key, $Variables[$key])
    }

    Write-Utf8File -Path $OutputPath -Content $content
}

$version = Get-VersionFromBuildFiles
$packageDatabasePassword = New-RandomHex -Length 32
$packageSlug = "blackcat-empty-v$version"
$packageDirName = "BlackCat-Blank-v$version"
$packageDir = Join-Path $desktopDir $packageDirName
$zipPath = Join-Path $desktopDir "$packageDirName.zip"
$jarPath = Join-Path $backendDir "build\libs\blackcat-v3-backend-$version.jar"
$packageContainerName = "$packageSlug-mysql"
$packageVolumeName = "$packageSlug-mysql-data"
$packageAdminResetKey = New-RandomHex -Length 32

foreach ($path in @($packageDir, $zipPath)) {
    if (Test-Path $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

& $frontendBuildScript
if ($LASTEXITCODE -ne 0) {
    throw "Frontend desktop build failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path $javaHome)) {
    throw "Bundled Java runtime not found: $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Push-Location $backendDir
try {
    & $gradleCmd bootJar
    if ($LASTEXITCODE -ne 0) {
        throw "Backend bootJar build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found after build: $jarPath"
}

New-Item -ItemType Directory -Path $packageDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageDir 'backend\build\libs') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageDir 'frontend') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageDir 'scripts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageDir '.tools') -Force | Out-Null

Copy-Item -LiteralPath $jarPath -Destination (Join-Path $packageDir 'backend\build\libs') -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'launch-blackcat.ps1') -Destination $packageDir -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'launch-blackcat.cmd') -Destination $packageDir -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'start-blackcat-backend.ps1') -Destination $packageDir -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'start-blackcat-backend.cmd') -Destination $packageDir -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'scripts\serve-blackcat-frontend.ps1') -Destination (Join-Path $packageDir 'scripts') -Force
Copy-Item -LiteralPath (Join-Path $rootDir 'frontend\dist') -Destination (Join-Path $packageDir 'frontend') -Recurse -Force
Copy-Item -LiteralPath $javaHome -Destination (Join-Path $packageDir '.tools') -Recurse -Force

$packageLaunchScriptPath = Join-Path $packageDir 'launch-blackcat.ps1'
$packageBackendScriptPath = Join-Path $packageDir 'start-blackcat-backend.ps1'

Replace-ExactText -Path $packageLaunchScriptPath -Replacements @{
    "`$databasePort = 13307" = "`$databasePort = $packageDatabasePort"
    "`$databaseContainerName = 'blackcat-v1-mysql'" = "`$databaseContainerName = '$packageContainerName'"
    "`$databaseVolumeName = 'blackcat-v1-mysql-data'" = "`$databaseVolumeName = '$packageVolumeName'"
}

Replace-ExactText -Path $packageBackendScriptPath -Replacements @{
    "127.0.0.1:13307" = "127.0.0.1:$packageDatabasePort"
    "`$env:ADMIN_RESET_PASSWORD_KEY = '33eee2012b94b2097a31a9a22231bfcc'" = "`$env:ADMIN_RESET_PASSWORD_KEY = '$packageAdminResetKey'"
}

$templateVariables = @{
    '{{VERSION}}' = $version
    '{{RESET_KEY}}' = $packageAdminResetKey
    '{{CONTAINER_NAME}}' = $packageContainerName
    '{{DATABASE_PORT}}' = $packageDatabasePort.ToString()
}

Render-TemplateFile `
    -TemplatePath $readmeTemplatePath `
    -OutputPath (Join-Path $packageDir 'README.txt') `
    -Variables $templateVariables

Render-TemplateFile `
    -TemplatePath $dockerGuideTemplatePath `
    -OutputPath (Join-Path $packageDir '01-install-docker-desktop.md') `
    -Variables $templateVariables

Render-TemplateFile `
    -TemplatePath $usageGuideTemplatePath `
    -OutputPath (Join-Path $packageDir '02-start-blackcat-blank.md') `
    -Variables $templateVariables

$archiveInputs = Get-ChildItem -LiteralPath $packageDir -Force | Select-Object -ExpandProperty FullName
Compress-Archive -Path $archiveInputs -DestinationPath $zipPath -Force

Write-Output "Package version: $version"
Write-Output "Package directory: $packageDir"
Write-Output "Package zip: $zipPath"
Write-Output "Admin reset key: $packageAdminResetKey"
