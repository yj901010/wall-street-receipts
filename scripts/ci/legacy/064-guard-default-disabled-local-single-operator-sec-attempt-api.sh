python <<'PYTHON'
import re
import xml.etree.ElementTree as ET
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def compact(source):
    return re.sub(r"\s+", " ", source).strip()

def powershell_function(source, name):
    start = source.index(f"function {name} {{")
    next_function = source.find("\nfunction ", start + len(name))
    end = len(source) if next_function == -1 else next_function
    return source[start:end].strip()

def environment_section(source, start_marker, end_marker):
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[start:end]

def require_exact_assignments(source, assignments, context):
    require(
        all(
            len(re.findall(
                rf"(?m)^\s*{re.escape(name)}\s*=\s*{re.escape(value)}\s*$",
                source,
            )) == 1
            for name, value in assignments.items()
        ),
        f"{context} environment sanitization changed",
    )

def markdown_section(source, start_marker, end_marker):
    start = source.index(start_marker)
    end = source.index(end_marker, start + len(start_marker))
    return source[start:end]

def workflow_step(source, name):
    marker = f"\n      - name: {name}\n"
    start = source.index(marker)
    end = source.index("\n      - name: ", start + len(marker))
    return start, end, source[start:end]

def remove_workflow_step(source, name):
    start, end, _ = workflow_step(source, name)
    return source[:start] + source[end:]

adr_path = Path(
    "decisions/ADR-043-default-disabled-local-single-operator-sec-attempt-api.md"
)
adr044_path = Path(
    "decisions/ADR-044-disposable-offline-local-operator-api-acceptance-harness.md"
)
harness_path = Path("scripts/verify-local-operator-api.ps1")
full_stack_harness_path = Path("scripts") / "verify-local-full-stack.ps1"
gitignore_path = Path(".gitignore")
document_paths = {
    adr_path,
    adr044_path,
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
}
require(
    all(path.is_file() for path in document_paths)
    and harness_path.is_file()
    and full_stack_harness_path.is_file()
    and gitignore_path.is_file(),
    "Missing ADR-043/ADR-044 decision, README, script, or implementation-log surface",
)
documents = {
    path: path.read_text(encoding="utf-8") for path in document_paths
}
adr = documents[adr_path]
adr044 = documents[adr044_path]
require(
    adr.startswith(
        "# ADR-043 — Default-Disabled Local Single-Operator SEC Attempt API\n\n"
        "- Status: Accepted\n- Date: 2026-08-26\n"
    )
    and all("ADR-043" in documents[path] for path in document_paths),
    "ADR-043 title, accepted status, date, or documentation parity changed",
)
require(
    adr044.startswith(
        "# ADR-044 — Disposable Offline Local Operator API Acceptance Harness\n\n"
        "- Status: Accepted\n- Date: 2026-08-26\n"
    )
    and all(
        "ADR-044" in documents[path]
        for path in document_paths
        if path != adr_path
    ),
    "ADR-044 title, accepted status, date, or documentation parity changed",
)
required_adr_terms = (
    "OPERATOR_API_ENABLED=true",
    "OPERATOR_API_TOKEN_SHA256=<lowercase SHA-256 of one random local Bearer token>",
    "defaults to `false`",
    "ordinary route handling returns `404`",
    "constant-time byte comparison",
    "InetAddress.getLoopbackAddress()",
    "POST /internal/v1/sec/collection-attempts/root",
    "POST /internal/v1/sec/collection-attempts/exact-root",
    "GET /internal/v1/sec/collection-attempts/{attemptId}",
    "automaticRetryAllowed` is always false",
    "SEC_PROVIDER_ENABLED=false",
    "No SEC API key",
    "exactly one API JVM/container/replica",
    "prohibits enabling this opaque-token boundary in a deployed",
)
require(
    all(term in compact(adr) for term in required_adr_terms),
    "ADR-043 local-only, default-off, exact-route, or no-retry semantics changed",
)

harness_command = (
    "pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1"
)
required_adr044_terms = (
    "scripts/verify-local-operator-api.ps1",
    harness_command,
    "Java 21, Docker with Compose v2, and PowerShell 7",
    "platform's POSIX `sh`; Windows uses `mvnw.cmd`",
    "The root `.env`, the default Compose project, its `postgres-data` volume",
    "OPERATOR_API_ENABLED=true",
    "SEC_PROVIDER_ENABLED=false",
    "SEC_BASE_URL=http://127.0.0.1:1",
    "No domain, DNS change, API key, SEC account, paid plan, OAuth client",
    "401 OPERATOR_AUTHENTICATION_REQUIRED",
    "409 OPERATOR_REQUEST_CONFLICT",
    "422 EXACT_EVIDENCE_NOT_ADMITTED",
    "one attempt, zero provider dispatches, and one terminal outcome",
    "atomically creates and exclusively holds the root `/.wsr-local-acceptance.lock`",
    "harness-owned temporary directory",
    "A hard-terminated owner deliberately leaves the ignored lock file behind",
    "A remote endpoint is rejected before `docker info`",
    "CI parses the PowerShell source and statically guards",
    "It does not execute this additional composed harness",
)
readme_acceptance = markdown_section(
    documents[Path("README.md")],
    "ADR-044 adds the preferred one-command pre-deployment acceptance gate",
    "ADR-022 remains the sole shared receipt",
)
api_operator_section = markdown_section(
    documents[Path("apps/api/README.md")],
    "### Local single-operator attempt API",
    "### Manual SEC live smoke",
)
log_scope = markdown_section(
    documents[Path("IMPLEMENTATION_LOG.md")],
    "## 2026-08-26 — ADR-044 disposable offline local operator acceptance harness",
    "### Operator requirements",
)
require(
    all(term in compact(adr044) for term in required_adr044_terms)
    and documents[Path("README.md")].count(harness_command) == 1
    and harness_command in api_operator_section
    and all(compact(term) in compact(readme_acceptance) for term in (
        "validated temporary directory",
        "fail-fast atomic root lock",
        "reject remote Docker endpoints before daemon contact",
        "pin the validated local endpoint for their remaining Docker operations",
    ))
    and all(compact(term) in compact(api_operator_section) for term in (
        "POSIX `sh`",
        "전용 temp build directory",
        "packaged `classpath:/`",
        "daemon 접촉 전에 거부",
        "/.wsr-local-acceptance.lock",
    ))
    and all(compact(term) in compact(log_scope) for term in (
        "standard POSIX `sh`",
        "validated harness temp directory",
        "atomically created root `/.wsr-local-acceptance.lock`",
        "Reject a remote Docker endpoint before daemon contact",
        "Keep Tomcat's base under the validated temp directory",
        "Fix `SPRING_CONFIG_LOCATION=classpath:/`",
    )),
    "ADR-044 disposable topology, offline boundary, acceptance matrix, or local command changed",
)

