param(
    [string]$Root = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'frontend\dist'),
    [string]$ListenHost = '127.0.0.1',
    [int]$Port = 18880
)

$ErrorActionPreference = 'Stop'
$resolvedRoot = [System.IO.Path]::GetFullPath($Root)

if (-not (Test-Path $resolvedRoot -PathType Container)) {
    throw "Frontend root not found: $resolvedRoot"
}

function Get-ContentType {
    param(
        [string]$FilePath
    )

    switch ([System.IO.Path]::GetExtension($FilePath).ToLowerInvariant()) {
        '.css' { return 'text/css; charset=utf-8' }
        '.gif' { return 'image/gif' }
        '.html' { return 'text/html; charset=utf-8' }
        '.ico' { return 'image/x-icon' }
        '.jpg' { return 'image/jpeg' }
        '.jpeg' { return 'image/jpeg' }
        '.js' { return 'text/javascript; charset=utf-8' }
        '.json' { return 'application/json; charset=utf-8' }
        '.map' { return 'application/json; charset=utf-8' }
        '.png' { return 'image/png' }
        '.svg' { return 'image/svg+xml' }
        '.txt' { return 'text/plain; charset=utf-8' }
        '.webp' { return 'image/webp' }
        '.woff' { return 'font/woff' }
        '.woff2' { return 'font/woff2' }
        default { return 'application/octet-stream' }
    }
}

function Test-IsHtmlRoute {
    param(
        [string]$RequestPath
    )

    return [string]::IsNullOrEmpty([System.IO.Path]::GetExtension($RequestPath)) `
        -and -not $RequestPath.StartsWith('/api', [System.StringComparison]::OrdinalIgnoreCase) `
        -and -not $RequestPath.StartsWith('/ws', [System.StringComparison]::OrdinalIgnoreCase)
}

function Resolve-FrontendPath {
    param(
        [string]$RequestPath
    )

    $rawPath = if ([string]::IsNullOrWhiteSpace($RequestPath)) { '/' } else { $RequestPath.Split('?')[0] }
    $decodedPath = [System.Uri]::UnescapeDataString($rawPath)
    $relativePath = if ($decodedPath -eq '/') { 'index.html' } else { $decodedPath.TrimStart('/').Replace('/', '\') }
    $candidatePath = [System.IO.Path]::GetFullPath((Join-Path $resolvedRoot $relativePath))

    if (-not $candidatePath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $null
    }

    if (Test-Path $candidatePath -PathType Container) {
        $candidatePath = Join-Path $candidatePath 'index.html'
    }

    if (Test-Path $candidatePath -PathType Leaf) {
        return $candidatePath
    }

    if (Test-IsHtmlRoute -RequestPath $decodedPath) {
        return (Join-Path $resolvedRoot 'index.html')
    }

    return $null
}

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://$ListenHost`:$Port/")
$listener.Start()

Write-Output "BlackCat frontend server listening at http://$ListenHost`:$Port/"

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $response = $context.Response

        try {
            $method = $context.Request.HttpMethod
            if ($method -ne 'GET' -and $method -ne 'HEAD') {
                $body = [System.Text.Encoding]::UTF8.GetBytes('Method Not Allowed')
                $response.StatusCode = 405
                $response.ContentType = 'text/plain; charset=utf-8'
                $response.ContentLength64 = $body.Length
                if ($method -ne 'HEAD') {
                    $response.OutputStream.Write($body, 0, $body.Length)
                }
                continue
            }

            $filePath = Resolve-FrontendPath -RequestPath $context.Request.RawUrl
            if (-not $filePath) {
                $body = [System.Text.Encoding]::UTF8.GetBytes('Not Found')
                $response.StatusCode = 404
                $response.ContentType = 'text/plain; charset=utf-8'
                $response.ContentLength64 = $body.Length
                if ($method -ne 'HEAD') {
                    $response.OutputStream.Write($body, 0, $body.Length)
                }
                continue
            }

            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            $response.StatusCode = 200
            $response.ContentType = Get-ContentType -FilePath $filePath
            $response.ContentLength64 = $bytes.Length
            $response.Headers['Cache-Control'] = if ($filePath.EndsWith('.html')) { 'no-cache' } else { 'public, max-age=31536000, immutable' }

            if ($method -ne 'HEAD') {
                $response.OutputStream.Write($bytes, 0, $bytes.Length)
            }
        }
        catch {
            $body = [System.Text.Encoding]::UTF8.GetBytes('Internal Server Error')
            $response.StatusCode = 500
            $response.ContentType = 'text/plain; charset=utf-8'
            $response.ContentLength64 = $body.Length
            $response.OutputStream.Write($body, 0, $body.Length)
            Write-Error $_
        }
        finally {
            $response.OutputStream.Close()
            $response.Close()
        }
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
