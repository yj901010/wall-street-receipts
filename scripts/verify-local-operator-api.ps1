#Requires -Version 7.0

[CmdletBinding()]
param(
    [ValidateRange(30, 600)]
    [int] $StartupTimeoutSeconds = 120,

    [switch] $SkipPackage
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
        [hashtable] $Variables,

        [Parameter(Mandatory)]
        [scriptblock] $Action
    )

    $previous = @{}
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

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory)]
        [Net.Http.HttpClient] $Client,

        [Parameter(Mandatory)]
        [Net.Http.HttpMethod] $Method,

        [Parameter(Mandatory)]
        [string] $Path,

        [AllowNull()]
        [string] $JsonBody,

        [AllowNull()]
        [string] $BearerToken
    )

    $request = [Net.Http.HttpRequestMessage]::new($Method, $Path)
    $response = $null
    try {
        if ($null -ne $BearerToken) {
            $request.Headers.Authorization =
                [Net.Http.Headers.AuthenticationHeaderValue]::new(
                    "Bearer",
                    $BearerToken
                )
        }
        if ($null -ne $JsonBody) {
            $request.Content = [Net.Http.StringContent]::new(
                $JsonBody,
                [Text.Encoding]::UTF8,
                "application/json"
            )
        }

        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $cacheControl = if ($null -eq $response.Headers.CacheControl) {
            $null
        }
        else {
            $response.Headers.CacheControl.ToString()
        }
        $location = if ($null -eq $response.Headers.Location) {
            $null
        }
        else {
            $response.Headers.Location.OriginalString
        }
        $contentType = if ($null -eq $response.Content.Headers.ContentType) {
            $null
        }
        else {
            $response.Content.Headers.ContentType.MediaType
        }

        return [pscustomobject]@{
            StatusCode   = [int] $response.StatusCode
            Content      = $content
            CacheControl = $cacheControl
            Location     = $location
            ContentType  = $contentType
        }
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $request.Dispose()
    }
}