acceptance_script_paths = {
    path for path in Path("scripts").glob("verify-local-*.ps1")
    if path.is_file()
}
require(
    acceptance_script_paths == {harness_path, full_stack_harness_path},
    "ADR-044/ADR-045 must remain the exact two-script local acceptance surface",
)
harness = harness_path.read_text(encoding="utf-8")
required_harness_markers = (
    "#Requires -Version 7.0",
    "[ValidateRange(30, 600)]",
    "[switch] $SkipPackage",
    "Set-StrictMode -Version Latest",
    "$IsWindows",
    "function Invoke-WithProcessEnvironment",
    "function Get-ProcessEnvironmentNameComparer",
    "function ConvertTo-ProcessEnvironmentMap",
    "function Add-InheritedEnvironmentRemovals",
    "[StringComparer]::OrdinalIgnoreCase",
    "[StringComparer]::Ordinal",
    "[Collections.Generic.Dictionary[string, object]]::new(",
    "-not $Variables.ContainsKey($environmentName)",
    "function Enter-RepositoryAcceptanceLock",
    "function Exit-RepositoryAcceptanceLock",
    'Join-Path $resolvedRepository ".wsr-local-acceptance.lock"',
    "[IO.FileMode]::CreateNew",
    "[IO.FileAccess]::Write",
    "[IO.FileShare]::None",
    "$lockStream.Flush($true)",
    'harness       = $HarnessId',
    'runId         = $RunId',
    'processId     = $PID',
    "$Lock.Stream.Dispose()",
    "Remove-Item -LiteralPath $expectedPath -Force",
    "The repository acceptance lock file remained after release.",
    "function Get-SelectedDockerEndpoint",
    "function Assert-LocalDockerEndpoint",
    '"context", "inspect"',
    "The selected Docker endpoint is not local.",
    "$dockerEndpoint = Get-SelectedDockerEndpoint $dockerCommand",
    "Assert-LocalDockerEndpoint $dockerEndpoint",
    "$dockerEnvironment = @{",
    "DOCKER_HOST       = $dockerEndpoint",
    "[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)",
    "[Security.Cryptography.RandomNumberGenerator]::Create()",
    "[byte[]]::new(32)",
    "[Convert]::ToBase64String($operatorBytes)",
    "$operatorTokenBytes = [Text.Encoding]::ASCII.GetBytes($operatorToken)",
    "$operatorSha = [Security.Cryptography.SHA256]::Create()",
    "$operatorDigestBytes = $operatorSha.ComputeHash($operatorTokenBytes)",
    "$operatorSha.Dispose()",
    'Join-Path $repositoryRoot ".env.example"',
    '$mavenCommand = $mavenWrapper',
    'Get-Command sh -CommandType Application -ErrorAction Stop',
    '$mavenArgumentPrefix = @($mavenWrapper)',
    '$packageArguments = $mavenArgumentPrefix + @(',
    '$apiBuildDirectory = Join-Path $temporaryDirectory "api-target"',
    '$isolatedJarPath = Join-Path',
    '$jarPath = if ($SkipPackage) { $sharedJarPath } else { $isolatedJarPath }',
    '("-Dwsr.build.directory=" + $apiBuildDirectory)',
    'JAVA_HOME         = $checkedJavaHome',
    'MAVEN_ARGS        = $null',
    'MAVEN_OPTS        = $null',
    'MAVEN_SKIP_RC     = "true"',
    r"Java version:\s*21(?:\.|,)",
    '$value = $Variables[$name]',
    'if ($null -eq $value)',
    'Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue',
    'if ($null -eq $previous[$name])',
    'SPRING_PROFILES_ACTIVE      = "acceptance"',
    'SPRING_MAIN_BANNER_MODE     = "off"',
    'LOGGING_LEVEL_ROOT          = "INFO"',
    'LOGGING_CONFIG              = $null',
    'LOGGING_FILE_NAME           = $null',
    'LOGGING_FILE_PATH           = $null',
    'LOG_FILE                    = $null',
    'LOG_PATH                    = $null',
    '$springApplicationJson = @{',
    'flyway = @{',
    'SPRING_APPLICATION_JSON     = $springApplicationJson',
    'SPRING_CONFIG_LOCATION      = "classpath:/"',
    'SPRING_CONFIG_ADDITIONAL_LOCATION = $null',
    'SPRING_CONFIG_IMPORT        = $null',
    'SPRING_CONFIG_NAME          = "application"',
    'SPRING_PROFILES_INCLUDE     = $null',
    'SPRING_PROFILES_GROUP_ACCEPTANCE = $null',
    'WSR_LOCAL_ENV_FILE          =',
    '(Join-Path $temporaryDirectory "never-import-root-env")',
    'JAVA_TOOL_OPTIONS           = $null',
    'JDK_JAVA_OPTIONS            = $null',
    '_JAVA_OPTIONS               = $null',
    'ssl = @{ enabled = $false }',
    'SERVER_SSL_ENABLED          = "false"',
    'SERVER_TOMCAT_BASEDIR       = $tomcatBaseDirectory',
    'SERVER_TOMCAT_ACCESSLOG_ENABLED = "false"',
    'SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.postgresql.Driver"',
    'SPRING_FLYWAY_ENABLED       = "true"',
    'SPRING_FLYWAY_URL           = $jdbcUrl',
    'SPRING_FLYWAY_USER          = "wsr_operator_acceptance"',
    'SPRING_FLYWAY_PASSWORD      = $databasePassword',
    'SPRING_FLYWAY_LOCATIONS     = "classpath:db/migration"',
    'APP_PROVIDERS_MARKET        = "fixture"',
    'APP_PROVIDERS_ANALYST       = "fixture"',
    'SEC_PROVIDER_ENABLED        = "false"',
    'SEC_BASE_URL                = "http://127.0.0.1:1"',
    'SEC_CONTACT_EMAIL           = ""',
    'OPERATOR_API_ENABLED        = "true"',
    "OPERATOR_API_TOKEN_SHA256   = $operatorDigest",
    'APP_PUBLIC_DATA_SEC_ENABLED = "false"',
    'APP_PUBLIC_DATA_SEC_BASE_URL = "http://127.0.0.1:1"',
    'APP_PUBLIC_DATA_SEC_CONTACT_EMAIL = ""',
    'MANAGEMENT_SERVER_PORT      = $null',
    'MANAGEMENT_SERVER_ADDRESS   = $null',
    'MANAGEMENT_SERVER_SSL_ENABLED = $null',
    '$apiEnvironment = ConvertTo-ProcessEnvironmentMap $apiEnvironment',
    'Add-InheritedEnvironmentRemovals',
    'LOGGING|LOGBACK|LOG4J|JUL',
    '$apiEnvironment["DEBUG"] = "false"',
    '$apiEnvironment["TRACE"] = "false"',
    '$apiEnvironment["SPRING_MVC_LOG_REQUEST_DETAILS"] = "false"',
    '$apiEnvironment["SPRING_CODEC_LOG_REQUEST_DETAILS"] = "false"',
    '$httpHandler = [Net.Http.HttpClientHandler]::new()',
    '$httpHandler.UseProxy = $false',
    '$httpHandler.AllowAutoRedirect = $false',
    '$httpClient = [Net.Http.HttpClient]::new($httpHandler, $false)',
    '[Uri]::new("http://127.0.0.1:$apiPort")',
    '"/internal/v1/sec/collection-attempts/root"',
    '"/internal/v1/sec/collection-attempts/exact-root"',
    '"OPERATOR_AUTHENTICATION_REQUIRED"',
    '"TERMINAL_FAILED_KNOWN"',
    '"PROVIDER_GATE_CLOSED"',
    '"PROVIDER_INVOCATION_NOT_STARTED"',
    '"OPERATOR_REQUEST_CONFLICT"',
    '"EXACT_EVIDENCE_NOT_ADMITTED"',
    "$unauthenticated.StatusCode -eq 401",
    "$first.StatusCode -eq 200",
    "$replay.StatusCode -eq 200",
    "$status.StatusCode -eq 200",
    "$conflict.StatusCode -eq 409",
    "$missingEvidence.StatusCode -eq 422",
    "($null -eq $firstJson.providerDispatch)",
    "($firstJson.automaticRetryAllowed -eq $false)",
    "$replay.Content -ceq $first.Content",
    "$status.Content -ceq $first.Content",
    '"1|0|1"',
    'down --volumes --remove-orphans',
    'Stop-Process -Id $apiProcess.Id -Force',
    '$stopped = $apiProcess.WaitForExit(10000)',
    'if (-not $stopped)',
    '"The exact API process did not exit within 10 seconds."',
    'Remove-Item -LiteralPath $resolvedPath -Recurse -Force',
    '[Array]::Clear($operatorBytes, 0, $operatorBytes.Length)',
    '[Array]::Clear($operatorDigestBytes, 0, $operatorDigestBytes.Length)',
    '[Array]::Clear($operatorTokenBytes, 0, $operatorTokenBytes.Length)',
    'Get-SanitizedLogTail',
    'Remove-VerifiedTemporaryDirectory $temporaryDirectory',
)
require(
    all(marker in harness for marker in required_harness_markers)
    and "$env:OS" not in harness
    and harness.count("if ($IsWindows)") >= 5
    and harness.count(
        'Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue'
    ) == 2,
    "ADR-044 token, loopback, provider-off, HTTP, ledger, redaction, or cleanup marker changed",
)

