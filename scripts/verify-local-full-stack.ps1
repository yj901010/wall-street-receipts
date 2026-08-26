#Requires -Version 7.0

[CmdletBinding()]
param(
    [ValidateRange(30, 600)]
    [int] $StartupTimeoutSeconds = 180,

    [switch] $SkipPackage,

    [switch] $SkipWebBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

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

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $ArgumentList,

        [Parameter(Mandatory)]
        [string] $FailureMessage
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)."
    }
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
            $value = $Variables[$name]
            if ($null -eq $value) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            }
            else {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    [string] $value,
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
                    $previous[$name],
                    "Process"
                )
            }
        }
    }
}

function Get-ProcessEnvironmentNameComparer {
    if ($IsWindows) {
        return [StringComparer]::OrdinalIgnoreCase
    }
    return [StringComparer]::Ordinal
}

function ConvertTo-ProcessEnvironmentMap {
    param(
        [Parameter(Mandatory)]
        [Collections.IDictionary] $Variables
    )

    $result = [Collections.Generic.Dictionary[string, object]]::new(
        (Get-ProcessEnvironmentNameComparer)
    )
    foreach ($entry in $Variables.GetEnumerator()) {
        $result[[string] $entry.Key] = $entry.Value
    }
    return ,$result
}

function Add-InheritedEnvironmentRemovals {
    param(
        [Parameter(Mandatory)]
        [Collections.Generic.Dictionary[string, object]] $Variables,

        [Parameter(Mandatory)]
        [string] $NamePattern
    )

    foreach (
        $inheritedEnvironmentName in
        [Environment]::GetEnvironmentVariables("Process").Keys
    ) {
        $environmentName = [string] $inheritedEnvironmentName
        if (
            $environmentName -match $NamePattern -and
            -not $Variables.ContainsKey($environmentName)
        ) {
            $Variables[$environmentName] = $null
        }
    }
}

function Enter-RepositoryAcceptanceLock {
    param(
        [Parameter(Mandatory)]
        [string] $RepositoryPath,

        [Parameter(Mandatory)]
        [string] $RunId,

        [Parameter(Mandatory)]
        [string] $HarnessId
    )

    $resolvedRepository = [IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $RepositoryPath).Path
    )
    $lockPath = Join-Path $resolvedRepository ".wsr-local-acceptance.lock"
    $lockStream = $null
    try {
        $lockStream = [IO.File]::Open(
            $lockPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
    }
    catch [IO.IOException] {
        throw (
            "Another ADR-044/ADR-045 local acceptance harness owns this " +
            "repository, or a prior hard-terminated run left " +
            ".wsr-local-acceptance.lock. Wait for the owner; if none exists, " +
            "inspect leftover harness processes and Docker resources before " +
            "removing only that lock file."
        )
    }

    try {
        $metadata = [ordered]@{
            schemaVersion = 1
            harness       = $HarnessId
            runId         = $RunId
            processId     = $PID
            acquiredAt    = [DateTimeOffset]::UtcNow.ToString("O")
        } | ConvertTo-Json -Compress
        $metadataBytes = [Text.Encoding]::UTF8.GetBytes($metadata)
        try {
            $lockStream.Write($metadataBytes, 0, $metadataBytes.Length)
            $lockStream.Flush($true)
        }
        finally {
            [Array]::Clear($metadataBytes, 0, $metadataBytes.Length)
        }

        return [pscustomobject]@{
            Path   = $lockPath
            Stream = $lockStream
        }
    }
    catch {
        if ($null -ne $lockStream) {
            $lockStream.Dispose()
        }
        if (Test-Path -LiteralPath $lockPath -PathType Leaf) {
            Remove-Item -LiteralPath $lockPath -Force
        }
        throw
    }
}

function Exit-RepositoryAcceptanceLock {
    param(
        [Parameter(Mandatory)]
        [pscustomobject] $Lock,

        [Parameter(Mandatory)]
        [string] $RepositoryPath
    )

    $resolvedRepository = [IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $RepositoryPath).Path
    )
    $expectedPath = [IO.Path]::GetFullPath(
        (Join-Path $resolvedRepository ".wsr-local-acceptance.lock")
    )
    $actualPath = [IO.Path]::GetFullPath([string] $Lock.Path)
    $comparison = if ($IsWindows) {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }
    Assert-Condition ($actualPath.Equals($expectedPath, $comparison)) `
        "Refusing to release an unexpected repository acceptance lock."

    $Lock.Stream.Dispose()
    Remove-Item -LiteralPath $expectedPath -Force
    Assert-Condition (-not (Test-Path -LiteralPath $expectedPath)) `
        "The repository acceptance lock file remained after release."
}

function New-AcceptanceWebMirror {
    param(
        [Parameter(Mandatory)]
        [string] $SourceWebDirectory,

        [Parameter(Mandatory)]
        [string] $SourceFixturesDirectory,

        [Parameter(Mandatory)]
        [string] $MirrorRoot,

        [Parameter(Mandatory)]
        [string] $MirrorWebDirectory
    )

    Assert-Condition (Test-Path -LiteralPath $MirrorRoot -PathType Container) `
        "The harness must own the web acceptance mirror root before populating it."
    Assert-Condition (
        @(Get-ChildItem -LiteralPath $MirrorRoot -Force).Count -eq 0
    ) "Refusing to populate a non-empty web acceptance mirror."
    [IO.Directory]::CreateDirectory($MirrorWebDirectory) | Out-Null
    [IO.Directory]::CreateDirectory(
        (Split-Path -Parent (Join-Path $MirrorRoot "fixtures/v1"))
    ) | Out-Null

    foreach ($fileName in @(
        "package.json",
        "next.config.ts",
        "next-env.d.ts",
        "tsconfig.json"
    )) {
        Copy-Item `
            -LiteralPath (Join-Path $SourceWebDirectory $fileName) `
            -Destination (Join-Path $MirrorWebDirectory $fileName)
    }
    Copy-Item `
        -LiteralPath (Join-Path $SourceWebDirectory "src") `
        -Destination (Join-Path $MirrorWebDirectory "src") `
        -Recurse
    Copy-Item `
        -LiteralPath $SourceFixturesDirectory `
        -Destination (Join-Path $MirrorRoot "fixtures/v1") `
        -Recurse
}

function Get-FreeLoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([Net.IPEndPoint] $listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Get-SelectedDockerEndpoint {
    param(
        [Parameter(Mandatory)]
        [string] $DockerCommand
    )

    $contextOverride = [Environment]::GetEnvironmentVariable(
        "DOCKER_CONTEXT",
        "Process"
    )
    $hostOverride = [Environment]::GetEnvironmentVariable(
        "DOCKER_HOST",
        "Process"
    )
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
        $arguments = @(
            "context", "inspect",
            "--format", "{{.Endpoints.docker.Host}}"
        )
    }

    $endpointOutput = & $DockerCommand @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the selected Docker context without contacting its daemon."
    }
    return (($endpointOutput -join "").Trim())
}

function Assert-LocalDockerEndpoint {
    param(
        [Parameter(Mandatory)]
        [string] $Endpoint
    )

    $localEndpoint =
        $Endpoint -match '^unix:///.+' -or
        $Endpoint -match '^npipe:////\./pipe/.+' -or
        $Endpoint -match '^fd://.+' -or
        $Endpoint -match '^tcp://(?:127(?:\.[0-9]{1,3}){3}|\[::1\]):[0-9]+$'
    Assert-Condition $localEndpoint `
        "The selected Docker endpoint is not local. Select a local Docker Desktop, unix-socket, named-pipe, or loopback context and retry."
}