function ConvertFrom-RequiredJson {
    param(
        [Parameter(Mandatory)]
        [string] $Content,

        [Parameter(Mandatory)]
        [string] $Context
    )

    try {
        return $Content | ConvertFrom-Json
    }
    catch {
        throw "$Context did not return valid JSON."
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
            Get-Content -LiteralPath $path -Tail 40
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
    $comparison = if ($env:OS -eq "Windows_NT") {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }

    Assert-Condition `
        ($resolvedPath.StartsWith($temporaryRootWithSeparator, $comparison)) `
        "Refusing to remove a directory outside the operating-system temp root."
    Assert-Condition `
        ($leaf -match '^wsr-local-operator-[a-f0-9]{12}$') `
        "Refusing to remove an unexpected temporary directory."

    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$apiDirectory = Join-Path $repositoryRoot "apps/api"
$composePath = Join-Path $repositoryRoot "compose.yaml"
$safeEnvironmentPath = Join-Path $repositoryRoot ".env.example"
$mavenWrapper = if ($env:OS -eq "Windows_NT") {
    Join-Path $apiDirectory "mvnw.cmd"
}
else {
    Join-Path $apiDirectory "mvnw"
}
$jarPath = Join-Path $apiDirectory "target/wall-street-receipts-api-0.0.1-SNAPSHOT.jar"
$temporaryDirectory = Join-Path (
    [IO.Path]::GetTempPath()
) ("wsr-local-operator-" + [Guid]::NewGuid().ToString("N").Substring(0, 12))
$apiStandardOutputPath = Join-Path $temporaryDirectory "api.stdout.log"
$apiStandardErrorPath = Join-Path $temporaryDirectory "api.stderr.log"
$composeProject =
    "wsr-operator-" + $PID + "-" + [Guid]::NewGuid().ToString("N").Substring(0, 8)

$dockerCommand = $null
$javaCommand = $null
$mavenCommand = $null
$mavenArgumentPrefix = @()
$apiProcess = $null
$httpClient = $null
$composeMayExist = $false
$operatorBytes = $null
$operatorTokenBytes = $null
$operatorDigestBytes = $null
$operatorToken = $null
$operatorDigest = $null
$databasePassword = $null
$httpHandler = $null
$composeEnvironment = $null
$apiEnvironment = $null
$springApplicationJson = $null
$failure = $null
$cleanupFailures = [Collections.Generic.List[string]]::new()

try {
    Write-Host "[1/5] Checking Java 21, Docker, and repository prerequisites..."
    Assert-Condition (Test-Path -LiteralPath $composePath -PathType Leaf) `
        "compose.yaml was not found at the repository root."
    Assert-Condition (Test-Path -LiteralPath $safeEnvironmentPath -PathType Leaf) `
        ".env.example was not found at the repository root."
    Assert-Condition (Test-Path -LiteralPath $mavenWrapper -PathType Leaf) `
        "The Maven wrapper was not found under apps/api."

    $dockerCommand = (
        Get-Command docker -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    ).Source
    $javaCommand = (
        Get-Command java -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    ).Source
    if ($env:OS -eq "Windows_NT") {
        $mavenCommand = $mavenWrapper
    }
    else {
        $mavenCommand = (
            Get-Command sh -CommandType Application -ErrorAction Stop |
                Select-Object -First 1
        ).Source
        $mavenArgumentPrefix = @($mavenWrapper)
    }
    $javaVersionOutput = (& $javaCommand -version 2>&1) -join " "
    Assert-Condition ($javaVersionOutput -match 'version "21(?:\.|\")') `
        "Java 21 is required for the local operator acceptance check."

    $dockerInfo = & $dockerCommand info --format '{{.ServerVersion}}' 2>&1
    Assert-Condition ($LASTEXITCODE -eq 0) `
        "Docker is installed but its daemon is unavailable. Start Docker Desktop and retry."
    Assert-Condition (-not [string]::IsNullOrWhiteSpace(($dockerInfo -join ""))) `
        "Docker did not report a server version."
    & $dockerCommand compose version *> $null
    Assert-Condition ($LASTEXITCODE -eq 0) `
        "Docker Compose v2 is required."

    if (-not $SkipPackage) {
        Write-Host "[2/5] Packaging the API without rerunning the unit suite..."
        $packageArguments = $mavenArgumentPrefix + @(
            "-B",
            "-ntp",
            "-f", (Join-Path $apiDirectory "pom.xml"),
            "-DskipTests",
            "package"
        )
        Invoke-CheckedNative `
            $mavenCommand `
            $packageArguments `
            "API packaging failed"
    }
    else {
        Write-Host "[2/5] Reusing the existing packaged API..."
    }
    Assert-Condition (Test-Path -LiteralPath $jarPath -PathType Leaf) `
        "The packaged API JAR was not found. Rerun without -SkipPackage."

    $postgresPort = Get-FreeLoopbackPort
    do {
        $apiPort = Get-FreeLoopbackPort
    } while ($apiPort -eq $postgresPort)

    $operatorRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $operatorBytes = [byte[]]::new(32)
        $operatorRandom.GetBytes($operatorBytes)
    }
    finally {
        $operatorRandom.Dispose()
    }
    $operatorToken = [Convert]::ToBase64String($operatorBytes)
    $operatorTokenBytes = [Text.Encoding]::ASCII.GetBytes($operatorToken)
    $operatorSha = [Security.Cryptography.SHA256]::Create()
    try {
        $operatorDigestBytes = $operatorSha.ComputeHash($operatorTokenBytes)
    }
    finally {
        $operatorSha.Dispose()
    }
    $operatorDigest = -join (
        $operatorDigestBytes | ForEach-Object { $_.ToString("x2") }
    )
    Assert-Condition `
        ($operatorToken.Length -eq 44 -and $operatorToken.EndsWith("=")) `
        "The temporary operator token was not canonical Base64 for 32 bytes."
    Assert-Condition ($operatorDigest -match '^[0-9a-f]{64}$') `
        "The temporary operator-token digest was not canonical lowercase SHA-256."

    $databasePassword = [Guid]::NewGuid().ToString("N")
    $composeEnvironment = @{
        POSTGRES_PORT     = [string] $postgresPort
        POSTGRES_DB       = "wsr_operator_acceptance"
        POSTGRES_USER     = "wsr_operator_acceptance"
        POSTGRES_PASSWORD = $databasePassword
    }

    Write-Host "[3/5] Starting an isolated PostgreSQL 17 Compose project..."
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

    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    $jdbcUrl =
        "jdbc:postgresql://127.0.0.1:$postgresPort/wsr_operator_acceptance"
    $springApplicationJson = @{
        spring = @{
            profiles = @{
                active = "acceptance"
                include = @()
            }
            datasource = @{
                url = $jdbcUrl
                username = "wsr_operator_acceptance"
                password = $databasePassword
                "driver-class-name" = "org.postgresql.Driver"
            }
            flyway = @{
                enabled = $true
                url = $jdbcUrl
                user = "wsr_operator_acceptance"
                password = $databasePassword
                locations = "classpath:db/migration"
            }
        }
        server = @{
            address = "127.0.0.1"
            port = $apiPort
            ssl = @{ enabled = $false }
        }
        app = @{
            "operator-api" = @{
                enabled = $true
                "token-sha256" = $operatorDigest
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
    } | ConvertTo-Json -Depth 10 -Compress
    $apiEnvironment = @{
        SPRING_PROFILES_ACTIVE      = "acceptance"
        SPRING_MAIN_BANNER_MODE     = "off"
        LOGGING_LEVEL_ROOT          = "INFO"
        SPRING_APPLICATION_JSON     = $springApplicationJson
        SPRING_CONFIG_LOCATION      = $null
        SPRING_CONFIG_ADDITIONAL_LOCATION = $null
        SPRING_CONFIG_IMPORT        = $null
        SPRING_CONFIG_NAME          = "application"
        SPRING_PROFILES_INCLUDE     = $null
        SPRING_PROFILES_GROUP_ACCEPTANCE = $null
        WSR_LOCAL_ENV_FILE          =
            (Join-Path $temporaryDirectory "never-import-root-env")
        JAVA_TOOL_OPTIONS           = $null
        JDK_JAVA_OPTIONS            = $null
        _JAVA_OPTIONS               = $null
        SERVER_ADDRESS              = "127.0.0.1"
        SERVER_PORT                 = [string] $apiPort
        SERVER_SSL_ENABLED          = "false"
        POSTGRES_HOST               = "127.0.0.1"
        POSTGRES_PORT               = [string] $postgresPort
        POSTGRES_DB                 = "wsr_operator_acceptance"
        POSTGRES_USER               = "wsr_operator_acceptance"
        POSTGRES_PASSWORD           = $databasePassword
        SPRING_DATASOURCE_URL       = $jdbcUrl
        SPRING_DATASOURCE_USERNAME  = "wsr_operator_acceptance"
        SPRING_DATASOURCE_PASSWORD  = $databasePassword
        SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.postgresql.Driver"
        SPRING_FLYWAY_ENABLED       = "true"
        SPRING_FLYWAY_URL           = $jdbcUrl
        SPRING_FLYWAY_USER          = "wsr_operator_acceptance"
        SPRING_FLYWAY_PASSWORD      = $databasePassword
        SPRING_FLYWAY_LOCATIONS     = "classpath:db/migration"
        MARKET_PROVIDER             = "fixture"
        ANALYST_PROVIDER            = "fixture"
        SEC_PROVIDER_ENABLED        = "false"
        SEC_BASE_URL                = "http://127.0.0.1:1"
        SEC_CONTACT_EMAIL           = ""
        SEC_LIVE_SMOKE              = "false"
        OPERATOR_API_ENABLED        = "true"
        OPERATOR_API_TOKEN_SHA256   = $operatorDigest
        APP_OPERATOR_API_ENABLED    = "true"
        APP_OPERATOR_API_TOKEN_SHA256 = $operatorDigest
        APP_PUBLIC_DATA_SEC_ENABLED = "false"
        APP_PUBLIC_DATA_SEC_BASE_URL = "http://127.0.0.1:1"
        APP_PUBLIC_DATA_SEC_CONTACT_EMAIL = ""
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = "health,info"
        MANAGEMENT_ENDPOINT_ENV_SHOW_VALUES = "never"
        MANAGEMENT_ENDPOINT_CONFIGPROPS_SHOW_VALUES = "never"
        MANAGEMENT_SERVER_PORT      = $null
        MANAGEMENT_SERVER_ADDRESS   = $null
        MANAGEMENT_SERVER_SSL_ENABLED = $null
    }

    Write-Host "[4/5] Starting the loopback-only API and waiting for health..."
    $startProcessParameters = @{
        FilePath               = $javaCommand
        ArgumentList           = @("-jar", ('"' + $jarPath + '"'))
        WorkingDirectory       = $apiDirectory
        PassThru               = $true
        RedirectStandardOutput = $apiStandardOutputPath
        RedirectStandardError  = $apiStandardErrorPath
    }
    if ($env:OS -eq "Windows_NT") {
        $startProcessParameters.WindowStyle = "Hidden"
    }
    $apiProcess = Invoke-WithProcessEnvironment $apiEnvironment {
        Start-Process @startProcessParameters
    }

    $httpHandler = [Net.Http.HttpClientHandler]::new()
    $httpHandler.UseProxy = $false
    $httpHandler.AllowAutoRedirect = $false
    $httpClient = [Net.Http.HttpClient]::new($httpHandler, $false)
    $httpClient.BaseAddress = [Uri]::new("http://127.0.0.1:$apiPort")
    $httpClient.Timeout = [TimeSpan]::FromSeconds(10)
    $startupDeadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $healthy = $false
    while ([DateTimeOffset]::UtcNow -lt $startupDeadline) {
        $apiProcess.Refresh()
        if ($apiProcess.HasExited) {
            throw "The API process exited before becoming healthy."
        }
        try {
            $health = Invoke-JsonRequest `
                $httpClient `
                ([Net.Http.HttpMethod]::Get) `
                "/actuator/health" `
                $null `
                $null
            if ($health.StatusCode -eq 200) {
                $healthJson = ConvertFrom-RequiredJson $health.Content "Health endpoint"
                if ($healthJson.status -eq "UP") {
                    $healthy = $true
                    break
                }
            }
        }
        catch [Net.Http.HttpRequestException] {
            # Expected while the local server socket is not accepting requests yet.
        }
        catch [Threading.Tasks.TaskCanceledException] {
            # Expected only while startup is still in progress.
        }
        Start-Sleep -Milliseconds 250
    }
    Assert-Condition $healthy `
        "The API did not report UP before the startup timeout."

    Write-Host "[5/5] Exercising authentication, replay, conflict, and status contracts..."
    $rootPath = "/internal/v1/sec/collection-attempts/root"
    $attemptPathPrefix = "/internal/v1/sec/collection-attempts/"
    $unauthenticatedBody = @{
        operatorRequestId = [Guid]::NewGuid().ToString("D").ToLowerInvariant()
        cik = "320193"
    } | ConvertTo-Json -Compress
    $unauthenticated = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Post) `
        $rootPath `
        $unauthenticatedBody `
        $null
    $unauthenticatedJson = ConvertFrom-RequiredJson `
        $unauthenticated.Content `
        "Unauthenticated operator request"
    Assert-Condition ($unauthenticated.StatusCode -eq 401) `
        "The unauthenticated operator request was not rejected with 401."
    Assert-Condition `
        ($unauthenticatedJson.code -eq "OPERATOR_AUTHENTICATION_REQUIRED") `
        "The unauthenticated operator request returned an unexpected problem code."
    Assert-Condition ($unauthenticated.CacheControl -match '(^|,)\s*no-store(\s*|,|$)') `
        "The authentication problem response was cacheable."

    $operatorRequestId = [Guid]::NewGuid().ToString("D").ToLowerInvariant()
    $rootBody = @{
        operatorRequestId = $operatorRequestId
        cik = "320193"
    } | ConvertTo-Json -Compress
    $first = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Post) `
        $rootPath `
        $rootBody `
        $operatorToken
    $firstJson = ConvertFrom-RequiredJson $first.Content "Initial root attempt"
    Assert-Condition ($first.StatusCode -eq 200) `
        "The initial authenticated root attempt did not return 200."
    Assert-Condition ($first.CacheControl -match '(^|,)\s*no-store(\s*|,|$)') `
        "The initial root attempt response was cacheable."
    Assert-Condition ($firstJson.attemptId -match '^[0-9a-f]{64}$') `
        "The initial root attempt did not return a canonical attempt ID."
    Assert-Condition ($firstJson.operatorRequestId -eq $operatorRequestId) `
        "The initial root attempt changed the operator request ID."
    Assert-Condition ($firstJson.cik -eq "0000320193") `
        "The initial root attempt did not canonicalize the CIK."
    Assert-Condition ($firstJson.lifecycleState -eq "TERMINAL_FAILED_KNOWN") `
        "The provider-disabled attempt did not close in the expected lifecycle state."
    Assert-Condition `
        ($firstJson.terminalOutcome.failureCode -eq "PROVIDER_GATE_CLOSED") `
        "The provider-disabled attempt did not expose PROVIDER_GATE_CLOSED."
    Assert-Condition `
        ($firstJson.terminalOutcome.requestDisposition -eq "PROVIDER_INVOCATION_NOT_STARTED") `
        "The provider-disabled attempt claimed an unexpected request disposition."
    Assert-Condition ($null -eq $firstJson.providerDispatch) `
        "The provider-disabled attempt incorrectly recorded provider dispatch."
    Assert-Condition ($firstJson.automaticRetryAllowed -eq $false) `
        "The provider-disabled attempt incorrectly allowed automatic retry."
    Assert-Condition ($first.Location -eq ($attemptPathPrefix + $firstJson.attemptId)) `
        "The initial root attempt returned an unexpected Location header."

    $replay = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Post) `
        $rootPath `
        $rootBody `
        $operatorToken
    Assert-Condition ($replay.StatusCode -eq 200) `
        "The exact replay did not return 200."
    Assert-Condition ($replay.Content -ceq $first.Content) `
        "The exact replay did not return the immutable original representation."
    Assert-Condition ($replay.Location -eq $first.Location) `
        "The exact replay changed the Location header."

    $status = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Get) `
        ($attemptPathPrefix + $firstJson.attemptId) `
        $null `
        $operatorToken
    Assert-Condition ($status.StatusCode -eq 200) `
        "The exact status lookup did not return 200."
    Assert-Condition ($status.Content -ceq $first.Content) `
        "The status lookup did not reconstruct the immutable representation."
    Assert-Condition ($null -eq $status.Location) `
        "The status lookup unexpectedly returned a Location header."
    Assert-Condition ($status.CacheControl -match '(^|,)\s*no-store(\s*|,|$)') `
        "The status response was cacheable."

    $conflictBody = @{
        operatorRequestId = $operatorRequestId
        cik = "1"
    } | ConvertTo-Json -Compress
    $conflict = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Post) `
        $rootPath `
        $conflictBody `
        $operatorToken
    $conflictJson = ConvertFrom-RequiredJson $conflict.Content "Conflicting replay"
    Assert-Condition ($conflict.StatusCode -eq 409) `
        "The changed command replay was not rejected with 409."
    Assert-Condition ($conflictJson.code -eq "OPERATOR_REQUEST_CONFLICT") `
        "The changed command replay returned an unexpected problem code."
    Assert-Condition ($conflict.CacheControl -match '(^|,)\s*no-store(\s*|,|$)') `
        "The conflict response was cacheable."

    $missingEvidenceBody = @{
        operatorRequestId = [Guid]::NewGuid().ToString("D").ToLowerInvariant()
        rootCaptureId = "f" * 64
        descriptorActions = @()
    } | ConvertTo-Json -Compress
    $missingEvidence = Invoke-JsonRequest `
        $httpClient `
        ([Net.Http.HttpMethod]::Post) `
        "/internal/v1/sec/collection-attempts/exact-root" `
        $missingEvidenceBody `
        $operatorToken
    $missingEvidenceJson = ConvertFrom-RequiredJson `
        $missingEvidence.Content `
        "Missing exact evidence request"
    Assert-Condition ($missingEvidence.StatusCode -eq 422) `
        "Missing exact evidence was not rejected with 422."
    Assert-Condition ($missingEvidenceJson.code -eq "EXACT_EVIDENCE_NOT_ADMITTED") `
        "Missing exact evidence returned an unexpected problem code."
    Assert-Condition ($missingEvidence.CacheControl -match '(^|,)\s*no-store(\s*|,|$)') `
        "The exact-evidence rejection was cacheable."

    $countQuery = @"
SELECT
    (SELECT count(*) FROM sec_filing_collection_attempts) || '|' ||
    (SELECT count(*) FROM sec_filing_collection_attempt_provider_dispatches) || '|' ||
    (SELECT count(*) FROM sec_filing_collection_attempt_outcomes);
"@
    $databaseCounts = & $dockerCommand compose `
        --project-name $composeProject `
        --file $composePath `
        --env-file $safeEnvironmentPath `
        exec --no-TTY postgres `
        psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 `
        --username wsr_operator_acceptance `
        --dbname wsr_operator_acceptance `
        --command $countQuery 2>&1
    Assert-Condition ($LASTEXITCODE -eq 0) `
        "Could not inspect the disposable PostgreSQL ledger."
    Assert-Condition ((($databaseCounts -join "").Trim()) -eq "1|0|1") `
        "The disposable PostgreSQL ledger did not contain exactly one attempt, zero dispatches, and one outcome."

    Write-Host "PASS: isolated PostgreSQL 17, loopback API, authentication, durable status,"
    Write-Host "      exact replay, conflict, and evidence-admission contracts are coherent."
    Write-Host "PASS: SEC provider traffic remained disabled and pointed at a closed loopback origin."
}
catch {
    $sensitiveValues = @(
        [string] $operatorToken,
        [string] $operatorDigest,
        [string] $databasePassword
    )
    $logTail = Get-SanitizedLogTail `
        @($apiStandardOutputPath, $apiStandardErrorPath) `
        $sensitiveValues
    $message = "Local operator acceptance check failed: $($_.Exception.Message)"
    if (-not [string]::IsNullOrWhiteSpace($logTail)) {
        $message += [Environment]::NewLine + "Sanitized API log tail:" + `
            [Environment]::NewLine + $logTail
    }
    $failure = [InvalidOperationException]::new($message, $_.Exception)
}
finally {
    if ($null -ne $httpClient) {
        $httpClient.Dispose()
    }
    if ($null -ne $httpHandler) {
        $httpHandler.Dispose()
    }

    if ($null -ne $apiProcess) {
        try {
            $apiProcess.Refresh()
            if (-not $apiProcess.HasExited) {
                Stop-Process -Id $apiProcess.Id -Force
                $stopped = $apiProcess.WaitForExit(10000)
                if (-not $stopped) {
                    throw "The exact API process did not exit within 10 seconds."
                }
            }
            $apiProcess.Dispose()
        }
        catch {
            $cleanupFailures.Add("Could not stop the exact API process: $($_.Exception.Message)")
        }
    }

    if ($composeMayExist -and $null -ne $dockerCommand) {
        if ($composeProject -notmatch '^wsr-operator-[0-9]+-[a-f0-9]{8}$') {
            $cleanupFailures.Add("Refused cleanup for an unexpected Compose project name.")
        }
        else {
            & $dockerCommand compose `
                --project-name $composeProject `
                --file $composePath `
                --env-file $safeEnvironmentPath `
                down --volumes --remove-orphans *> $null
            if ($LASTEXITCODE -ne 0) {
                $cleanupFailures.Add(
                    "Could not remove isolated Compose project $composeProject."
                )
            }
        }
    }

    try {
        Remove-VerifiedTemporaryDirectory $temporaryDirectory
    }
    catch {
        $cleanupFailures.Add($_.Exception.Message)
    }

    if ($null -ne $operatorBytes) {
        [Array]::Clear($operatorBytes, 0, $operatorBytes.Length)
    }
    if ($null -ne $operatorDigestBytes) {
        [Array]::Clear($operatorDigestBytes, 0, $operatorDigestBytes.Length)
    }
    if ($null -ne $operatorTokenBytes) {
        [Array]::Clear($operatorTokenBytes, 0, $operatorTokenBytes.Length)
    }
    $operatorToken = $null
    $operatorDigest = $null
    $databasePassword = $null
    $springApplicationJson = $null
    if ($null -ne $apiEnvironment) {
        $apiEnvironment.Clear()
        $apiEnvironment = $null
    }
    if ($null -ne $composeEnvironment) {
        $composeEnvironment.Clear()
        $composeEnvironment = $null
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

Write-Host "Cleanup complete: no test API process, Compose project, volume, or token file remains."