enter_lock = powershell_function(
    harness, "Enter-RepositoryAcceptanceLock"
)
exit_lock = powershell_function(
    harness, "Exit-RepositoryAcceptanceLock"
)
require(
    enter_lock.count("[IO.FileMode]::CreateNew") == 1
    and enter_lock.count("[IO.FileShare]::None") == 1
    and "[IO.FileMode]::OpenOrCreate" not in enter_lock
    and "schemaVersion = 1" in enter_lock
    and "$lockStream.Flush($true)" in enter_lock
    and "$Lock.Stream.Dispose()" in exit_lock
    and exit_lock.index("$Lock.Stream.Dispose()")
        < exit_lock.index("Remove-Item -LiteralPath $expectedPath -Force"),
    "ADR-044 must atomically create, exclusively hold, validate, and remove the exact root lock",
)

java_probe = environment_section(
    harness,
    "$javaProbeEnvironment = @{",
    "\n    $javaSettingsOutput",
)
maven_build = environment_section(
    harness,
    "$mavenBuildEnvironment = @{",
    "\n    $mavenVersionOutput",
)
api_environment = environment_section(
    harness,
    "$apiEnvironment = @{",
    '\n\n    Write-Host "[4/5]',
)
docker_environment = environment_section(
    harness,
    "$dockerEnvironment = @{",
    "\n    $dockerInfo =",
)
compose_environment = environment_section(
    harness,
    "$composeEnvironment = @{",
    '\n\n    Write-Host "[3/5]',
)
require_exact_assignments(
    java_probe,
    {
        "JAVA_TOOL_OPTIONS": "$null",
        "JDK_JAVA_OPTIONS": "$null",
        "_JAVA_OPTIONS": "$null",
        "CLASSPATH": "$null",
    },
    "ADR-044 Java probe",
)
require_exact_assignments(
    maven_build,
    {
        "JAVA_HOME": "$checkedJavaHome",
        "MAVEN_ARGS": "$null",
        "MAVEN_OPTS": "$null",
        "MAVEN_SKIP_RC": '"true"',
        "JAVA_TOOL_OPTIONS": "$null",
        "JDK_JAVA_OPTIONS": "$null",
        "_JAVA_OPTIONS": "$null",
        "CLASSPATH": "$null",
    },
    "ADR-044 Maven",
)
require_exact_assignments(
    api_environment,
    {
        "SPRING_CONFIG_LOCATION": '"classpath:/"',
        "LOGGING_LEVEL_ROOT": '"INFO"',
        "LOGGING_CONFIG": "$null",
        "LOGGING_FILE_NAME": "$null",
        "LOGGING_FILE_PATH": "$null",
        "LOG_FILE": "$null",
        "LOG_PATH": "$null",
        "JAVA_TOOL_OPTIONS": "$null",
        "JDK_JAVA_OPTIONS": "$null",
        "_JAVA_OPTIONS": "$null",
        "SERVER_TOMCAT_BASEDIR": "$tomcatBaseDirectory",
        "SERVER_TOMCAT_ACCESSLOG_ENABLED": '"false"',
        "APP_PROVIDERS_MARKET": '"fixture"',
        "APP_PROVIDERS_ANALYST": '"fixture"',
    },
    "ADR-044 API config/logging/JVM/provider",
)
docker_pin_assignments = {
    "DOCKER_CONTEXT": "$null",
    "DOCKER_HOST": "$dockerEndpoint",
    "DOCKER_TLS_VERIFY": "$null",
    "DOCKER_CERT_PATH": "$null",
}
require_exact_assignments(
    docker_environment, docker_pin_assignments, "ADR-044 Docker probe"
)
require_exact_assignments(
    compose_environment, docker_pin_assignments, "ADR-044 Compose"
)
spring_json = environment_section(
    harness,
    "$springApplicationJson = @{",
    "\n    $apiEnvironment = @{",
)
comparer_function = powershell_function(
    harness, "Get-ProcessEnvironmentNameComparer"
)
converter_function = powershell_function(
    harness, "ConvertTo-ProcessEnvironmentMap"
)
inherited_removal_function = powershell_function(
    harness, "Add-InheritedEnvironmentRemovals"
)
invoke_environment_function = powershell_function(
    harness, "Invoke-WithProcessEnvironment"
)
api_convert = api_environment.index(
    "$apiEnvironment = ConvertTo-ProcessEnvironmentMap $apiEnvironment"
)
inherited_removal = api_environment.index(
    "Add-InheritedEnvironmentRemovals", api_convert
)
logging_root_override = api_environment.index(
    '$apiEnvironment["LOGGING_LEVEL_ROOT"] = "INFO"',
    inherited_removal,
)
require(
    'app = @{ providers = @{ market = "fixture" analyst = "fixture" }'
        in compact(spring_json)
    and "[StringComparer]::OrdinalIgnoreCase" in comparer_function
    and "[StringComparer]::Ordinal" in comparer_function
    and "[Collections.Generic.Dictionary[string, object]]::new("
        in converter_function
    and "(Get-ProcessEnvironmentNameComparer)" in converter_function
    and "-not $Variables.ContainsKey($environmentName)"
        in inherited_removal_function
    and '[Environment]::GetEnvironmentVariables("Process").Keys'
        in inherited_removal_function
    and "$environmentName -match $NamePattern"
        in inherited_removal_function
    and "$Variables[$environmentName] = $null"
        in inherited_removal_function
    and "[Collections.Generic.Dictionary[string, object]]::new("
        in invoke_environment_function
    and "(Get-ProcessEnvironmentNameComparer)"
        in invoke_environment_function
    and "$previous[$name] = [Environment]::GetEnvironmentVariable($name, \"Process\")"
        in invoke_environment_function
    and invoke_environment_function.count(
        'Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue'
    ) == 2
    and api_convert < inherited_removal < logging_root_override
    and "LOGGING|LOGBACK|LOG4J|JUL"
        in api_environment
    and "'^(?:(?:SPRING|SERVER|MANAGEMENT|APP|POSTGRES|FLYWAY|MARKET|ANALYST|MACRO|MEDIA|SEC|OPERATOR|DATA|LOGGING|LOGBACK|LOG4J|JUL)(?:[_.-]|$)|DEBUG$|TRACE$)'"
        in api_environment
    and all(
        api_environment.count(
            f'$apiEnvironment["{name}"] = "{value}"'
        ) == 1
        for name, value in {
            "LOGGING_LEVEL_ROOT": "INFO",
            "DEBUG": "false",
            "TRACE": "false",
            "SPRING_MVC_LOG_REQUEST_DETAILS": "false",
            "SPRING_CODEC_LOG_REQUEST_DETAILS": "false",
        }.items()
    ),
    "ADR-044 Spring JSON providers or inherited logging/request-detail sanitization changed",
)