function Invoke-HttpRequest {
    param(
        [Parameter(Mandatory)]
        [Net.Http.HttpClient] $Client,

        [Parameter(Mandatory)]
        [string] $Path
    )

    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $Path)
    $response = $null
    try {
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $contentType = if ($null -eq $response.Content.Headers.ContentType) {
            $null
        }
        else {
            $response.Content.Headers.ContentType.MediaType
        }
        return [pscustomobject]@{
            StatusCode  = [int] $response.StatusCode
            Content     = $content
            ContentType = $contentType
        }
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $request.Dispose()
    }
}

function Get-SanitizedLogTail {
    param(
        [Parameter(Mandatory)]
        [string[]] $Paths,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [AllowEmptyString()]
        [string[]] $SensitiveValues
    )

    $lines = foreach ($path in $Paths) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            Get-Content -LiteralPath $path -Tail 60
        }
    }
    $text = $lines -join [Environment]::NewLine
    foreach ($value in $SensitiveValues) {
        if (-not [string]::IsNullOrEmpty($value)) {
            $text = $text.Replace($value, "<redacted>")
        }
    }
    return $text
}

function Remove-VerifiedTemporaryDirectory {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $temporaryRootWithSeparator =
        $temporaryRoot.TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        ) + [IO.Path]::DirectorySeparatorChar
    $resolvedPath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path)
    $leaf = Split-Path -Leaf $resolvedPath
    $comparison = if ($IsWindows) {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }

    Assert-Condition `
        ($resolvedPath.StartsWith($temporaryRootWithSeparator, $comparison)) `
        "Refusing to remove a directory outside the operating-system temp root."
    Assert-Condition `
        ($leaf -match '^wsr-local-full-stack-[a-f0-9]{12}$') `
        "Refusing to remove an unexpected temporary directory."

    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

function Remove-VerifiedWebMirrorDirectory {
    param(
        [Parameter(Mandatory)]
        [string] $Path,

        [Parameter(Mandatory)]
        [string] $WebDirectory
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $resolvedPath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path)
    $resolvedWebDirectory = [IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $WebDirectory).Path
    )
    $parent = [IO.Path]::GetFullPath((Split-Path -Parent $resolvedPath))
    $leaf = Split-Path -Leaf $resolvedPath
    $comparison = if ($IsWindows) {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }

    Assert-Condition `
        ($parent.Equals($resolvedWebDirectory, $comparison)) `
        "Refusing to remove a web mirror outside the web application directory."
    Assert-Condition `
        ($leaf -match '^\.wsr-local-full-stack-[a-f0-9]{12}$') `
        "Refusing to remove an unexpected web mirror directory."
    Assert-Condition `
        (-not ((Get-Item -LiteralPath $resolvedPath).Attributes.HasFlag(
            [IO.FileAttributes]::ReparsePoint
        ))) `
        "Refusing to remove a reparse-point web mirror directory."

    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

function Stop-OwnedProcess {
    param(
        [Parameter(Mandatory)]
        [Diagnostics.Process] $Process,

        [Parameter(Mandatory)]
        [string] $Label
    )

    $Process.Refresh()
    if (-not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $stopped = $Process.WaitForExit(10000)
        if (-not $stopped) {
            throw "The exact $Label process did not exit within 10 seconds."
        }
    }
    $Process.Dispose()
}

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$apiDirectory = Join-Path $repositoryRoot "apps/api"
$webDirectory = Join-Path $repositoryRoot "apps/web"
$composePath = Join-Path $repositoryRoot "compose.yaml"
$safeEnvironmentPath = Join-Path $repositoryRoot ".env.example"
$fixturesDirectory = Join-Path $repositoryRoot "fixtures/v1"
$webPackagePath = Join-Path $webDirectory "package.json"
$playwrightConfigPath = Join-Path $webDirectory "playwright.config.ts"
$nextEnvironmentPath = Join-Path $webDirectory "next-env.d.ts"
$typescriptConfigPath = Join-Path $webDirectory "tsconfig.json"
$nextCliPath = Join-Path $webDirectory "node_modules/next/dist/bin/next"
$playwrightCliPath = Join-Path $webDirectory "node_modules/@playwright/test/cli.js"
$mavenWrapper = if ($IsWindows) {
    Join-Path $apiDirectory "mvnw.cmd"
}
else {
    Join-Path $apiDirectory "mvnw"
}
$runId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$temporaryDirectory = Join-Path (
    [IO.Path]::GetTempPath()
) ("wsr-local-full-stack-" + $runId)
$apiBuildDirectory = Join-Path $temporaryDirectory "api-target"
$sharedJarPath = Join-Path `
    $apiDirectory `
    "target/wall-street-receipts-api-0.0.1-SNAPSHOT.jar"
