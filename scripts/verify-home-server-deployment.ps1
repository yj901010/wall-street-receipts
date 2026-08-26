[CmdletBinding()]
param(
    [ValidateRange(120, 1800)]
    [int] $StartupTimeoutSeconds = 600,

    [switch] $RunBrowserSuite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-Condition {
    param(
        [Parameter(Mandatory)]
        [bool] $Condition,

        [Parameter(Mandatory)]
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-ProcessEnvironmentNameComparer {
    if ($IsWindows) {
        return [StringComparer]::OrdinalIgnoreCase
    }
    return [StringComparer]::Ordinal
}

function Invoke-WithProcessEnvironment {
    param(
        [Parameter(Mandatory)]
        [Collections.IDictionary] $Variables,

        [Parameter(Mandatory)]
        [scriptblock] $Action
    )

    $previous = [Collections.Generic.Dictionary[string, object]]::new(
        (Get-ProcessEnvironmentNameComparer)
    )
    try {
        foreach ($name in $Variables.Keys) {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            if ($null -eq $Variables[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            }
            else {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    [string] $Variables[$name],
                    "Process"
                )
            }
        }
        & $Action
    }
    finally {
        foreach ($name in $Variables.Keys) {
            if ($null -eq $previous[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            }
            else {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    [string] $previous[$name],
                    "Process"
                )
            }
        }
    }
}

function New-ProcessEnvironmentMap {
    $result = [Collections.Generic.Dictionary[string, object]]::new(
        (Get-ProcessEnvironmentNameComparer)
    )
    return ,$result
}

function Add-InheritedEnvironmentRemovals {
    param(
        [Parameter(Mandatory)]
        [Collections.Generic.Dictionary[string, object]] $Variables,

        [Parameter(Mandatory)]
        [string] $NamePattern
    )

    foreach ($inheritedName in [Environment]::GetEnvironmentVariables("Process").Keys) {
        $name = [string] $inheritedName
        if ($name -match $NamePattern) {
            $Variables[$name] = $null
        }
    }
}

function Get-SelectedDockerEndpoint {
    param([Parameter(Mandatory)][string] $DockerCommand)

    $contextOverride = [Environment]::GetEnvironmentVariable("DOCKER_CONTEXT", "Process")
    $hostOverride = [Environment]::GetEnvironmentVariable("DOCKER_HOST", "Process")
    if (-not [string]::IsNullOrWhiteSpace($contextOverride)) {
        $arguments = @(
            "context", "inspect", $contextOverride.Trim(),
            "--format", "{{.Endpoints.docker.Host}}"
        )
    }
    elseif (-not [string]::IsNullOrWhiteSpace($hostOverride)) {
        return $hostOverride.Trim()
    }
    else {
        $arguments = @("context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
    }

    $endpointOutput = & $DockerCommand @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the selected Docker context without contacting its daemon."
    }
    return (($endpointOutput -join "").Trim())
}

function Assert-LocalDockerEndpoint {
    param([Parameter(Mandatory)][string] $Endpoint)

    $localEndpoint =
        $Endpoint -match '^unix:///.+' -or
        $Endpoint -match '^npipe:////\./pipe/.+' -or
        $Endpoint -match '^fd://.+' -or
        $Endpoint -match '^tcp://(?:127(?:\.[0-9]{1,3}){3}|\[::1\]):[0-9]+$'
    Assert-Condition $localEndpoint `
        "The rehearsal rejects remote Docker endpoints before daemon contact."
}

function Invoke-DockerProcess {
    param([Parameter(Mandatory)][string[]] $Arguments)

    $result = Invoke-WithProcessEnvironment $script:DockerEnvironment {
        $nativeOutput = @(& $script:DockerCommand @Arguments 2>&1)
        [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = $nativeOutput
        }
    }
    return ,$result
}

function Invoke-DockerCommand {
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [switch] $Capture
    )

    $result = Invoke-DockerProcess -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $($result.ExitCode)."
    }

    if ($Capture) {
        return @($result.Output)
    }
    $result.Output | ForEach-Object { Write-Host $_ }
}

function Invoke-ComposeCommand {
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [switch] $Capture
    )

    $composeArguments = @(
        "compose",
        "--env-file", $script:EnvFile,
        "--file", $script:ComposeFile,
        "--project-name", $script:ProjectName
    ) + $Arguments
    return Invoke-DockerCommand -Arguments $composeArguments -Capture:$Capture
}

function Get-RandomLowerHex {
    param([ValidateRange(4, 32)][int] $ByteCount)

    $bytes = [byte[]]::new($ByteCount)
    try {
        [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        return [Convert]::ToHexString($bytes).ToLowerInvariant()
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-MinimumComposeVersion {
    param([Parameter(Mandatory)][string] $Version)

    $match = [regex]::Match($Version.Trim(), '^v?(?<major>\d+)\.(?<minor>\d+)(?:\.(?<patch>\d+))?')
    Assert-Condition $match.Success "Docker Compose returned an unreadable version."
    $major = [int] $match.Groups['major'].Value
    $minor = [int] $match.Groups['minor'].Value
    Assert-Condition ($major -gt 2 -or ($major -eq 2 -and $minor -ge 20)) `
        "Docker Compose 2.20.0 or newer is required."
}

function Remove-OwnedTemporaryDirectory {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $ExpectedBase,
        [Parameter(Mandatory)][string] $ExpectedLeaf,
        [Parameter(Mandatory)][string] $MarkerPath,
        [Parameter(Mandatory)][string] $MarkerValue
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $item = Get-Item -LiteralPath $Path -Force
    Assert-Condition $item.PSIsContainer "Refused to remove a temporary path that is not a directory."
    Assert-Condition (-not $item.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) `
        "Refused to remove a reparse-point rehearsal directory."
    $resolvedPath = [IO.Path]::GetFullPath($item.FullName)
    $comparison = if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    Assert-Condition ([IO.Path]::GetFileName($resolvedPath) -eq $ExpectedLeaf) `
        "Refused to remove an unexpected rehearsal directory name."
    Assert-Condition (
        [IO.Path]::GetDirectoryName($resolvedPath).TrimEnd('\', '/').Equals(
            $ExpectedBase.TrimEnd('\', '/'),
            $comparison
        )
    ) "Refused to remove a rehearsal directory outside the operating-system temp root."
    $marker = Get-Item -LiteralPath $MarkerPath -Force
    Assert-Condition (-not $marker.PSIsContainer) "The rehearsal ownership marker is not a file."
    Assert-Condition (-not $marker.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) `
        "Refused to trust a reparse-point rehearsal ownership marker."
    Assert-Condition ((Get-Content -LiteralPath $MarkerPath -Raw) -eq $MarkerValue) `
        "The rehearsal ownership marker does not match this run."
    [IO.Directory]::Delete($resolvedPath, $true)
}

function Get-AvailableRehearsalPort {
    foreach ($candidatePort in 18080..18179) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $candidatePort)
        try {
            $listener.Start()
            return $candidatePort
        }
        catch [Net.Sockets.SocketException] {
            continue
        }
        finally {
            $listener.Stop()
        }
    }
    throw "No available IPv4 loopback port was found in the rehearsal range."
}

function Get-HttpResponse {
    param(
        [Parameter(Mandatory)]
        [Net.Http.HttpClient] $Client,

        [Parameter(Mandatory)]
        [Net.Http.HttpMethod] $Method,

        [Parameter(Mandatory)]
        [string] $Path
    )

    for ($attempt = 1; $attempt -le 20; $attempt++) {
        $request = [Net.Http.HttpRequestMessage]::new($Method, $Path)
        try {
            if ($Method -eq [Net.Http.HttpMethod]::Post) {
                $request.Content = [Net.Http.StringContent]::new("")
            }
            $response = $Client.Send($request)
            try {
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                $headers = @{}
                foreach ($header in $response.Headers) {
                    $headers[$header.Key] = @($header.Value)
                }
                return [pscustomobject]@{
                    StatusCode = [int] $response.StatusCode
                    ContentType = $response.Content.Headers.ContentType.MediaType
                    Body = $body
                    Headers = $headers
                }
            }
            finally {
                $response.Dispose()
            }
        }
        catch [Net.Http.HttpRequestException] {
            if ($attempt -eq 20) {
                throw
            }
            Start-Sleep -Milliseconds 250
        }
        finally {
            $request.Dispose()
        }
    }
}

Assert-Condition ($PSVersionTable.PSVersion.Major -ge 7) `
    "PowerShell 7 or newer is required."

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$script:ComposeFile = Join-Path $repoRoot "deploy/home-server/compose.yaml"
$nextEnvPath = Join-Path $repoRoot "apps/web/next-env.d.ts"
Assert-Condition (Test-Path -LiteralPath $script:ComposeFile -PathType Leaf) `
    "Home-server Compose file is missing."

$nextEnvHashBefore = $null
if (Test-Path -LiteralPath $nextEnvPath -PathType Leaf) {
    $nextEnvHashBefore = (Get-FileHash -LiteralPath $nextEnvPath -Algorithm SHA256).Hash
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
Assert-Condition ($null -ne $dockerCommand) "Docker is required and must be on PATH."

$script:DockerCommand = $dockerCommand.Source
$selectedEndpoint = Get-SelectedDockerEndpoint -DockerCommand $script:DockerCommand
Assert-LocalDockerEndpoint -Endpoint $selectedEndpoint
$script:DockerEnvironment = New-ProcessEnvironmentMap
foreach ($pattern in @(
    '^(?i:DOCKER_)',
    '^(?i:COMPOSE_)',
    '^(?i:WSR_)',
    '^(?i:(?:HTTP|HTTPS|ALL|NO)_PROXY)$'
)) {
    Add-InheritedEnvironmentRemovals -Variables $script:DockerEnvironment -NamePattern $pattern
}
$script:DockerEnvironment['DOCKER_CONTEXT'] = $null
$script:DockerEnvironment['DOCKER_HOST'] = $selectedEndpoint
$script:DockerEnvironment['DOCKER_TLS_VERIFY'] = $null
$script:DockerEnvironment['DOCKER_CERT_PATH'] = $null

Invoke-DockerCommand -Arguments @("info") | Out-Null
$composeVersion = Invoke-DockerCommand -Arguments @("compose", "version", "--short") -Capture
Assert-MinimumComposeVersion -Version ((@($composeVersion) -join "").Trim())

$runId = Get-RandomLowerHex -ByteCount 8
$script:ProjectName = "wsr-home-$runId"
$imageTag = "rehearsal-$runId"
$apiImage = "wall-street-receipts-api:$imageTag"
$webImage = "wall-street-receipts-web:$imageTag"
$caddyImage = "wall-street-receipts-caddy:$imageTag"
$requestedPort = Get-AvailableRehearsalPort
$publishedPort = $null
$temporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporaryLeaf = "wsr-home-rehearsal-$runId"
$temporaryRoot = [IO.Path]::Combine($temporaryBase, $temporaryLeaf)
$temporaryRootOwned = $false
$ownershipMarkerPath = Join-Path $temporaryRoot ".wsr-owner"
$ownershipMarkerValue = "ADR-046:$runId"
$secretPath = Join-Path $temporaryRoot "postgres_password"
$script:EnvFile = Join-Path $temporaryRoot "compose.env"
$stackStarted = $false
$apiImageOwned = $false
$webImageOwned = $false
$caddyImageOwned = $false
$httpClient = $null
$failure = $null

try {
    New-Item -ItemType Directory -Path $temporaryRoot -ErrorAction Stop | Out-Null
    $temporaryRootOwned = $true
    [IO.File]::WriteAllText(
        $ownershipMarkerPath,
        $ownershipMarkerValue,
        [Text.UTF8Encoding]::new($false)
    )

    foreach ($candidateImage in @($apiImage, $webImage, $caddyImage)) {
        $inspection = Invoke-DockerProcess -Arguments @("image", "inspect", $candidateImage)
        Assert-Condition ($inspection.ExitCode -ne 0) `
            "The random rehearsal image tag unexpectedly exists: $candidateImage"
    }

    $secretBytes = [byte[]]::new(32)
    try {
        [Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
        $secretText = [Convert]::ToHexString($secretBytes).ToLowerInvariant()
        [IO.File]::WriteAllText(
            $secretPath,
            $secretText + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false)
        )
    }
    finally {
        [Array]::Clear($secretBytes, 0, $secretBytes.Length)
        $secretText = $null
    }

    $portableSecretPath = $secretPath.Replace('\', '/')
    $envContents = @(
        "WSR_DOMAIN=wsr.invalid",
        "WSR_ACME_EMAIL=operator@wsr.invalid",
        "WSR_IMAGE_TAG=$imageTag",
        "WSR_POSTGRES_PASSWORD_FILE=$portableSecretPath",
        "WSR_REHEARSAL_PORT=$requestedPort",
        "WSR_INGRESS_MODE=unknown",
        "WSR_PUBLIC_IP_POLICY=unknown"
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText(
        $script:EnvFile,
        $envContents + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )

    Write-Host "[1/6] Validating the rehearsal Compose model..."
    $renderedLines = Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal", "config", "--format", "json"
    ) -Capture
    $rendered = ((@($renderedLines) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable)
    Assert-Condition (
        @($rendered.services.Keys).Count -eq 4 -and
        @($rendered.services.Keys | Where-Object { $_ -notin @("postgres", "api", "web", "caddy-rehearsal") }).Count -eq 0
    ) "The effective rehearsal service set was overridden."
    Assert-Condition ($rendered.services.api.image -eq $apiImage) `
        "The effective API image tag was overridden."
    Assert-Condition ($rendered.services.web.image -eq $webImage) `
        "The effective web image tag was overridden."
    Assert-Condition ($rendered.services."caddy-rehearsal".image -eq $caddyImage) `
        "The effective Caddy image tag was overridden."
    Assert-Condition (
        $rendered.services."caddy-rehearsal".environment.WSR_DOMAIN -eq "https://127.0.0.1:8443" -and
        $rendered.services."caddy-rehearsal".environment.WSR_DEFAULT_SNI -eq "127.0.0.1"
    ) "The private-TLS rehearsal site or default SNI was overridden."
    $renderedPort = @($rendered.services."caddy-rehearsal".ports)[0]
    Assert-Condition (
        $renderedPort.host_ip -eq "127.0.0.1" -and
        [int] $renderedPort.published -eq $requestedPort
    ) "The effective loopback rehearsal port was overridden."
    Assert-Condition (
        [IO.Path]::GetFullPath($rendered.secrets.postgres_password.file) -eq
            [IO.Path]::GetFullPath($secretPath)
    ) "The effective PostgreSQL secret path was overridden."

    Write-Host "[2/6] Building isolated API, web, and Caddy runtime images..."
    $apiImageOwned = $true
    $webImageOwned = $true
    $caddyImageOwned = $true
    Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal", "build", "--pull", "api", "web", "caddy-rehearsal"
    )
    Invoke-DockerCommand -Arguments @(
        "run", "--rm", "--network", "none", "--read-only",
        "--security-opt", "no-new-privileges:true", "--cap-drop", "ALL",
        "--mount", "type=volume,destination=/data",
        "--mount", "type=volume,destination=/config",
        "--entrypoint", "/bin/sh", $caddyImage,
        "-c", "test -w /data && test -w /config && touch /data/.write-probe /config/.write-probe"
    ) | Out-Null

    Write-Host "[3/6] Starting the loopback-only rehearsal profile..."
    $stackStarted = $true
    Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal",
        "up", "--detach", "--wait", "--wait-timeout", "$StartupTimeoutSeconds",
        "postgres", "api", "web", "caddy-rehearsal"
    )
    $caddyContainerId = Invoke-ComposeCommand -Arguments @("ps", "--quiet", "caddy-rehearsal") -Capture
    $caddyContainerIdText = (@($caddyContainerId) -join "").Trim()
    Assert-Condition ($caddyContainerIdText -match '^[0-9a-f]{12,64}$') `
        "Unable to resolve the exact rehearsal Caddy container."
    $portJson = Invoke-DockerCommand -Arguments @(
        "inspect", "--format", "{{json .HostConfig.PortBindings}}", $caddyContainerIdText
    ) -Capture
    $networkPorts = ((@($portJson) -join "").Trim() | ConvertFrom-Json -AsHashtable)
    $caddyBindings = @($networkPorts["8443/tcp"])
    Assert-Condition ($caddyBindings.Count -eq 1) `
        "Rehearsal Caddy must publish exactly one runtime port binding."
    $caddyBinding = $caddyBindings[0]
    Assert-Condition ($caddyBinding["HostIp"] -eq "127.0.0.1") `
        "Rehearsal Caddy runtime binding escaped IPv4 loopback."
    Assert-Condition ($caddyBinding["HostPort"] -match '^\d+$') `
        "Docker did not allocate a numeric rehearsal port."
    $publishedPort = [int] $caddyBinding["HostPort"]
    Assert-Condition ($publishedPort -ge 1 -and $publishedPort -le 65535) `
        "Docker allocated an invalid rehearsal port."
    Assert-Condition ($publishedPort -eq $requestedPort) `
        "Docker did not retain the preflighted rehearsal port."

    Write-Host "[4/6] Verifying runtime port isolation and database evidence..."
    foreach ($service in @("postgres", "api", "web")) {
        $containerId = Invoke-ComposeCommand -Arguments @("ps", "--quiet", $service) -Capture
        $containerIdText = (@($containerId) -join "").Trim()
        Assert-Condition ($containerIdText -match '^[0-9a-f]{12,64}$') `
            "Unable to resolve the exact $service container."
        $bindings = Invoke-DockerCommand -Arguments @(
            "inspect", "--format", "{{json .HostConfig.PortBindings}}", $containerIdText
        ) -Capture
        $bindingText = (@($bindings) -join "").Trim()
        Assert-Condition ($bindingText -eq "{}" -or $bindingText -eq "null") `
            "$service unexpectedly publishes a host port."
    }

    $counts = Invoke-ComposeCommand -Arguments @(
        "exec", "--no-TTY", "postgres",
        "psql", "--username", "wsr", "--dbname", "wsr", "--tuples-only", "--no-align",
        "--command", "SELECT (SELECT count(*) FROM analyst_calls) || '|' || (SELECT count(*) FROM analyst_call_revisions) || '|' || (SELECT count(*) FROM call_outcomes);"
    ) -Capture
    Assert-Condition ((@($counts) -join "").Trim() -eq "3|2|4") `
        "The fixture-backed PostgreSQL evidence must remain exactly 3|2|4."

    Write-Host "[5/6] Exercising all public routes through rehearsal Caddy..."
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $handler.AllowAutoRedirect = $false
    # The client base URI below is a fixed numeric-loopback endpoint. Its
    # ephemeral Caddy local-CA root is intentionally not installed on the host.
    $handler.ServerCertificateCustomValidationCallback =
        [Net.Http.HttpClientHandler]::DangerousAcceptAnyServerCertificateValidator
    $httpClient = [Net.Http.HttpClient]::new($handler, $true)
    $httpClient.BaseAddress = [Uri]::new("https://127.0.0.1:$publishedPort")
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)
    $routes = @(
        "/",
        "/market",
        "/calls",
        "/calls/demo-call-001",
        "/calls/demo-call-002",
        "/institutions",
        "/analysts",
        "/maps/sp500",
        "/maps/nasdaq100",
        "/markets/sp500",
        "/screener",
        "/methodology"
    )
    $homeResponse = $null
    $detailResponse = $null
    foreach ($route in $routes) {
        $response = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Get) -Path $route
        Assert-Condition ($response.StatusCode -eq 200) "$route returned HTTP $($response.StatusCode)."
        Assert-Condition ($response.ContentType -eq "text/html") "$route did not return text/html."
        Assert-Condition ($response.Body.Contains("WALL STREET RECEIPTS")) `
            "$route did not render the shared product shell."
        if ($route -eq "/") {
            $homeResponse = $response
        }
        if ($route -eq "/calls/demo-call-001") {
            $detailResponse = $response
        }
    }
    Assert-Condition ($null -ne $homeResponse) "The home response was not captured."
    Assert-Condition ($null -ne $detailResponse) "The call-detail response was not captured."
    Assert-Condition ($homeResponse.Body.Contains("DEMO")) `
        "The public home page did not disclose DEMO mode."
    Assert-Condition ($detailResponse.Body.Contains("DEMO index outlook")) `
        "The API-backed call detail lost its fixture source provenance."
    Assert-Condition ($detailResponse.Body.Contains("2026")) `
        "The API-backed call detail lost its point-in-time timestamp surface."
    Assert-Condition ($homeResponse.Headers.ContainsKey("X-Content-Type-Options")) `
        "Caddy did not add X-Content-Type-Options."
    $nosniffValues = $homeResponse.Headers["X-Content-Type-Options"]
    Assert-Condition ($nosniffValues -contains "nosniff") `
        "X-Content-Type-Options did not retain nosniff."
    Assert-Condition ($homeResponse.Headers["X-Frame-Options"] -contains "DENY") `
        "X-Frame-Options did not retain DENY."
    Assert-Condition ($homeResponse.Headers["Referrer-Policy"] -contains "strict-origin-when-cross-origin") `
        "Referrer-Policy changed."
    Assert-Condition ($homeResponse.Headers["Permissions-Policy"] -contains "camera=(), microphone=(), geolocation=()") `
        "Permissions-Policy changed."
    Assert-Condition (-not $homeResponse.Headers.ContainsKey("Server")) `
        "Caddy leaked its Server response header."

    $post = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Post) -Path "/"
    Assert-Condition ($post.StatusCode -ne 405 -and $post.StatusCode -lt 500) `
        "POST did not safely reach the Next application boundary."
    $put = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Put) -Path "/"
    Assert-Condition ($put.StatusCode -eq 405) `
        "Unsupported methods must be rejected by Caddy with HTTP 405."

    if ($RunBrowserSuite) {
        Write-Host "[browser] Running the 3-viewport Playwright suite through rehearsal Caddy..."
        $pnpmCommand = Get-Command pnpm -ErrorAction SilentlyContinue
        Assert-Condition ($null -ne $pnpmCommand) `
            "pnpm is required only when -RunBrowserSuite is selected."
        $browserEnvironment = New-ProcessEnvironmentMap
        foreach ($pattern in @(
            '^(?i:PLAYWRIGHT_)',
            '^(?i:(?:HTTP|HTTPS|ALL|NO)_PROXY)$',
            '^(?i:CI|NODE_OPTIONS|NODE_PATH|NODE_USE_ENV_PROXY)$'
        )) {
            Add-InheritedEnvironmentRemovals -Variables $browserEnvironment -NamePattern $pattern
        }
        $browserEnvironment['CI'] = "true"
        $browserEnvironment['PLAYWRIGHT_EXTERNAL_SERVER'] = "true"
        $browserEnvironment['PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS'] = "true"
        $browserEnvironment['PLAYWRIGHT_REHEARSAL_NO_RETRIES'] = "true"
        $browserEnvironment['PLAYWRIGHT_BASE_URL'] = "https://127.0.0.1:$publishedPort"
        $browserEnvironment['CALL_AUDIT_PROVIDER'] = "api"
        $browserEnvironment['API_BASE_URL'] = "http://api:8080"
        $browserOutput = Join-Path $temporaryRoot "playwright-results"
        Invoke-WithProcessEnvironment $browserEnvironment {
            & $pnpmCommand.Source `
                --dir (Join-Path $repoRoot "apps/web") `
                exec playwright test `
                --reporter=line `
                "--output=$browserOutput"
            if ($LASTEXITCODE -ne 0) {
                throw "The rehearsal Playwright suite failed with exit code $LASTEXITCODE."
            }
        }
    }

    Write-Host "[6/6] Rehearsal passed; cleaning only owned resources..."
    Write-Host "PASS: images, PostgreSQL, Spring, Next, loopback Caddy, 12 routes, headers, and 3|2|4 evidence compose correctly."
}
catch {
    $failure = $_
    if ($stackStarted) {
        Write-Warning "Rehearsal failed. Printing a bounded tail from the owned Compose project."
        $logResult = Invoke-DockerProcess -Arguments @(
            "compose", "--env-file", $script:EnvFile,
            "--file", $script:ComposeFile,
            "--project-name", $script:ProjectName,
            "--profile", "rehearsal",
            "logs", "--no-color", "--tail", "120"
        )
        $logResult.Output | ForEach-Object { Write-Warning $_ }
    }
}
finally {
    if ($null -ne $httpClient) {
        $httpClient.Dispose()
    }

    if ($stackStarted) {
        try {
            Invoke-ComposeCommand -Arguments @(
                "--profile", "rehearsal", "down", "--volumes", "--remove-orphans", "--timeout", "20"
            )
        }
        catch {
            if ($null -eq $failure) {
                $failure = $_
            }
            else {
                Write-Warning $_
            }
        }
    }

    foreach ($ownedImage in @(
        @{ Name = $apiImage; Owned = $apiImageOwned },
        @{ Name = $webImage; Owned = $webImageOwned },
        @{ Name = $caddyImage; Owned = $caddyImageOwned }
    )) {
        if ($ownedImage.Owned) {
            $removeResult = Invoke-DockerProcess -Arguments @(
                "image", "rm", $ownedImage.Name
            )
            if ($removeResult.ExitCode -ne 0 -and $null -eq $failure) {
                $failure = [InvalidOperationException]::new(
                    "Unable to remove owned rehearsal image $($ownedImage.Name)."
                )
            }
        }
    }

    if ($temporaryRootOwned) {
        try {
            Remove-OwnedTemporaryDirectory `
                -Path $temporaryRoot `
                -ExpectedBase $temporaryBase `
                -ExpectedLeaf $temporaryLeaf `
                -MarkerPath $ownershipMarkerPath `
                -MarkerValue $ownershipMarkerValue
            $temporaryRootOwned = $false
        }
        catch {
            if ($null -eq $failure) {
                $failure = $_
            }
            else {
                Write-Warning $_
            }
        }
        if ($temporaryRootOwned -and $null -eq $failure) {
            $failure = [InvalidOperationException]::new(
                "Unable to remove the owned rehearsal temporary directory."
            )
        }
    }

    if ($null -ne $nextEnvHashBefore) {
        $nextEnvHashAfter = (Get-FileHash -LiteralPath $nextEnvPath -Algorithm SHA256).Hash
        if ($nextEnvHashAfter -ne $nextEnvHashBefore -and $null -eq $failure) {
            $failure = [InvalidOperationException]::new(
                "The Docker rehearsal changed caller-owned apps/web/next-env.d.ts."
            )
        }
    }
}

if ($null -ne $failure) {
    throw $failure
}