lock_enter_call = harness.index(
    "$repositoryLock = Enter-RepositoryAcceptanceLock"
)
docker_select = harness.index(
    "$dockerEndpoint = Get-SelectedDockerEndpoint $dockerCommand",
    lock_enter_call,
)
docker_assert = harness.index(
    "Assert-LocalDockerEndpoint $dockerEndpoint", docker_select
)
docker_environment_start = harness.index(
    "$dockerEnvironment = @{", docker_assert
)
docker_info = harness.index(
    "$dockerInfo = Invoke-WithProcessEnvironment $dockerEnvironment",
    docker_environment_start,
)
docker_compose_version = harness.index(
    "Invoke-WithProcessEnvironment $dockerEnvironment {",
    docker_info + 1,
)
temporary_owned_initial = harness.index(
    "$temporaryDirectoryOwned = $false"
)
temporary_create = harness.index(
    "New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null",
    docker_compose_version,
)
temporary_owned = harness.index(
    "$temporaryDirectoryOwned = $true", temporary_create
)
package_gate = harness.index("if (-not $SkipPackage)", docker_info)
jar_assertion = harness.index(
    "Assert-Condition (Test-Path -LiteralPath $jarPath -PathType Leaf)",
    package_gate,
)
api_jar_start = harness.index(
    'ArgumentList           = @("-jar", (\'"\' + $jarPath + \'"\'))',
    jar_assertion,
)
compose_environment_start = harness.index(
    "$composeEnvironment = @{", jar_assertion
)
compose_up = harness.index(
    "Invoke-WithProcessEnvironment $composeEnvironment {",
    compose_environment_start,
)
compose_up_command = harness.index(
    '"up", "--detach", "--wait", "--wait-timeout", "90", "postgres"',
    compose_up,
)
database_exec = harness.index(
    "$databaseCountResult = Invoke-WithProcessEnvironment $composeEnvironment",
    api_jar_start,
)
main_finally = harness.rindex("finally {")
compose_down = harness.index(
    "$composeCleanupExitCode = Invoke-WithProcessEnvironment $composeEnvironment",
    main_finally,
)
temp_cleanup_gate = harness.rindex("if ($temporaryDirectoryOwned)")
temp_cleanup = harness.index(
    "Remove-VerifiedTemporaryDirectory $temporaryDirectory"
    , temp_cleanup_gate
)
temporary_released = harness.index(
    "$temporaryDirectoryOwned = $false", temp_cleanup
)
lock_exit_call = harness.rindex(
    "Exit-RepositoryAcceptanceLock $repositoryLock $repositoryRoot"
)
require(
    temporary_owned_initial < lock_enter_call
    < docker_select < docker_assert < docker_environment_start
    < docker_info < docker_compose_version
    < temporary_create < temporary_owned < package_gate
    < jar_assertion < compose_environment_start < compose_up
    < compose_up_command
    < api_jar_start < database_exec < main_finally < compose_down
    < temp_cleanup_gate
    < temp_cleanup < temporary_released < lock_exit_call
    and harness.count(
        "$jarPath = if ($SkipPackage) { $sharedJarPath } else { $isolatedJarPath }"
    ) == 1
    and harness.count(
        '("-Dwsr.build.directory=" + $apiBuildDirectory)'
    ) == 1,
    "ADR-044 lock, remote-Docker rejection, pinned Docker, owned-temp creation, isolated package, JAR run/evidence, cleanup, and unlock order changed",
)
require(
    harness.count(
        "New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null"
    ) == 1
    and harness.count("$temporaryDirectoryOwned = $true") == 1
    and harness.count("if ($temporaryDirectoryOwned)") == 1
    and harness.count(
        "Remove-VerifiedTemporaryDirectory $temporaryDirectory"
    ) == 1
    and "Remove-VerifiedTemporaryDirectory $temporaryDirectory"
        not in harness[lock_enter_call:temporary_create],
    "ADR-044 must never claim or remove a pre-existing exact-name temporary directory",
)
temporary_cleanup_function = powershell_function(
    harness, "Remove-VerifiedTemporaryDirectory"
)
require(
    all(marker in temporary_cleanup_function for marker in (
        "[IO.Path]::GetTempPath()",
        "$resolvedPath.StartsWith($temporaryRootWithSeparator, $comparison)",
        "^wsr-local-operator-[a-f0-9]{12}$",
        "Remove-Item -LiteralPath $resolvedPath -Recurse -Force",
    )),
    "ADR-044 temporary cleanup must remain constrained to its validated OS-temp child",
)
require(
    harness.count("& $dockerCommand info") == 1
    and harness.count("& $dockerCommand compose") == 3
    and harness.count("DOCKER_HOST       = $dockerEndpoint") == 2
    and harness.count("DOCKER_CONTEXT    = $null") == 2
    and harness.count("DOCKER_TLS_VERIFY = $null") == 2
    and harness.count("DOCKER_CERT_PATH  = $null") == 2
    and compact(harness).count(
        "$dockerInfo = Invoke-WithProcessEnvironment $dockerEnvironment { $output = & $dockerCommand info"
    ) == 1
    and compact(harness).count(
        "Invoke-WithProcessEnvironment $dockerEnvironment { & $dockerCommand compose version"
    ) == 1
    and re.search(
        r"Invoke-WithProcessEnvironment \$composeEnvironment \{\s*"
        r"Invoke-CheckedNative\s*`\s*\$dockerCommand\s*`\s*@\(.*?"
        r'"up", "--detach", "--wait", "--wait-timeout", "90", "postgres".*?'
        r'\)\s*`\s*"The isolated PostgreSQL service did not become healthy"\s*\}',
        harness,
        re.DOTALL,
    ) is not None
    and compact(harness).count(
        "$databaseCountResult = Invoke-WithProcessEnvironment $composeEnvironment { $output = & $dockerCommand compose"
    ) == 1
    and compact(harness).count(
        "$composeCleanupExitCode = Invoke-WithProcessEnvironment $composeEnvironment { & $dockerCommand compose"
    ) == 1,
    "ADR-044 Docker info/version/up/exec/down must stay pinned to the selected local endpoint",
)