$isolatedJarPath = Join-Path `
    $apiBuildDirectory `
    "wall-street-receipts-api-0.0.1-SNAPSHOT.jar"
$jarPath = if ($SkipPackage) { $sharedJarPath } else { $isolatedJarPath }
$webMirrorRoot = Join-Path $webDirectory ".wsr-local-full-stack-$runId"
$mirroredWebDirectory = Join-Path $webMirrorRoot "apps/web"
$acceptanceWebDirectory = if ($SkipWebBuild) {
    $webDirectory
}
else {
    $mirroredWebDirectory
}
$nextDistributionDirectory = Join-Path $acceptanceWebDirectory ".next"
$nextBuildIdPath = Join-Path $nextDistributionDirectory "BUILD_ID"
$apiStandardOutputPath = Join-Path $temporaryDirectory "api.stdout.log"
$apiStandardErrorPath = Join-Path $temporaryDirectory "api.stderr.log"
$webStandardOutputPath = Join-Path $temporaryDirectory "web.stdout.log"
$webStandardErrorPath = Join-Path $temporaryDirectory "web.stderr.log"
$tomcatBaseDirectory = Join-Path $temporaryDirectory "tomcat"
$playwrightOutputPath = Join-Path $temporaryDirectory "playwright-results"
$composeProject =
    "wsr-fullstack-" + $PID + "-" + [Guid]::NewGuid().ToString("N").Substring(0, 8)

$dockerCommand = $null
$javaCommand = $null
$nodeCommand = $null
$mavenCommand = $null
$mavenArgumentPrefix = @()
$apiProcess = $null
$webProcess = $null
$apiHttpClient = $null
$apiHttpHandler = $null
$webHttpClient = $null
$webHttpHandler = $null
$composeMayExist = $false
$temporaryDirectoryOwned = $false
$webMirrorOwned = $false
$databasePassword = $null
$dockerEnvironment = $null
$composeEnvironment = $null
$apiEnvironment = $null
$webEnvironment = $null
$playwrightEnvironment = $null
$springApplicationJson = $null
$javaProbeEnvironment = $null
$nodeProbeEnvironment = $null
$mavenBuildEnvironment = $null
$repositoryLock = $null
$failure = $null
$cleanupFailures = [Collections.Generic.List[string]]::new()