gitignore = gitignore_path.read_text(encoding="utf-8")
require(
    gitignore.splitlines().count("/.wsr-local-acceptance.lock") == 1,
    "ADR-044 stale root acceptance lock must remain exactly ignored",
)

live_smoke_variable = "SEC_" + "LIVE_SMOKE"
sec_origin = "https://data." + "sec.gov"
require(
    f'{live_smoke_variable}              = "false"' in harness
    and sec_origin not in harness
    and 'Join-Path $repositoryRoot ".env"' not in harness
    and re.search(
        r"(?m)^\s*OPERATOR_API_TOKEN\s*=", harness
    ) is None
    and 'SEC_PROVIDER_ENABLED        = "true"' not in harness
    and 'SERVER_ADDRESS              = "0.0.0.0"' not in harness
    and "Invoke-WebRequest" not in harness
    and "Invoke-RestMethod" not in harness
    and "curl " not in harness
    and "wget " not in harness
    and re.search(
        r"(?im)(?:Write-Host|Write-Output|Write-Information)[^\n]*"
        r"(?:operatorToken|operatorDigest|databasePassword)",
        harness,
    ) is None
    and re.search(
        r"(?im)(?:Set-Content|Add-Content|Out-File)[^\n]*"
        r"(?:operatorToken|operatorDigest|databasePassword)",
        harness,
    ) is None,
    "ADR-044 harness must not read local secrets, persist a raw token, expose the server, or contact SEC",
)
http_origins = re.findall(r'https?://[^"\s]+', harness)
require(
    http_origins.count("http://127.0.0.1:1") == 3
    and http_origins.count("http://127.0.0.1:$apiPort") == 1
    and len(http_origins) == 4,
    "ADR-044 harness HTTP origins must remain exact closed or dynamic loopback addresses",
)
require(
    "--project-name" in harness
    and "$composeProject" in harness
    and "^wsr-operator-[0-9]+-[a-f0-9]{8}$" in harness
    and "^wsr-local-operator-[a-f0-9]{12}$" in harness
    and "Invoke-WithProcessEnvironment" in harness
    and "finally {" in harness
    and "Cleanup complete: no test API process, Compose project, volume, or token file remains."
        in harness,
    "ADR-044 isolated project ownership, process environment, or cleanup proof changed",
)

main_root = Path("apps/api/src/main/java/com/wallstreetreceipts/api")
test_root = Path("apps/api/src/test/java/com/wallstreetreceipts/api")
new_main_paths = {
    main_root / "application/filinghistory/ExactEvidenceNotAdmittedException.java",
    main_root / "application/filinghistory/OperatorRequestConflictException.java",
    main_root / "application/filinghistory/SecFilingHistoryCollectionAttemptNotFoundException.java",
    main_root / "application/filinghistory/SecFilingHistoryCollectionAttemptQueryService.java",
    main_root / "config/OperatorApiProperties.java",
    main_root / "config/OperatorApiSecurityConfiguration.java",
    main_root / "config/OperatorSecCollectionAttemptApiConfiguration.java",
    main_root / "web/operator/OperatorSecCollectionAttemptController.java",
    main_root / "web/operator/OperatorSecCollectionAttemptExceptionHandler.java",
    main_root / "web/operator/OperatorSecCollectionAttemptRequests.java",
    main_root / "web/operator/OperatorSecCollectionAttemptResponseMapper.java",
    main_root / "web/operator/OperatorSecCollectionAttemptResponses.java",
    main_root / "web/security/OperatorApiSecurityProblemWriter.java",
    main_root / "web/security/ApiRequestRejectedHandler.java",
    main_root / "web/security/OperatorBearerAuthenticationToken.java",
    main_root / "web/security/OperatorBearerTokenAuthenticationFilter.java",
    main_root / "web/security/OperatorBearerTokenAuthenticationProvider.java",
}
new_test_paths = {
    test_root / "application/filinghistory/SecFilingHistoryCollectionAttemptQueryServiceTest.java",
    test_root / "config/OperatorApiPropertiesTest.java",
    test_root / "config/OperatorSecCollectionAttemptApiConfigurationTest.java",
    test_root / "web/operator/OperatorSecCollectionAttemptApiTest.java",
    test_root / "web/operator/OperatorSecCollectionAttemptIntegrationTest.java",
    test_root / "web/security/OperatorApiDisabledSecurityTest.java",
    test_root / "web/security/OperatorApiLoopbackBindingTest.java",
    test_root / "web/security/OperatorApiSecurityTest.java",
}
require(
    len(new_main_paths) == 17
    and len(new_test_paths) == 8
    and all(path.is_file() for path in new_main_paths | new_test_paths)
    and set((main_root / "config").glob("Operator*.java"))
        == {path for path in new_main_paths if path.parent.name == "config"}
    and set((main_root / "web/operator").glob("*.java"))
        == {path for path in new_main_paths if path.parent.name == "operator"}
    and set((main_root / "web/security").glob("*.java"))
        == {path for path in new_main_paths if path.parent.name == "security"}
    and set((test_root / "config").glob("Operator*.java"))
        == {path for path in new_test_paths if path.parent.name == "config"}
    and set((test_root / "web/operator").glob("*.java"))
        == {path for path in new_test_paths if path.parent.name == "operator"}
    and set((test_root / "web/security").glob("*.java"))
        == {path for path in new_test_paths if path.parent.name == "security"},
    "ADR-043 exact 17 main and 8 test surfaces changed",
)