try {
    $repositoryLock = Enter-RepositoryAcceptanceLock `
        $repositoryRoot `
        $runId `
        "ADR-045"

    Write-Host "[1/7] Checking Java 21, Node.js 24, Docker, and repository prerequisites..."
    foreach ($requiredPath in @(
        $composePath,
        $safeEnvironmentPath,
        $mavenWrapper,
        $webPackagePath,
        $playwrightConfigPath,
        $nextEnvironmentPath,
        $typescriptConfigPath
    )) {
        Assert-Condition (Test-Path -LiteralPath $requiredPath -PathType Leaf) `
            "Required repository file is missing: $requiredPath"
    }
    Assert-Condition (Test-Path -LiteralPath $fixturesDirectory -PathType Container) `
        "The canonical DEMO fixture directory is missing."
    Assert-Condition (Test-Path -LiteralPath (Join-Path $webDirectory "src") -PathType Container) `
        "The web source directory is missing."

    $dockerCommand = (
        Get-Command docker -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    ).Source
    $javaCommand = (
        Get-Command java -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    ).Source
    $nodeCommand = (
        Get-Command node -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    ).Source
    if ($IsWindows) {
        $mavenCommand = $mavenWrapper
    }
    else {
        $mavenCommand = (
            Get-Command sh -CommandType Application -ErrorAction Stop |
                Select-Object -First 1
        ).Source
        $mavenArgumentPrefix = @($mavenWrapper)
    }

    $javaProbeEnvironment = @{
        JAVA_TOOL_OPTIONS = $null
        JDK_JAVA_OPTIONS  = $null
        _JAVA_OPTIONS     = $null
        CLASSPATH         = $null
    }
    $javaSettingsOutput = Invoke-WithProcessEnvironment $javaProbeEnvironment {
        & $javaCommand -XshowSettings:properties -version 2>&1
    }
    $javaVersionOutput = $javaSettingsOutput -join " "
    Assert-Condition ($javaVersionOutput -match 'version "21(?:\.|\")') `
        "Java 21 is required for the local full-stack acceptance check."
    $javaHomeMatch = $javaSettingsOutput |
        Select-String -Pattern '^\s*java\.home\s*=\s*(.+)\s*$' |
        Select-Object -First 1
    Assert-Condition ($null -ne $javaHomeMatch) `
        "The checked Java runtime did not report java.home."
    $checkedJavaHome = $javaHomeMatch.Matches[0].Groups[1].Value.Trim()
    Assert-Condition (Test-Path -LiteralPath $checkedJavaHome -PathType Container) `
        "The checked Java 21 runtime home does not exist."

    $mavenBuildEnvironment = @{
        JAVA_HOME         = $checkedJavaHome
        MAVEN_ARGS        = $null
        MAVEN_OPTS        = $null
        MAVEN_SKIP_RC     = "true"
        JAVA_TOOL_OPTIONS = $null
        JDK_JAVA_OPTIONS  = $null
        _JAVA_OPTIONS     = $null
        CLASSPATH         = $null
    }
    $mavenVersionOutput = Invoke-WithProcessEnvironment $mavenBuildEnvironment {
        $output = & $mavenCommand @($mavenArgumentPrefix + @("-version")) 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "The checked Maven wrapper could not report its runtime."
        }
        return $output
    }
    Assert-Condition `
        (($mavenVersionOutput -join " ") -match 'Java version:\s*21(?:\.|,)') `
        "The Maven wrapper must run with the checked Java 21 runtime."
    $nodeProbeEnvironment = @{
        NODE_OPTIONS = $null
        NODE_PATH    = $null
    }
    $nodeVersionOutput = (
        (Invoke-WithProcessEnvironment $nodeProbeEnvironment {
            & $nodeCommand --version 2>&1
        }) -join ""
    ).Trim()
    Assert-Condition ($nodeVersionOutput -match '^v24\.') `
        "Node.js 24 is required for the local full-stack acceptance check."
    Assert-Condition (Test-Path -LiteralPath $nextCliPath -PathType Leaf) `
        "The Next.js runtime is missing. Run pnpm install --frozen-lockfile first."
    Assert-Condition (Test-Path -LiteralPath $playwrightCliPath -PathType Leaf) `
        "Playwright is unavailable. Run pnpm install --frozen-lockfile first."

    $dockerEndpoint = Get-SelectedDockerEndpoint $dockerCommand
    Assert-LocalDockerEndpoint $dockerEndpoint
    $dockerEnvironment = @{
        DOCKER_CONTEXT    = $null
        DOCKER_HOST       = $dockerEndpoint
        DOCKER_TLS_VERIFY = $null
        DOCKER_CERT_PATH  = $null
    }
    $dockerInfo = Invoke-WithProcessEnvironment $dockerEnvironment {
        $output = & $dockerCommand info --format '{{.ServerVersion}}' 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Docker is installed but its pinned local daemon is unavailable. Start Docker Desktop and retry."
        }
        return $output
    }
    Assert-Condition (-not [string]::IsNullOrWhiteSpace(($dockerInfo -join ""))) `
        "Docker did not report a server version."
    Invoke-WithProcessEnvironment $dockerEnvironment {
        & $dockerCommand compose version *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose v2 is required."
        }
    }

    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    $temporaryDirectoryOwned = $true

    if (-not $SkipPackage) {
        Write-Host "[2/7] Packaging the API into a harness-owned output directory..."
        $packageArguments = $mavenArgumentPrefix + @(
            "-B",
            "-ntp",
            "-f", (Join-Path $apiDirectory "pom.xml"),
            "-DskipTests",
            ("-Dwsr.build.directory=" + $apiBuildDirectory),
            "package"
        )
        Invoke-WithProcessEnvironment $mavenBuildEnvironment {
            Invoke-CheckedNative `
                $mavenCommand `
                $packageArguments `
                "API packaging failed"
        }
    }
    else {
        Write-Host "[2/7] Reusing the existing packaged API..."
    }
    Assert-Condition (Test-Path -LiteralPath $jarPath -PathType Leaf) `
        "The packaged API JAR was not found. Rerun without -SkipPackage."

    $ports = [Collections.Generic.HashSet[int]]::new()
    while ($ports.Count -lt 3) {
        [void] $ports.Add((Get-FreeLoopbackPort))
    }
    $reservedPorts = @($ports)
    $postgresPort = $reservedPorts[0]
    $apiPort = $reservedPorts[1]
    $webPort = $reservedPorts[2]
    $apiBaseUrl = "http://127.0.0.1:$apiPort"

    if (-not $SkipWebBuild) {
        Write-Host "[3/7] Building production Next.js from a secret-free harness-owned source mirror..."
        New-Item -ItemType Directory -Path $webMirrorRoot | Out-Null
        $webMirrorOwned = $true
        New-AcceptanceWebMirror `
            $webDirectory `
            $fixturesDirectory `
            $webMirrorRoot `
            $mirroredWebDirectory
        $buildEnvironment = @{
            CALL_AUDIT_PROVIDER   = "api"
            API_BASE_URL          = $apiBaseUrl
            NEXT_PUBLIC_DATA_MODE = "DEMO"
            NEXT_PUBLIC_API_BASE_URL = ""
            MARKET_PROVIDER       = "fixture"
            ANALYST_PROVIDER      = "fixture"
            SP500_HISTORY_PROVIDER = "fixture"
            MARKET_BOARD_PROVIDER = "fixture"
            METHODOLOGY_PROVIDER  = "fixture"
            INSTITUTION_DIRECTORY_PROVIDER = "fixture"
            ANALYST_DIRECTORY_PROVIDER = "fixture"
            MARKET_MAP_PROVIDER   = "fixture"
            MARKET_TREEMAP_PROVIDER = "fixture"
            NEXT_TELEMETRY_DISABLED = "1"
            NODE_ENV             = "production"
            NODE_OPTIONS         = ""
            NODE_PATH            = $null
            NODE_USE_ENV_PROXY   = $null
            HTTP_PROXY           = $null
            HTTPS_PROXY          = $null
            ALL_PROXY            = $null
            NO_PROXY             = $null
            CI                   = ""
        }
        $buildEnvironment = ConvertTo-ProcessEnvironmentMap $buildEnvironment
        Add-InheritedEnvironmentRemovals `
            $buildEnvironment `
            '^(?:HTTP|HTTPS|ALL|NO)_PROXY$'
        Invoke-WithProcessEnvironment $buildEnvironment {
            Invoke-CheckedNative `
                $nodeCommand `
                @($nextCliPath, "build", $acceptanceWebDirectory) `
                "Production web build failed"
        }
        $buildEnvironment.Clear()
    }
    else {
        Write-Host "[3/7] Reusing the existing production Next.js build..."
    }
    Assert-Condition (Test-Path -LiteralPath $nextCliPath -PathType Leaf) `
        "The Next.js runtime is missing. Run pnpm install --frozen-lockfile first."
    Assert-Condition (Test-Path -LiteralPath $nextBuildIdPath -PathType Leaf) `
        "The production Next.js build was not found. Rerun without -SkipWebBuild."

    $databasePassword = [Guid]::NewGuid().ToString("N")
    $composeEnvironment = @{
        DOCKER_CONTEXT    = $null
        DOCKER_HOST       = $dockerEndpoint
        DOCKER_TLS_VERIFY = $null
        DOCKER_CERT_PATH  = $null
        POSTGRES_PORT     = [string] $postgresPort
        POSTGRES_DB       = "wsr_full_stack_acceptance"
        POSTGRES_USER     = "wsr_full_stack_acceptance"
        POSTGRES_PASSWORD = $databasePassword
    }

    Write-Host "[4/7] Starting an isolated PostgreSQL 17 Compose project..."
    $composeMayExist = $true
    Invoke-WithProcessEnvironment $composeEnvironment {
        Invoke-CheckedNative `
            $dockerCommand `
            @(
                "compose",
                "--project-name", $composeProject,
                "--file", $composePath,
                "--env-file", $safeEnvironmentPath,
                "up", "--detach", "--wait", "--wait-timeout", "90", "postgres"
            ) `
            "The isolated PostgreSQL service did not become healthy"
    }

    $jdbcUrl =
        "jdbc:postgresql://127.0.0.1:$postgresPort/wsr_full_stack_acceptance"
    $springApplicationJson = @{
        spring = @{
            profiles = @{
                active = "acceptance"
                include = @()
            }
            datasource = @{
                url = $jdbcUrl
                username = "wsr_full_stack_acceptance"
                password = $databasePassword
                "driver-class-name" = "org.postgresql.Driver"
            }
            flyway = @{
                enabled = $true
                url = $jdbcUrl
                user = "wsr_full_stack_acceptance"
                password = $databasePassword
                locations = "classpath:db/migration"
            }
        }
        server = @{
            address = "127.0.0.1"
            port = $apiPort
            ssl = @{ enabled = $false }
            tomcat = @{
                basedir = $tomcatBaseDirectory
                accesslog = @{
                    enabled = $true
                    buffered = $false
                    directory = "logs"
                    prefix = "full_stack_access"
                    suffix = ".log"
                    pattern = "%m %U%q %s"
                }
            }
        }
        app = @{
            providers = @{
                market = "fixture"
                analyst = "fixture"
            }
            "operator-api" = @{
                enabled = $false
                "token-sha256" = ""
            }
            "public-data" = @{
                sec = @{
                    enabled = $false
                    "base-url" = "http://127.0.0.1:1"
                    "contact-email" = ""
                }
            }
        }
        management = @{
            endpoints = @{
                web = @{
                    exposure = @{
                        include = "health,info"
                    }
                }
            }
            endpoint = @{
                env = @{ "show-values" = "never" }
                configprops = @{ "show-values" = "never" }
            }
        }
    } | ConvertTo-Json -Depth 12 -Compress
    $apiEnvironment = @{
        APP_ENV                       = "acceptance"
        DATA_MODE                     = "DEMO"
        SPRING_PROFILES_ACTIVE        = "acceptance"
        SPRING_MAIN_BANNER_MODE       = "off"
        LOGGING_LEVEL_ROOT            = "INFO"
        LOGGING_CONFIG                = $null
        LOGGING_FILE_NAME             = $null
        LOGGING_FILE_PATH             = $null
        LOG_FILE                      = $null
        LOG_PATH                      = $null
        SPRING_APPLICATION_JSON       = $springApplicationJson
        SPRING_CONFIG_LOCATION        = "classpath:/"
        SPRING_CONFIG_ADDITIONAL_LOCATION = $null
        SPRING_CONFIG_IMPORT          = $null
        SPRING_CONFIG_NAME            = "application"
        SPRING_PROFILES_INCLUDE       = $null
        SPRING_PROFILES_GROUP_ACCEPTANCE = $null
        WSR_LOCAL_ENV_FILE            =
            (Join-Path $temporaryDirectory "never-import-root-env")
        JAVA_TOOL_OPTIONS             = $null
        JDK_JAVA_OPTIONS              = $null
        _JAVA_OPTIONS                 = $null
        CLASSPATH                     = $null
        SERVER_ADDRESS                = "127.0.0.1"
        SERVER_PORT                   = [string] $apiPort
        SERVER_SSL_ENABLED            = "false"
        SERVER_TOMCAT_BASEDIR         = $tomcatBaseDirectory
        SERVER_TOMCAT_ACCESSLOG_ENABLED = "true"
        SERVER_TOMCAT_ACCESSLOG_BUFFERED = "false"
        SERVER_TOMCAT_ACCESSLOG_DIRECTORY = "logs"
        SERVER_TOMCAT_ACCESSLOG_PREFIX = "full_stack_access"
        SERVER_TOMCAT_ACCESSLOG_SUFFIX = ".log"
        SERVER_TOMCAT_ACCESSLOG_PATTERN = "%m %U%q %s"
        POSTGRES_HOST                 = "127.0.0.1"
        POSTGRES_PORT                 = [string] $postgresPort
        POSTGRES_DB                   = "wsr_full_stack_acceptance"
        POSTGRES_USER                 = "wsr_full_stack_acceptance"
        POSTGRES_PASSWORD             = $databasePassword
        SPRING_DATASOURCE_URL         = $jdbcUrl
        SPRING_DATASOURCE_USERNAME    = "wsr_full_stack_acceptance"
        SPRING_DATASOURCE_PASSWORD    = $databasePassword
        SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.postgresql.Driver"
        SPRING_FLYWAY_ENABLED         = "true"
        SPRING_FLYWAY_URL             = $jdbcUrl
        SPRING_FLYWAY_USER            = "wsr_full_stack_acceptance"
        SPRING_FLYWAY_PASSWORD        = $databasePassword
        SPRING_FLYWAY_LOCATIONS       = "classpath:db/migration"
        MARKET_PROVIDER               = "fixture"
        ANALYST_PROVIDER              = "fixture"
        APP_PROVIDERS_MARKET          = "fixture"
        APP_PROVIDERS_ANALYST         = "fixture"
        MACRO_PROVIDER                = "fixture"
        MEDIA_PROVIDER                = "fixture"
        SEC_PROVIDER_ENABLED          = "false"
        SEC_BASE_URL                  = "http://127.0.0.1:1"
        SEC_CONTACT_EMAIL             = ""
        SEC_LIVE_SMOKE                = "false"
        OPERATOR_API_ENABLED          = "false"
        OPERATOR_API_TOKEN_SHA256     = ""
        APP_OPERATOR_API_ENABLED      = "false"
        APP_OPERATOR_API_TOKEN_SHA256 = ""
        APP_PUBLIC_DATA_SEC_ENABLED   = "false"
        APP_PUBLIC_DATA_SEC_BASE_URL  = "http://127.0.0.1:1"
        APP_PUBLIC_DATA_SEC_CONTACT_EMAIL = ""
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,info"
        MANAGEMENT_ENDPOINT_ENV_SHOW_VALUES = "never"
        MANAGEMENT_ENDPOINT_CONFIGPROPS_SHOW_VALUES = "never"
        MANAGEMENT_SERVER_PORT        = $null
        MANAGEMENT_SERVER_ADDRESS     = $null
        MANAGEMENT_SERVER_SSL_ENABLED = $null
    }
    $apiEnvironment = ConvertTo-ProcessEnvironmentMap $apiEnvironment
    Add-InheritedEnvironmentRemovals `
        $apiEnvironment `
        '^(?:(?:SPRING|SERVER|MANAGEMENT|APP|POSTGRES|FLYWAY|MARKET|ANALYST|MACRO|MEDIA|SEC|OPERATOR|DATA|LOGGING|LOGBACK|LOG4J|JUL)(?:[_.-]|$)|DEBUG$|TRACE$)'
    $apiEnvironment["LOGGING_LEVEL_ROOT"] = "INFO"
    $apiEnvironment["DEBUG"] = "false"
    $apiEnvironment["TRACE"] = "false"
    $apiEnvironment["SPRING_MVC_LOG_REQUEST_DETAILS"] = "false"
    $apiEnvironment["SPRING_CODEC_LOG_REQUEST_DETAILS"] = "false"

    Write-Host "[5/7] Starting the loopback API and waiting for real PostgreSQL-backed health..."
    $apiStartParameters = @{
        FilePath               = $javaCommand
        ArgumentList           = @("-jar", ('"' + $jarPath + '"'))
        WorkingDirectory       = $apiDirectory
        PassThru               = $true
        RedirectStandardOutput = $apiStandardOutputPath
        RedirectStandardError  = $apiStandardErrorPath
    }
    if ($IsWindows) {
        $apiStartParameters.WindowStyle = "Hidden"
    }
    $apiProcess = Invoke-WithProcessEnvironment $apiEnvironment {
        Start-Process @apiStartParameters
    }

    $apiHttpHandler = [Net.Http.HttpClientHandler]::new()
    $apiHttpHandler.UseProxy = $false
    $apiHttpHandler.AllowAutoRedirect = $false
    $apiHttpClient = [Net.Http.HttpClient]::new($apiHttpHandler, $false)
    $apiHttpClient.BaseAddress = [Uri]::new("http://127.0.0.1:$apiPort")
    $apiHttpClient.Timeout = [TimeSpan]::FromSeconds(10)
    $apiDeadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $apiHealthy = $false
    while ([DateTimeOffset]::UtcNow -lt $apiDeadline) {
        $apiProcess.Refresh()
        if ($apiProcess.HasExited) {
            throw "The API process exited before becoming healthy."
        }
        try {
            $health = Invoke-HttpRequest $apiHttpClient "/actuator/health"
            if ($health.StatusCode -eq 200 -and $health.Content -match '"status"\s*:\s*"UP"') {
                $apiHealthy = $true
                break
            }
        }
        catch [Net.Http.HttpRequestException] {
            # Expected while the loopback socket is not accepting requests yet.
        }
        catch [Threading.Tasks.TaskCanceledException] {
            # Expected only while startup is still in progress.
        }
        Start-Sleep -Milliseconds 250
    }
    Assert-Condition $apiHealthy `
        "The API did not report UP before the startup timeout."

    $webLoopbackBaseUrl = "http://127.0.0.1:$webPort"
    $webEnvironment = @{
        CALL_AUDIT_PROVIDER     = "api"
        API_BASE_URL            = $apiBaseUrl
        NEXT_PUBLIC_DATA_MODE   = "DEMO"
        NEXT_PUBLIC_API_BASE_URL = ""
        MARKET_PROVIDER         = "fixture"
        ANALYST_PROVIDER        = "fixture"
        SP500_HISTORY_PROVIDER  = "fixture"
        MARKET_BOARD_PROVIDER   = "fixture"
        METHODOLOGY_PROVIDER    = "fixture"
        INSTITUTION_DIRECTORY_PROVIDER = "fixture"
        ANALYST_DIRECTORY_PROVIDER = "fixture"
        MARKET_MAP_PROVIDER     = "fixture"
        MARKET_TREEMAP_PROVIDER = "fixture"
        NEXT_TELEMETRY_DISABLED = "1"
        NODE_ENV               = "production"
        NODE_OPTIONS           = ""
        NODE_PATH              = $null
        NODE_USE_ENV_PROXY     = $null
        HTTP_PROXY             = $null
        HTTPS_PROXY            = $null
        ALL_PROXY              = $null
        NO_PROXY               = $null
        PORT                   = [string] $webPort
        HOSTNAME               = "127.0.0.1"
        CI                     = ""
    }
    $webEnvironment = ConvertTo-ProcessEnvironmentMap $webEnvironment
    Add-InheritedEnvironmentRemovals `
        $webEnvironment `
        '^(?:HTTP|HTTPS|ALL|NO)_PROXY$'

    Write-Host "[6/7] Starting production Next.js and smoke-checking every primary product route..."
    $webStartParameters = @{
        FilePath               = $nodeCommand
        ArgumentList           = @(
            ('"' + $nextCliPath + '"'),
            "start",
            "--hostname", "127.0.0.1",
            "--port", [string] $webPort
        )
        WorkingDirectory       = $acceptanceWebDirectory
        PassThru               = $true
        RedirectStandardOutput = $webStandardOutputPath
        RedirectStandardError  = $webStandardErrorPath
    }
    if ($IsWindows) {
        $webStartParameters.WindowStyle = "Hidden"
    }
    $webProcess = Invoke-WithProcessEnvironment $webEnvironment {
        Start-Process @webStartParameters
    }

    $webHttpHandler = [Net.Http.HttpClientHandler]::new()
    $webHttpHandler.UseProxy = $false
    $webHttpHandler.AllowAutoRedirect = $false
    $webHttpClient = [Net.Http.HttpClient]::new($webHttpHandler, $false)
    $webHttpClient.BaseAddress = [Uri]::new($webLoopbackBaseUrl)
    $webHttpClient.Timeout = [TimeSpan]::FromSeconds(30)
    $webDeadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $webHealthy = $false
    while ([DateTimeOffset]::UtcNow -lt $webDeadline) {
        $webProcess.Refresh()
        if ($webProcess.HasExited) {
            throw "The production web process exited before becoming ready."
        }
        try {
            $homeResponse = Invoke-HttpRequest $webHttpClient "/"
            if (
                $homeResponse.StatusCode -eq 200 -and
                $homeResponse.ContentType -eq "text/html" -and
                $homeResponse.Content.Contains("WALL STREET RECEIPTS")
            ) {
                $webHealthy = $true
                break
            }
        }
        catch [Net.Http.HttpRequestException] {
            # Expected while the production server is not accepting requests yet.
        }
        catch [Threading.Tasks.TaskCanceledException] {
            # Expected only while startup is still in progress.
        }
        Start-Sleep -Milliseconds 250
    }
    Assert-Condition $webHealthy `
        "The production web application did not become ready before the startup timeout."

    $primaryRoutes = @(
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
    foreach ($route in $primaryRoutes) {
        $page = Invoke-HttpRequest $webHttpClient $route
        Assert-Condition ($page.StatusCode -eq 200) `
            "Primary route $route returned HTTP $($page.StatusCode)."
        Assert-Condition ($page.ContentType -eq "text/html") `
            "Primary route $route did not return text/html."
        Assert-Condition ($page.Content.Contains("WALL STREET RECEIPTS")) `
            "Primary route $route did not render the shared product shell."
    }

    Write-Host "[7/7] Running focused real-browser API-mode checks and proving exact Spring reads..."
    $playwrightEnvironment = @{
        PLAYWRIGHT_BASE_URL     = $webLoopbackBaseUrl
        PLAYWRIGHT_EXTERNAL_SERVER = "true"
        PLAYWRIGHT_LOCAL_PRODUCTION_HTTP = "true"
        CALL_AUDIT_PROVIDER     = "api"
        API_BASE_URL            = $apiBaseUrl
        NEXT_PUBLIC_DATA_MODE   = "DEMO"
        NEXT_PUBLIC_API_BASE_URL = ""
        MARKET_PROVIDER         = "fixture"
        ANALYST_PROVIDER        = "fixture"
        SP500_HISTORY_PROVIDER  = "fixture"
        MARKET_BOARD_PROVIDER   = "fixture"
        METHODOLOGY_PROVIDER    = "fixture"
        INSTITUTION_DIRECTORY_PROVIDER = "fixture"
        ANALYST_DIRECTORY_PROVIDER = "fixture"
        MARKET_MAP_PROVIDER     = "fixture"
        MARKET_TREEMAP_PROVIDER = "fixture"
        PLAYWRIGHT_HTML_OPEN    = "never"
        NEXT_TELEMETRY_DISABLED = "1"
        NODE_OPTIONS            = ""
        NODE_PATH               = $null
        NODE_USE_ENV_PROXY      = $null
        HTTP_PROXY              = $null
        HTTPS_PROXY             = $null
        ALL_PROXY               = $null
        NO_PROXY                = $null
        NO_COLOR                = $null
        FORCE_COLOR             = $null
        CI                      = "true"
    }
    $playwrightEnvironment = ConvertTo-ProcessEnvironmentMap $playwrightEnvironment
    Add-InheritedEnvironmentRemovals `
        $playwrightEnvironment `
        '^(?:HTTP|HTTPS|ALL|NO)_PROXY$'
    Invoke-WithProcessEnvironment $playwrightEnvironment {
        Invoke-CheckedNative `
            $nodeCommand `
            @(
                $playwrightCliPath,
                "test",
                "call-revisions.spec.ts",
                "call-outcomes.spec.ts",
                "call-list-api.spec.ts",
                ("--config=" + $playwrightConfigPath),
                "--project=chromium-1280",
                "--workers=1",
                "--retries=0",
                "--reporter=line",
                ("--output=" + $playwrightOutputPath)
            ) `
            "Focused production full-stack browser acceptance failed"
    }

    $requiredAccessLines = @(
        "GET /v1/calls?dataMode=DEMO&page=0&size=25&sort=eventTime&order=desc 200",
        "GET /v1/calls?assetId=asset-nvda&ticker=nvda&institutionId=inst-gs&analystId=analyst-demo-b&direction=BULLISH&status=ACTIVE&dataMode=DEMO&from=2026-08-11T00%3A00%3A00.000Z&to=2026-08-12T00%3A00%3A00.000Z&page=0&size=1&sort=capturedAt&order=asc 200",
        "GET /v1/calls?dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?dataMode=DEMO&page=1&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls?ticker=TSLA&dataMode=DEMO&page=0&size=1&sort=eventTime&order=desc 200",
        "GET /v1/calls/demo-call-002- 200",
        "GET /v1/calls/demo-call-002/context- 200",
        "GET /v1/calls/demo-call-002/revisions- 200",
        "GET /v1/calls/demo-call-002/outcomes- 200",
        "GET /v1/calls/demo-call-001- 200",
        "GET /v1/calls/demo-call-001/context- 200",
        "GET /v1/calls/demo-call-001/revisions- 200",
        "GET /v1/calls/demo-call-001/outcomes- 200"
    )
    $accessLogDirectory = Join-Path $tomcatBaseDirectory "logs"
    $accessDeadline = [DateTimeOffset]::UtcNow.AddSeconds(10)
    $missingAccessLines = $requiredAccessLines
    do {
        $observedAccessLines = [Collections.Generic.HashSet[string]]::new(
            [StringComparer]::Ordinal
        )
        if (Test-Path -LiteralPath $accessLogDirectory -PathType Container) {
            foreach ($log in Get-ChildItem -LiteralPath $accessLogDirectory -Filter "full_stack_access*.log" -File) {
                foreach ($line in Get-Content -LiteralPath $log.FullName) {
                    if (-not [string]::IsNullOrWhiteSpace($line)) {
                        [void] $observedAccessLines.Add($line.Trim())
                    }
                }
            }
        }
        $missingAccessLines = @(
            $requiredAccessLines | Where-Object { -not $observedAccessLines.Contains($_) }
        )
        if ($missingAccessLines.Count -eq 0) {
            break
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTimeOffset]::UtcNow -lt $accessDeadline)
    Assert-Condition ($missingAccessLines.Count -eq 0) `
        ("Next did not exercise every required Spring resource: " + ($missingAccessLines -join "; "))

    $countQuery = @"
SELECT
    (SELECT count(*) FROM analyst_calls) || '|' ||
    (SELECT count(*) FROM analyst_call_revisions) || '|' ||
    (SELECT count(*) FROM call_outcomes);
"@
    $databaseCountResult = Invoke-WithProcessEnvironment $composeEnvironment {
        $output = & $dockerCommand compose `
            --project-name $composeProject `
            --file $composePath `
            --env-file $safeEnvironmentPath `
            exec --no-TTY postgres `
            psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 `
            --username wsr_full_stack_acceptance `
            --dbname wsr_full_stack_acceptance `
            --command $countQuery 2>&1
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output   = @($output)
        }
    }
    Assert-Condition ($databaseCountResult.ExitCode -eq 0) `
        "Could not inspect the disposable PostgreSQL call ledger."
    Assert-Condition ((($databaseCountResult.Output -join "").Trim()) -eq "3|2|4") `
        "The disposable PostgreSQL ledger did not contain exactly 3 calls, 2 revisions, and 4 outcomes."

    Write-Host "PASS: 12 production routes rendered through the isolated local stack."
    Write-Host "PASS: 3/3 focused Chromium checks used server-only API mode with no browser API call."
    Write-Host "PASS: all 13 exact Spring reads and PostgreSQL counts 3|2|4 were observed."
    Write-Host "PASS: SEC and operator boundaries remained disabled; no external provider was contacted."
}
catch {
    $logTail = Get-SanitizedLogTail `
        @(
            $apiStandardOutputPath,
            $apiStandardErrorPath,
            $webStandardOutputPath,
            $webStandardErrorPath
        ) `
        @([string] $databasePassword)
    $message = "Local full-stack acceptance check failed: $($_.Exception.Message)"
    if (-not [string]::IsNullOrWhiteSpace($logTail)) {
        $message += [Environment]::NewLine + "Sanitized process log tail:" + `
            [Environment]::NewLine + $logTail
    }
    $failure = [InvalidOperationException]::new($message, $_.Exception)
}
finally {
    if ($null -ne $webHttpClient) {
        $webHttpClient.Dispose()
    }
    if ($null -ne $webHttpHandler) {
        $webHttpHandler.Dispose()
    }
    if ($null -ne $apiHttpClient) {
        $apiHttpClient.Dispose()
    }
    if ($null -ne $apiHttpHandler) {
        $apiHttpHandler.Dispose()
    }

    if ($null -ne $webProcess) {
        try {
            Stop-OwnedProcess $webProcess "production web"
        }
        catch {
            $cleanupFailures.Add(
                "Could not stop the exact production web process: $($_.Exception.Message)"
            )
        }
    }
    if ($null -ne $apiProcess) {
        try {
            Stop-OwnedProcess $apiProcess "API"
        }
        catch {
            $cleanupFailures.Add(
                "Could not stop the exact API process: $($_.Exception.Message)"
            )
        }
    }

    if ($composeMayExist -and $null -ne $dockerCommand) {
        if ($composeProject -notmatch '^wsr-fullstack-[0-9]+-[a-f0-9]{8}$') {
            $cleanupFailures.Add("Refused cleanup for an unexpected Compose project name.")
        }
        else {
            try {
                $composeCleanupExitCode = Invoke-WithProcessEnvironment $composeEnvironment {
                    & $dockerCommand compose `
                        --project-name $composeProject `
                        --file $composePath `
                        --env-file $safeEnvironmentPath `
                        down --volumes --remove-orphans *> $null
                    return $LASTEXITCODE
                }
                if ($composeCleanupExitCode -ne 0) {
                    $cleanupFailures.Add(
                        "Could not remove isolated Compose project $composeProject."
                    )
                }
            }
            catch {
                $cleanupFailures.Add(
                    "Could not run cleanup for isolated Compose project ${composeProject}: $($_.Exception.Message)"
                )
            }
        }
    }

    if ($webMirrorOwned) {
        try {
            Remove-VerifiedWebMirrorDirectory `
                $webMirrorRoot `
                $webDirectory
            $webMirrorOwned = $false
        }
        catch {
            $cleanupFailures.Add($_.Exception.Message)
        }
    }

    if ($temporaryDirectoryOwned) {
        try {
            Remove-VerifiedTemporaryDirectory $temporaryDirectory
            $temporaryDirectoryOwned = $false
        }
        catch {
            $cleanupFailures.Add($_.Exception.Message)
        }
    }

    $databasePassword = $null
    $springApplicationJson = $null
    foreach ($environmentMap in @(
        $javaProbeEnvironment,
        $nodeProbeEnvironment,
        $mavenBuildEnvironment,
        $apiEnvironment,
        $webEnvironment,
        $playwrightEnvironment,
        $composeEnvironment,
        $dockerEnvironment
    )) {
        if ($null -ne $environmentMap) {
            $environmentMap.Clear()
        }
    }
    $apiEnvironment = $null
    $webEnvironment = $null
    $playwrightEnvironment = $null
    $composeEnvironment = $null
    $dockerEnvironment = $null
    $javaProbeEnvironment = $null
    $nodeProbeEnvironment = $null
    $mavenBuildEnvironment = $null

    if ($null -ne $repositoryLock) {
        try {
            Exit-RepositoryAcceptanceLock $repositoryLock $repositoryRoot
        }
        catch {
            $cleanupFailures.Add(
                "Could not release the repository acceptance lock: $($_.Exception.Message)"
            )
        }
        $repositoryLock = $null
    }
}

if ($cleanupFailures.Count -gt 0) {
    $cleanupMessage = $cleanupFailures -join [Environment]::NewLine
    if ($null -ne $failure) {
        throw [InvalidOperationException]::new(
            $failure.Message + [Environment]::NewLine + "Cleanup failures:" + `
                [Environment]::NewLine + $cleanupMessage,
            $failure
        )
    }
    throw "The acceptance checks passed, but cleanup failed:`n$cleanupMessage"
}

if ($null -ne $failure) {
    throw $failure
}

Write-Host "Cleanup complete: no test web/API process, Compose project, volume, source mirror, harness build, or temp report remains."