sources = {
    path.name: path.read_text(encoding="utf-8") for path in new_main_paths
}
tests = {
    path.name: path.read_text(encoding="utf-8") for path in new_test_paths
}
properties = sources["OperatorApiProperties.java"]
security = sources["OperatorApiSecurityConfiguration.java"]
request_rejected = sources["ApiRequestRejectedHandler.java"]
bearer_filter = sources["OperatorBearerTokenAuthenticationFilter.java"]
bearer_provider = sources["OperatorBearerTokenAuthenticationProvider.java"]
controller = sources["OperatorSecCollectionAttemptController.java"]
requests = sources["OperatorSecCollectionAttemptRequests.java"]
responses = sources["OperatorSecCollectionAttemptResponses.java"]
mapper = sources["OperatorSecCollectionAttemptResponseMapper.java"]
query = sources["SecFilingHistoryCollectionAttemptQueryService.java"]

require(
    'Pattern.compile("^[0-9a-f]{64}$")' in properties
    and '"0".repeat(64)' in properties
    and "tokenSha256=<redacted>" in properties
    and 'matcher("/internal/v1/sec/**")' in security
    and "SessionCreationPolicy.STATELESS" in security
    and ".anyRequest().hasAuthority(OPERATOR_AUTHORITY)" in security
    and "factory.setAddress(InetAddress.getLoopbackAddress())" in security
    and 'prefix = "app.operator-api"' in security
    and 'havingValue = "true"' in security
    and "MessageDigest.isEqual(expectedDigest, actualDigest)" in bearer_provider
    and "authorizationHeaders.size() != 1" in bearer_filter
    and "decoded.length != 32" in bearer_filter
    and "web.requestRejectedHandler(requestRejectedHandler)" in security
    and 'URI.create("/invalid-request")' in request_rejected
    and '"INVALID_QUERY"' in request_rejected
    and 'OPERATOR_API_PREFIX = "/internal/v1/sec/"' in request_rejected
    and 'response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")'
        in request_rejected,
    "ADR-043 digest, exact Bearer, authority, stateless, or loopback boundary changed",
)
require(
    'PATH = "/internal/v1/sec/collection-attempts"' in controller
    and controller.count("@PostMapping(") == 2
    and controller.count("@GetMapping(") == 1
    and '@PostMapping("/root")' in controller
    and '@PostMapping("/exact-root")' in controller
    and '@GetMapping("/{attemptId}")' in controller
    and controller.count("CacheControl.noStore()") == 2
    and "executionService.captureRoot(" in controller
    and "executionService.collectExactRoot(" in controller
    and "queryService.findByAttemptId(attemptId)" in controller
    and "requireDocumentEnd(parser)" in requests
    and "Duplicate operator command field" in requests
    and "automaticRetryAllowed" in responses
    and "false," in mapper
    and "findByAttemptId(attemptId)" in query
    and all(marker not in controller.lower() for marker in (
        "retry", "resume", "cancel", "abandon", "resolve", "schedule",
    )),
    "ADR-043 exact routes, strict bodies, allowlist, no-store, or no-retry transport changed",
)

application = Path(
    "apps/api/src/main/resources/application.yml"
).read_text(encoding="utf-8")
env_example = Path(".env.example").read_text(encoding="utf-8")
require(
    application.splitlines().count(
        "    enabled: ${OPERATOR_API_ENABLED:false}"
    ) == 1
    and application.splitlines().count(
        "    token-sha256: ${OPERATOR_API_TOKEN_SHA256:}"
    ) == 1
    and env_example.splitlines().count("OPERATOR_API_ENABLED=false") == 1
    and env_example.splitlines().count("OPERATOR_API_TOKEN_SHA256=") == 1
    and "OPERATOR_API_TOKEN=" not in env_example,
    "ADR-043 server-only default-disabled configuration changed",
)
pom_root = ET.parse("apps/api/pom.xml").getroot()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
dependencies = [
    dependency.findtext("m:artifactId", namespaces=namespace)
    for dependency in pom_root.findall("m:dependencies/m:dependency", namespace)
]
pom_properties = pom_root.find("m:properties", namespace)
pom_build = pom_root.find("m:build", namespace)
require(
    dependencies.count("spring-boot-starter-security") == 1
    and "spring-security-test" not in dependencies
    and pom_properties is not None
    and len(pom_properties.findall("m:wsr.build.directory", namespace)) == 1
    and pom_properties.findtext(
        "m:wsr.build.directory", namespaces=namespace
    ) == "${project.basedir}/target"
    and pom_build is not None
    and len(pom_build.findall("m:directory", namespace)) == 1
    and pom_build.findtext("m:directory", namespaces=namespace)
        == "${wsr.build.directory}"
    and not pom_root.findall(
        "m:profiles/m:profile/m:build/m:directory", namespace
    ),
    "ADR-043/ADR-044 runtime dependency or isolated Maven output boundary changed",
)

focused_test_source = "\n".join(tests.values())
require(
    all(method in focused_test_source for method in (
        "operatorApiIsDisabledWithoutCredentialsByDefault",
        "enabledOperatorApiFailsStartupForMissingOrInvalidDigest",
        "disabledOperatorApiFallsThroughToOrdinaryNotFoundWithoutAuthChallenge",
        "forwardedIdentityHeadersAreNeverTrustedWithoutBearerCredential",
        "enabledOperatorApiOverridesWildcardConfigurationWithActualLoopbackBinding",
        "captureRootReturnsAllowlistedNoStoreStatusAndLocation",
        "statusPreservesIndeterminateSafetyFlagsWithoutSuggestingRetry",
        "authenticatedOfflineCommandPersistsReplaysAndReconstructsWithoutAnyProvider",
    ))
    and all(marker not in focused_test_source for marker in (
        "data.sec.gov", "HttpClient.newHttpClient(", "RestClient.create(",
        "WebClient.create(", ".openConnection(", "new Socket(",
    )),
    "ADR-043 focused tests must cover the boundary and remain offline",
)
publication_paths = {
    Path("contracts/openapi.yaml"),
    *Path("schemas").glob("*.json"),
    *(path for path in Path("fixtures/v1").rglob("*") if path.is_file()),
    *(path for root in (Path("apps/web/src"), Path("apps/web/e2e"))
      for path in root.rglob("*") if path.is_file()),
}
require(
    all(
        "/internal/v1/sec" not in path.read_text(
            encoding="utf-8", errors="ignore"
        )
        for path in publication_paths
    ),
    "ADR-043 private local operator API must not reach public contracts or web",
)

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
guard_step_name = (
    "Guard default-disabled local single-operator SEC attempt API"
)
full_stack_guard_name = (
    "Guard disposable offline local full-stack acceptance harness"
)
parse_step_name = (
    "Parse disposable offline local operator API acceptance harness"
)
full_stack_parse_name = (
    "Parse disposable offline local full-stack acceptance harness"
)
guard_start, guard_end, _ = workflow_step(workflow, guard_step_name)
parse_start, _, parse_source = workflow_step(workflow, parse_step_name)
focused_command = (
    "./mvnw -B -ntp "
    "-Dtest=OperatorApiPropertiesTest,OperatorApiDisabledSecurityTest,"
    "OperatorApiSecurityTest,OperatorApiLoopbackBindingTest,"
    "OperatorSecCollectionAttemptApiConfigurationTest,"
    "SecFilingHistoryCollectionAttemptQueryServiceTest,"
    "OperatorSecCollectionAttemptApiTest,OperatorSecCollectionAttemptIntegrationTest test"
)
workflow_behavior = workflow
for step_name in (
    guard_step_name,
    parse_step_name,
    full_stack_guard_name,
    full_stack_parse_name,
):
    require(
        workflow_behavior.count(f"\n      - name: {step_name}\n") == 1,
        f"Workflow step count changed for {step_name}",
    )
    workflow_behavior = remove_workflow_step(
        workflow_behavior, step_name
    )
require(
    guard_end == parse_start
    and parse_source.count(harness_path.as_posix()) == 1
    and parse_source.count(
        "[System.Management.Automation.Language.Parser]::ParseFile("
    ) == 1
    and compact(workflow_behavior).count(focused_command) == 1
    and harness_command not in workflow_behavior
    and "pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1"
        not in workflow_behavior
    and "OPERATOR_API_ENABLED: 'true'" not in workflow_behavior
    and re.search(
        r"(?:curl|wget|Invoke-WebRequest)[^\n]*data\.sec\.gov",
        workflow_behavior,
        re.IGNORECASE,
    ) is None,
    "Default CI must run exactly the offline ADR-043 suite with both live gates closed",
)

print(
    "Validated ADR-043 exact 17+8 surface, default-disabled loopback-only "
    "opaque Bearer boundary, exact immutable routes and allowlist, sanitized "
    "no-store failures, offline integration, and no public web/OpenAPI/live SEC path; "
    "validated ADR-044 one-file disposable loopback/PostgreSQL harness, "
    "shared atomic root lock, isolated Maven output, credential isolation, "
    "owned cleanup, and parse-only CI"
)
PYTHON
