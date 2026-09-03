python <<'PYTHON'
import hashlib
import json
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
    require(source.count(marker) == 1, f"Workflow step count changed for {name}")
    start = source.index(marker)
    end = source.index("\n      - name: ", start + len(marker))
    return start, end, source[start:end]

def remove_workflow_step(source, name):
    start, end, _ = workflow_step(source, name)
    return source[:start] + source[end:]

adr_path = Path(
    "decisions/ADR-045-disposable-offline-local-full-stack-acceptance-harness.md"
)
harness_path = Path("scripts/verify-local-full-stack.ps1")
operator_harness_path = Path("scripts") / "verify-local-operator-api.ps1"
readme_path = Path("README.md")
api_readme_path = Path("apps/api/README.md")
log_path = Path("IMPLEMENTATION_LOG.md")
playwright_config_path = Path("apps/web/playwright.config.ts")
runtime_assertions_path = Path("apps/web/e2e/runtime-assertions.ts")
next_config_path = Path("apps/web/next.config.ts")
tsconfig_path = Path("apps/web/tsconfig.json")
pom_path = Path("apps/api/pom.xml")
gitignore_path = Path(".gitignore")
spec_paths = {
    Path("apps/web/e2e/call-list-api.spec.ts"),
    Path("apps/web/e2e/call-revisions.spec.ts"),
    Path("apps/web/e2e/call-outcomes.spec.ts"),
}
required_paths = {
    adr_path,
    harness_path,
    operator_harness_path,
    readme_path,
    api_readme_path,
    log_path,
    playwright_config_path,
    runtime_assertions_path,
    next_config_path,
    tsconfig_path,
    pom_path,
    gitignore_path,
    *spec_paths,
}
require(
    all(path.is_file() for path in required_paths),
    "Missing ADR-045 harness, decision, browser, README, or implementation-log surface",
)

documents = {
    path: path.read_text(encoding="utf-8")
    for path in (adr_path, readme_path, api_readme_path, log_path)
}
adr = documents[adr_path]
harness_command = (
    "pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1"
)
require(
    adr.startswith(
        "# ADR-045 — Disposable Offline Local Full-Stack Acceptance Harness\n\n"
        "- Status: Accepted\n- Date: 2026-08-26\n"
    )
    and all(
        "ADR-045" in documents[path]
        for path in (adr_path, readme_path, log_path)
    )
    and all(
        harness_command in documents[path]
        for path in (adr_path, readme_path, log_path)
    ),
    "ADR-045 title, accepted status, date, command, or documentation parity changed",
)
required_adr_terms = (
    "atomically creates and exclusively holds the same root `/.wsr-local-acceptance.lock` as ADR-044",
    "Maven wrapper's checked Java 21 runtime, Node.js 24, a local-only Docker endpoint with Compose v2",
    "CALL_AUDIT_PROVIDER=api",
    "NEXT_PUBLIC_DATA_MODE=DEMO",
    "apps/web/.wsr-local-full-stack-<run-id>/apps/web",
    "only the explicit allowlist above is copied",
    "original `apps/web/next-env.d.ts`, `apps/web/tsconfig.json`, and standard ignored `.next` remain untouched",
    "`SPRING_CONFIG_LOCATION` is fixed to `classpath:/`",
    "atomic root lock across package/build/run/cleanup",
    "A hard-terminated owner deliberately leaves the ignored lock file behind",
    "every other current web selector",
    "clear inherited Node module injection and every observed case variant of the HTTP, HTTPS, all-proxy, and no-proxy variables",
    "exact numeric `127.0.0.1` origin",
    "`--no-proxy-server`",
    "12 primary product routes",
    "five list reads and eight detail/context/revision/outcome reads",
    "three calls, two revisions, and four outcomes",
    "Each directory is removed only after this run has successfully created it",
    "Remote Docker daemons are intentionally rejected before contact",
    "CI does not execute this extra local composition command",
)
readme_acceptance = markdown_section(
    documents[readme_path],
    "ADR-044 adds the preferred one-command pre-deployment acceptance gate",
    "ADR-022 remains the sole shared receipt",
)
log_scope = markdown_section(
    documents[log_path],
    "## 2026-08-26 — ADR-045 disposable offline local full-stack acceptance harness",
    "### Routes and module structure",
)
require(
    all(compact(term) in compact(adr) for term in required_adr_terms)
    and all(compact(term) in compact(readme_acceptance) for term in (
        "secret-free source mirror",
        "standard `.next`",
        "fail-fast atomic root lock",
        "reject remote Docker endpoints before daemon contact",
        "pin the validated local endpoint for their remaining Docker operations",
        "clear inherited proxy variables including mixed/lowercase variants",
        "browser uses exact `127.0.0.1`",
    ))
    and all(compact(term) in compact(log_scope) for term in (
        "apps/web/.wsr-local-full-stack-<run-id>/apps/web",
        "leaving the caller's `next-env.d.ts`, `tsconfig.json`, standard `.next`",
        "all nine other current web provider selectors to `fixture`",
        "Reject remote Docker endpoints before daemon contact",
        "Remove inherited Spring, server, management, datasource/Hikari, JNDI, direct-provider, Flyway, Java-option, and logging namespace settings before applying the exact process allowlist",
        "Clear Node/browser proxy and module-injection variables",
        "`classpath:/`",
        "exact `127.0.0.1` and Chromium `--no-proxy-server`",
        "Set ownership flags only after successful atomic directory creation",
    ))
    and "daemon 접촉 전에" in documents[api_readme_path]
    and "/.wsr-local-acceptance.lock" in documents[api_readme_path],
    "ADR-045 topology, isolation, ownership, operator requirements, or documentation parity changed",
)

acceptance_script_paths = {
    path for path in Path("scripts").glob("verify-local-*.ps1")
    if path.is_file()
}
require(
    acceptance_script_paths == {harness_path, operator_harness_path},
    "ADR-044/ADR-045 must remain the exact two-script local acceptance surface",
)

harness = harness_path.read_text(encoding="utf-8")
required_harness_markers = (
    "#Requires -Version 7.0",
    "[ValidateRange(30, 600)]",
    "[switch] $SkipPackage",
    "[switch] $SkipWebBuild",
    "function Enter-RepositoryAcceptanceLock",
    "function Exit-RepositoryAcceptanceLock",
    'Join-Path $resolvedRepository ".wsr-local-acceptance.lock"',
    "[IO.FileMode]::CreateNew",
    "[IO.FileAccess]::Write",
    "[IO.FileShare]::None",
    "$lockStream.Flush($true)",
    "$Lock.Stream.Dispose()",
    "function Get-SelectedDockerEndpoint",
    "function Assert-LocalDockerEndpoint",
    '"context", "inspect"',
    "$dockerEndpoint = Get-SelectedDockerEndpoint $dockerCommand",
    "Assert-LocalDockerEndpoint $dockerEndpoint",
    "$dockerEnvironment = @{",
    "DOCKER_HOST       = $dockerEndpoint",
    "[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)",
    'Join-Path $repositoryRoot ".env.example"',
    'Join-Path $webDirectory "next-env.d.ts"',
    'Join-Path $webDirectory "tsconfig.json"',
    '$apiBuildDirectory = Join-Path $temporaryDirectory "api-target"',
    '("-Dwsr.build.directory=" + $apiBuildDirectory)',
    'JAVA_HOME         = $checkedJavaHome',
    'MAVEN_ARGS        = $null',
    'MAVEN_OPTS        = $null',
    'MAVEN_SKIP_RC     = "true"',
    r"Java version:\s*21(?:\.|,)",
    '$webMirrorRoot = Join-Path $webDirectory ".wsr-local-full-stack-$runId"',
    '$mirroredWebDirectory = Join-Path $webMirrorRoot "apps/web"',
    '$acceptanceWebDirectory = if ($SkipWebBuild)',
    '$nextDistributionDirectory = Join-Path $acceptanceWebDirectory ".next"',
    "function New-AcceptanceWebMirror",
    "function Remove-VerifiedWebMirrorDirectory",
    '@($nextCliPath, "build", $acceptanceWebDirectory)',
    '"start",',
    '"--hostname", "127.0.0.1"',
    'WorkingDirectory       = $acceptanceWebDirectory',
    '$webLoopbackBaseUrl = "http://127.0.0.1:$webPort"',
    'PLAYWRIGHT_BASE_URL     = $webLoopbackBaseUrl',
    'CALL_AUDIT_PROVIDER   = "api"',
    'CALL_AUDIT_PROVIDER     = "api"',
    'NEXT_PUBLIC_DATA_MODE = "DEMO"',
    'NEXT_PUBLIC_API_BASE_URL = ""',
    'PLAYWRIGHT_EXTERNAL_SERVER = "true"',
    'PLAYWRIGHT_LOCAL_PRODUCTION_HTTP = "true"',
    '"call-list-api.spec.ts"',
    '"call-revisions.spec.ts"',
    '"call-outcomes.spec.ts"',
    '"--project=chromium-1280"',
    'SERVER_TOMCAT_ACCESSLOG_PATTERN = "%m %U%q %s"',
    'SPRING_CONFIG_LOCATION        = "classpath:/"',
    'APP_PROVIDERS_MARKET          = "fixture"',
    'APP_PROVIDERS_ANALYST         = "fixture"',
    'SEC_PROVIDER_ENABLED          = "false"',
    'SEC_BASE_URL                  = "http://127.0.0.1:1"',
    'OPERATOR_API_ENABLED          = "false"',
    '"3|2|4"',
    "down --volumes --remove-orphans",
    'Stop-OwnedProcess $webProcess "production web"',
    'Stop-OwnedProcess $apiProcess "API"',
    "Stop-Process -Id $Process.Id -Force",
    "$stopped = $Process.WaitForExit(10000)",
    "Remove-Item -LiteralPath $resolvedPath -Recurse -Force",
    "Remove-VerifiedWebMirrorDirectory",
    "Remove-VerifiedTemporaryDirectory $temporaryDirectory",
    "Cleanup complete: no test web/API process, Compose project, volume, source mirror, harness build, or temp report remains.",
)
require(
    all(marker in harness for marker in required_harness_markers)
    and harness.count(
        'Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue'
    ) == 2
    and harness.count('"GET /v1/calls') == 13
    and harness.count("/calls/demo-call-001") >= 2
    and harness.count("/calls/demo-call-002") >= 2,
    "ADR-045 build, route, browser, evidence, isolation, or cleanup marker changed",
)

operator_harness = operator_harness_path.read_text(encoding="utf-8")
shared_function_names = (
    "Invoke-WithProcessEnvironment",
    "Get-ProcessEnvironmentNameComparer",
    "ConvertTo-ProcessEnvironmentMap",
    "Add-InheritedEnvironmentRemovals",
    "Enter-RepositoryAcceptanceLock",
    "Exit-RepositoryAcceptanceLock",
    "Get-SelectedDockerEndpoint",
    "Assert-LocalDockerEndpoint",
)
require(
    all(
        powershell_function(harness, name)
            == powershell_function(operator_harness, name)
        for name in shared_function_names
    )
    and "$env:OS" not in harness
    and "$env:OS" not in operator_harness
    and harness.count("if ($IsWindows)") >= 5
    and operator_harness.count("if ($IsWindows)") >= 5,
    "ADR-044 and ADR-045 must share the exact atomic lock and local-Docker contract",
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
    and all(marker in enter_lock for marker in (
        "harness       = $HarnessId",
        "runId         = $RunId",
        "processId     = $PID",
        "$lockStream.Flush($true)",
    ))
    and "$Lock.Stream.Dispose()" in exit_lock
    and exit_lock.index("$Lock.Stream.Dispose()")
        < exit_lock.index("Remove-Item -LiteralPath $expectedPath -Force"),
    "ADR-045 must atomically create, exclusively hold, validate, and remove the exact root lock",
)

non_call_provider_selectors = {
    "MARKET_PROVIDER",
    "ANALYST_PROVIDER",
    "SP500_HISTORY_PROVIDER",
    "MARKET_BOARD_PROVIDER",
    "METHODOLOGY_PROVIDER",
    "INSTITUTION_DIRECTORY_PROVIDER",
    "ANALYST_DIRECTORY_PROVIDER",
    "MARKET_MAP_PROVIDER",
    "MARKET_TREEMAP_PROVIDER",
}
provider_source_paths = {
    path for path in Path("apps/web/src").rglob("*")
    if path.suffix in {".ts", ".tsx"}
    and not re.search(r"\.(?:test|spec)\.[^.]+$", path.name)
    and "__tests__" not in path.parts
}
web_provider_source = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted(provider_source_paths)
)
observed_dot_selectors = set(
    re.findall(
        r"process\s*\.\s*env\s*\.\s*([A-Z0-9_]+_PROVIDER)\b",
        web_provider_source,
    )
)
observed_bracket_selectors = set(
    re.findall(
        r'''process\s*\.\s*env\s*\[\s*["']([A-Z0-9_]+_PROVIDER)["']\s*\]''',
        web_provider_source,
    )
)
destructured_provider_selectors = set()
for fields in re.findall(
    r"(?:const|let|var)\s*\{([^}]*)\}\s*=\s*process\s*\.\s*env",
    web_provider_source,
):
    destructured_provider_selectors.update(
        re.findall(r"\b([A-Z0-9_]+_PROVIDER)\b", fields)
    )
provider_tokens = set(
    re.findall(r"\b[A-Z][A-Z0-9_]+_PROVIDER\b", web_provider_source)
)
all_provider_selectors = non_call_provider_selectors | {
    "CALL_AUDIT_PROVIDER"
}

web_environment_sections = []
for start_marker, end_marker in (
    (
        "$buildEnvironment = @{",
        "\n        Invoke-WithProcessEnvironment $buildEnvironment",
    ),
    ("$webEnvironment = @{", "\n\n    Write-Host \"[6/7]"),
    (
        "$playwrightEnvironment = @{",
        "\n    Invoke-WithProcessEnvironment $playwrightEnvironment",
    ),
):
    web_environment_sections.append(
        environment_section(harness, start_marker, end_marker)
    )
require(
    observed_dot_selectors == all_provider_selectors
    and provider_tokens == all_provider_selectors
    and not observed_bracket_selectors
    and not destructured_provider_selectors
    and all(
        all(
            len(re.findall(
                rf'(?m)^\s*{selector}\s*=\s*"fixture"\s*$', section
            )) == 1
            for section in web_environment_sections
        )
        for selector in non_call_provider_selectors
    )
    and all(
        len(re.findall(
            r'(?m)^\s*CALL_AUDIT_PROVIDER\s*=\s*"api"\s*$', section
        )) == 1
        for section in web_environment_sections
    ),
    "ADR-045 must force the exact current non-call web providers to fixture in all child environments",
)

java_probe = environment_section(
    harness, "$javaProbeEnvironment = @{", "\n    $javaSettingsOutput"
)
maven_build = environment_section(
    harness, "$mavenBuildEnvironment = @{", "\n    $mavenVersionOutput"
)
node_probe = environment_section(
    harness, "$nodeProbeEnvironment = @{", "\n    $nodeVersionOutput"
)
api_environment = environment_section(
    harness, "$apiEnvironment = @{", '\n\n    Write-Host "[5/7]'
)
docker_environment = environment_section(
    harness, "$dockerEnvironment = @{", "\n    $dockerInfo ="
)
compose_environment = environment_section(
    harness, "$composeEnvironment = @{", '\n\n    Write-Host "[4/7]'
)
require_exact_assignments(
    java_probe,
    {
        "JAVA_TOOL_OPTIONS": "$null",
        "JDK_JAVA_OPTIONS": "$null",
        "_JAVA_OPTIONS": "$null",
        "CLASSPATH": "$null",
    },
    "ADR-045 Java probe",
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
    "ADR-045 Maven",
)
require_exact_assignments(
    node_probe,
    {"NODE_OPTIONS": "$null", "NODE_PATH": "$null"},
    "ADR-045 Node probe",
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
        "APP_PROVIDERS_MARKET": '"fixture"',
        "APP_PROVIDERS_ANALYST": '"fixture"',
    },
    "ADR-045 API config/logging/JVM/provider",
)
docker_pin_assignments = {
    "DOCKER_CONTEXT": "$null",
    "DOCKER_HOST": "$dockerEndpoint",
    "DOCKER_TLS_VERIFY": "$null",
    "DOCKER_CERT_PATH": "$null",
}
require_exact_assignments(
    docker_environment, docker_pin_assignments, "ADR-045 Docker probe"
)
require_exact_assignments(
    compose_environment, docker_pin_assignments, "ADR-045 Compose"
)
spring_json = environment_section(
    harness, "$springApplicationJson = @{", "\n    $apiEnvironment = @{"
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
    all(marker in compact(spring_json) for marker in (
        'app = @{ providers = @{ market = "fixture" analyst = "fixture" }',
        '"public-data" = @{ sec = @{ enabled = $false',
    ))
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
    "ADR-045 Spring JSON providers or inherited logging/request-detail sanitization changed",
)
node_child_assignments = {
    "NEXT_PUBLIC_API_BASE_URL": '""',
    "NODE_OPTIONS": '""',
    "NODE_PATH": "$null",
    "NODE_USE_ENV_PROXY": "$null",
    "HTTP_PROXY": "$null",
    "HTTPS_PROXY": "$null",
    "ALL_PROXY": "$null",
    "NO_PROXY": "$null",
}
for child_name, section in zip(
    ("build", "web", "playwright"),
    web_environment_sections,
    strict=True,
):
    require_exact_assignments(
        section,
        node_child_assignments,
        f"ADR-045 {child_name} Node child",
    )
    environment_name = f"${child_name}Environment"
    conversion = (
        f"{environment_name} = ConvertTo-ProcessEnvironmentMap "
        f"{environment_name}"
    )
    removal = "Add-InheritedEnvironmentRemovals"
    proxy_pattern = "'^(?:HTTP|HTTPS|ALL|NO)_PROXY$'"
    require(
        section.count(conversion) == 1
        and section.count(removal) == 1
        and section.count(proxy_pattern) == 1
        and section.index(conversion)
            < section.index(removal)
            < section.index(proxy_pattern),
        f"ADR-045 {child_name} child must remove inherited proxy-name case variants after OS-aware map conversion",
    )

next_config = next_config_path.read_text(encoding="utf-8")
gitignore = gitignore_path.read_text(encoding="utf-8")
tsconfig = json.loads(tsconfig_path.read_text(encoding="utf-8"))
pom_root = ET.parse(pom_path).getroot()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
pom_properties = pom_root.find("m:properties", namespace)
pom_build = pom_root.find("m:build", namespace)
require(
    tsconfig.get("exclude")
        == ["node_modules", ".wsr-local-full-stack-*"]
    and gitignore.splitlines().count(
        "apps/web/.wsr-local-full-stack-*/"
    ) == 1
    and gitignore.splitlines().count(
        "/.wsr-local-acceptance.lock"
    ) == 1
    and "apps/web/.next-wsr-full-stack-*/" not in gitignore
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
    "ADR-045 exact TypeScript mirror exclusion, Git ignores, or Maven output boundary changed",
)

mirror_function = powershell_function(
    harness, "New-AcceptanceWebMirror"
)
mirror_without_continuations = re.sub(
    r"`(?:\r\n|\n|\r)[ \t]*", "", mirror_function
)
mirror_function_compact = compact(mirror_without_continuations)
copy_item_commands = tuple(
    compact(command)
    for command in re.findall(
        r"(?im)^[ \t]*Copy-Item\b[^\r\n]*",
        mirror_without_continuations,
    )
)
expected_copy_item_commands = (
    'Copy-Item -LiteralPath (Join-Path $SourceWebDirectory $fileName) -Destination (Join-Path $MirrorWebDirectory $fileName)',
    'Copy-Item -LiteralPath (Join-Path $SourceWebDirectory "src") -Destination (Join-Path $MirrorWebDirectory "src") -Recurse',
    'Copy-Item -LiteralPath $SourceFixturesDirectory -Destination (Join-Path $MirrorRoot "fixtures/v1") -Recurse',
)
copy_item_force_parameter = re.compile(
    r"(?i)(?<![\w-])-fo(?:r(?:c(?:e)?)?)?(?=$|[\s:=])"
)
require(
    copy_item_commands == expected_copy_item_commands
    and len(re.findall(
        r"(?i)\bCopy-Item\b", mirror_without_continuations
    )) == 3
    and mirror_function.count("-Recurse") == 2
    and all(marker in mirror_function_compact for marker in (
        "@(Get-ChildItem -LiteralPath $MirrorRoot -Force).Count -eq 0",
        "[IO.Directory]::CreateDirectory($MirrorWebDirectory) | Out-Null",
        'foreach ($fileName in @( "package.json", "next.config.ts", "next-env.d.ts", "tsconfig.json" ))',
        '-LiteralPath (Join-Path $SourceWebDirectory $fileName) -Destination (Join-Path $MirrorWebDirectory $fileName)',
        '-LiteralPath (Join-Path $SourceWebDirectory "src") -Destination (Join-Path $MirrorWebDirectory "src") -Recurse',
        '-LiteralPath $SourceFixturesDirectory -Destination (Join-Path $MirrorRoot "fixtures/v1") -Recurse',
    ))
    and ".env" not in mirror_function.casefold()
    and "*" not in mirror_function
    and not any(
        copy_item_force_parameter.search(command)
        for command in copy_item_commands
    )
    and not any("@" in command for command in copy_item_commands)
    and "Get-ChildItem -LiteralPath $SourceWebDirectory" not in mirror_function,
    "ADR-045 web mirror must remain an exact secret-free source/fixture allowlist",
)

require(
    harness.count("$nextEnvironmentPath") == 2
    and harness.count("$typescriptConfigPath") == 2
    and harness.count(
        '$webMirrorRoot = Join-Path $webDirectory ".wsr-local-full-stack-$runId"'
    ) == 1
    and harness.count(
        '$mirroredWebDirectory = Join-Path $webMirrorRoot "apps/web"'
    ) == 1
    and harness.count(
        '$nextDistributionDirectory = Join-Path $acceptanceWebDirectory ".next"'
    ) == 1
    and harness.count(
        '@($nextCliPath, "build", $acceptanceWebDirectory)'
    ) == 1
    and harness.count(
        "WorkingDirectory       = $acceptanceWebDirectory"
    ) == 1
    and all(old_marker not in harness for old_marker in (
        "Get-RepositoryAcceptanceMutexName",
        "WSR_ACCEPTANCE_NEXT_DIST_DIR",
        ".next-wsr-full-stack-",
        "Restore-ExactFileBytes",
        "Remove-VerifiedWebBuildDirectory",
        "[IO.File]::WriteAllBytes(",
    ))
    and "WSR_ACCEPTANCE_NEXT_DIST_DIR" not in next_config
    and "distDir: acceptanceDistDir" not in next_config,
    "ADR-045 default build must use standard .next only inside the source mirror without touching caller files",
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
    "Invoke-WithProcessEnvironment $dockerEnvironment {", docker_info + 1
)
temporary_owned_initial = harness.index(
    "$temporaryDirectoryOwned = $false"
)
web_mirror_owned_initial = harness.index("$webMirrorOwned = $false")
temporary_create = harness.index(
    "New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null",
    docker_compose_version,
)
temporary_owned = harness.index(
    "$temporaryDirectoryOwned = $true", temporary_create
)
package_gate = harness.index("if (-not $SkipPackage)", temporary_owned)
jar_assertion = harness.index(
    "Assert-Condition (Test-Path -LiteralPath $jarPath -PathType Leaf)",
    package_gate,
)
web_build_gate = harness.index("if (-not $SkipWebBuild)", jar_assertion)
mirror_create = harness.index(
    "New-Item -ItemType Directory -Path $webMirrorRoot | Out-Null",
    web_build_gate,
)
mirror_owned = harness.index("$webMirrorOwned = $true", mirror_create)
mirror_populate = harness.index(
    "New-AcceptanceWebMirror", mirror_owned
)
production_build = harness.index(
    '@($nextCliPath, "build", $acceptanceWebDirectory)',
    mirror_populate,
)
compose_environment_start = harness.index(
    "$composeEnvironment = @{", production_build
)
compose_up = harness.index(
    "Invoke-WithProcessEnvironment $composeEnvironment {",
    compose_environment_start,
)
compose_up_command = harness.index(
    '"up", "--detach", "--wait", "--wait-timeout", "90", "postgres"',
    compose_up,
)
api_start = harness.index(
    "$apiProcess = Invoke-WithProcessEnvironment $apiEnvironment",
    compose_up,
)
web_start = harness.index(
    "$webProcess = Invoke-WithProcessEnvironment $webEnvironment",
    api_start,
)
playwright_run = harness.index(
    "Invoke-WithProcessEnvironment $playwrightEnvironment", web_start
)
compose_exec = harness.index(
    "$databaseCountResult = Invoke-WithProcessEnvironment $composeEnvironment",
    playwright_run,
)
main_finally = harness.rindex("finally {")
compose_down = harness.index(
    "$composeCleanupExitCode = Invoke-WithProcessEnvironment $composeEnvironment",
    main_finally,
)
mirror_cleanup_gate = harness.index(
    "if ($webMirrorOwned)", compose_down
)
mirror_cleanup = harness.index(
    "Remove-VerifiedWebMirrorDirectory", mirror_cleanup_gate
)
mirror_released = harness.index(
    "$webMirrorOwned = $false", mirror_cleanup
)
temporary_cleanup_gate = harness.index(
    "if ($temporaryDirectoryOwned)", mirror_released
)
temporary_cleanup = harness.index(
    "Remove-VerifiedTemporaryDirectory $temporaryDirectory",
    temporary_cleanup_gate,
)
temporary_released = harness.index(
    "$temporaryDirectoryOwned = $false", temporary_cleanup
)
lock_exit_call = harness.rindex(
    "Exit-RepositoryAcceptanceLock $repositoryLock $repositoryRoot"
)
require(
    temporary_owned_initial < lock_enter_call
    and web_mirror_owned_initial < lock_enter_call
    and lock_enter_call < docker_select < docker_assert
    < docker_environment_start < docker_info < docker_compose_version
    < temporary_create < temporary_owned < package_gate < jar_assertion
    < web_build_gate < mirror_create < mirror_owned < mirror_populate
    < production_build < compose_environment_start < compose_up
    < compose_up_command < api_start < web_start < playwright_run
    < compose_exec
    < main_finally < compose_down < mirror_cleanup_gate
    < mirror_cleanup < mirror_released < temporary_cleanup_gate
    < temporary_cleanup < temporary_released < lock_exit_call,
    "ADR-045 lock, pinned Docker, owned outputs, build/run/evidence, cleanup, or unlock order changed",
)
require(
    "New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null\n    $temporaryDirectoryOwned = $true"
        in harness
    and "New-Item -ItemType Directory -Path $webMirrorRoot | Out-Null\n        $webMirrorOwned = $true"
        in harness
    and harness.count(
        "New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null"
    ) == 1
    and harness.count(
        "New-Item -ItemType Directory -Path $webMirrorRoot | Out-Null"
    ) == 1
    and harness.count("$temporaryDirectoryOwned = $true") == 1
    and harness.count("$webMirrorOwned = $true") == 1
    and harness.count("if ($temporaryDirectoryOwned)") == 1
    and harness.count("if ($webMirrorOwned)") == 1
    and "Remove-VerifiedTemporaryDirectory $temporaryDirectory"
        not in harness[lock_enter_call:temporary_create]
    and "Remove-VerifiedWebMirrorDirectory"
        not in harness[lock_enter_call:mirror_create],
    "ADR-045 must never adopt or delete pre-existing exact-name temp or mirror paths",
)
temporary_cleanup_function = powershell_function(
    harness, "Remove-VerifiedTemporaryDirectory"
)
mirror_cleanup_function = powershell_function(
    harness, "Remove-VerifiedWebMirrorDirectory"
)
require(
    all(marker in temporary_cleanup_function for marker in (
        "[IO.Path]::GetTempPath()",
        "$resolvedPath.StartsWith($temporaryRootWithSeparator, $comparison)",
        "^wsr-local-full-stack-[a-f0-9]{12}$",
        "Remove-Item -LiteralPath $resolvedPath -Recurse -Force",
    ))
    and all(marker in mirror_cleanup_function for marker in (
        "$parent.Equals($resolvedWebDirectory, $comparison)",
        "^\\.wsr-local-full-stack-[a-f0-9]{12}$",
        "[IO.FileAttributes]::ReparsePoint",
        "Remove-Item -LiteralPath $resolvedPath -Recurse -Force",
    )),
    "ADR-045 cleanup must remain constrained to its validated OS-temp child and direct non-reparse web mirror",
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
    "ADR-045 Docker info/version/up/exec/down must stay pinned to the selected local endpoint",
)

require(
    'Join-Path $repositoryRoot ".env"' not in harness
    and 'CALL_AUDIT_PROVIDER   = "fixture"' not in harness
    and 'CALL_AUDIT_PROVIDER     = "fixture"' not in harness
    and 'SEC_PROVIDER_ENABLED          = "true"' not in harness
    and 'OPERATOR_API_ENABLED          = "true"' not in harness
    and 'SERVER_ADDRESS                = "0.0.0.0"' not in harness
    and '"http://localhost' not in harness
    and '$webLoopbackBaseUrl = "http://127.0.0.1:$webPort"' in harness
    and "PLAYWRIGHT_BASE_URL     = $webLoopbackBaseUrl" in harness
    and "$apiHttpHandler.UseProxy = $false" in harness
    and "$webHttpHandler.UseProxy = $false" in harness
    and "https://data.sec.gov" not in harness
    and "Invoke-WebRequest" not in harness
    and "Invoke-RestMethod" not in harness
    and "curl " not in harness
    and "wget " not in harness,
    "ADR-045 must remain loopback-only, root-env independent, and provider-off",
)
require(
    "^wsr-fullstack-[0-9]+-[a-f0-9]{8}$" in harness
    and "^wsr-local-full-stack-[a-f0-9]{12}$" in harness
    and r"^\.wsr-local-full-stack-[a-f0-9]{12}$" in harness
    and "[IO.FileAttributes]::ReparsePoint" in powershell_function(
        harness, "Remove-VerifiedWebMirrorDirectory"
    )
    and "Invoke-WithProcessEnvironment" in harness
    and "Get-SanitizedLogTail" in harness
    and "finally {" in harness,
    "ADR-045 resource ownership validation or failure cleanup changed",
)

playwright_config = playwright_config_path.read_text(encoding="utf-8")
runtime_assertions = runtime_assertions_path.read_text(encoding="utf-8")
require(
    hashlib.sha256(playwright_config.encode("utf-8")).hexdigest()
        == "592b7414e438fef6b0a74687fb842590aea43e52b4eed9c5b581312bc334dc15"
    and hashlib.sha256(runtime_assertions.encode("utf-8")).hexdigest()
        == "f0afcf6aaedb03eae69e1a5ef0c7453c723c297a73f7532ccc989d9ba750cda8",
    "Post-ADR-045 browser current-byte custody changed",
)
adr046_config_declarations = (
    'const localProductionHttps = process.env.PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS;\n'
    'const rehearsalNoRetries = process.env.PLAYWRIGHT_REHEARSAL_NO_RETRIES;\n'
)
adr046_config_validation = (
    'if (localProductionHttps !== undefined && localProductionHttps !== "true") {\n'
    '  throw new Error("PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS must be exactly true when configured.");\n'
    '}\n'
    'if (rehearsalNoRetries !== undefined && rehearsalNoRetries !== "true") {\n'
    '  throw new Error("PLAYWRIGHT_REHEARSAL_NO_RETRIES must be exactly true when configured.");\n'
    '}\n'
)
adr046_current_retries = (
    '  retries: rehearsalNoRetries === "true" ? 0 : process.env.CI ? 2 : 0,\n'
)
adr045_retries = '  retries: process.env.CI ? 2 : 0,\n'
adr046_ignore_https_errors = (
    '    ...(localProductionHttps === "true" ? { ignoreHTTPSErrors: true } : {}),\n'
)
require(
    playwright_config.count(adr046_config_declarations) == 1
    and playwright_config.count(adr046_config_validation) == 1
    and playwright_config.count(adr046_current_retries) == 1
    and playwright_config.count(adr045_retries) == 0
    and playwright_config.count(adr046_ignore_https_errors) == 1,
    "ADR-046 Playwright delta changed before historical projection",
)
playwright_config = (
    playwright_config.replace(adr046_config_declarations, "", 1)
    .replace(adr046_config_validation, "", 1)
    .replace(adr046_current_retries, adr045_retries, 1)
    .replace(adr046_ignore_https_errors, "", 1)
)
adr046_runtime_wait = (
    '    // Production Server Actions can traverse a TLS reverse proxy before the\n'
    '    // refreshed RSC payload commits. Keep this helper attached to the actual\n'
    '    // action result instead of making each evidence test race that commit.\n'
    '    await expect(page.locator("html")).toHaveAttribute("lang", "en", { timeout: 15_000 });\n'
)
require(
    runtime_assertions.count(adr046_runtime_wait) == 1,
    "ADR-046 runtime assertion delta changed before historical projection",
)
runtime_assertions = runtime_assertions.replace(
    adr046_runtime_wait, "", 1
)
require(
    hashlib.sha256(playwright_config.encode("utf-8")).hexdigest()
        == "f0691367c9d75e2dec63e96e8de580269529ad05b7bf291a4e6c11b78d9a63ad"
    and hashlib.sha256(runtime_assertions.encode("utf-8")).hexdigest()
        == "3f4c93d5aad17e6b8b28cb633c8f1300551701d879d78f5d82065b359175b764",
    "ADR-046 virtual reverse projection changed from exact ADR-045 bytes",
)
specs = {path: path.read_text(encoding="utf-8") for path in spec_paths}
require(
    'process.env.PLAYWRIGHT_EXTERNAL_SERVER === "true"'
        in playwright_config
    and "webServer: externallyManagedWebServer" in playwright_config
    and "? undefined" in playwright_config
    and playwright_config.count('"--no-proxy-server"') == 1
    and 'localProductionHttp === "true"' in playwright_config
    and '? { launchOptions: { args: ["--no-proxy-server"] } }'
        in playwright_config
    and 'const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000"'
        in playwright_config
    and "fullyParallel: true" in playwright_config
    and "retries: process.env.CI ? 2 : 0" in playwright_config
    and "workers: process.env.CI ? 1 : undefined" in playwright_config
    and set(re.findall(
        r'name: "(chromium-(?:1440|1280|390))"', playwright_config
    )) == {"chromium-1440", "chromium-1280", "chromium-390"}
    and "PLAYWRIGHT_LOCAL_PRODUCTION_HTTP" in runtime_assertions
    and "if (localProductionHttp === undefined)" in runtime_assertions
    and 'await englishButton.press("Enter")' in runtime_assertions
    and 'localProductionHttp !== "true"' in runtime_assertions
    and 'name: "wsr_locale"' in runtime_assertions
    and "url: new URL(page.url()).origin" in runtime_assertions
    and all(
        "new URL(process.env.API_BASE_URL).origin" in source
        and 'page.on("request"' in source
        and source.count(
            "activateEnglishLocale(context, page, englishButton)"
        ) == 1
        for source in specs.values()
    ),
    "ADR-045 conditional external production server/no-proxy, default browser matrix, dynamic private-origin, or HTTP-locale boundary changed",
)

workflow = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
guard_name = "Guard disposable offline local full-stack acceptance harness"
parse_name = "Parse disposable offline local full-stack acceptance harness"
operator_guard_name = (
    "Guard default-disabled local single-operator SEC attempt API"
)
operator_parse_name = (
    "Parse disposable offline local operator API acceptance harness"
)
guard_start, guard_end, _ = workflow_step(workflow, guard_name)
parse_start, _, parse_source = workflow_step(workflow, parse_name)
operator_guard_start, operator_guard_end, _ = workflow_step(
    workflow, operator_guard_name
)
operator_parse_start, _, operator_parse_source = workflow_step(
    workflow, operator_parse_name
)
workflow_behavior = workflow
for step_name in (
    operator_guard_name,
    operator_parse_name,
    guard_name,
    parse_name,
):
    workflow_behavior = remove_workflow_step(
        workflow_behavior, step_name
    )
post_adr045_start = (
    "\n      - name: Validate Ubuntu home-server deployment contract\n"
)
post_adr045_end = "\n      - name: Install JSON Schema validator\n"
require(
    workflow_behavior.count(post_adr045_start) == 1
    and workflow_behavior.count(post_adr045_end) == 1,
    "Post-ADR-045 workflow projection markers changed",
)
post_adr045_start_index = workflow_behavior.index(post_adr045_start)
post_adr045_end_index = workflow_behavior.index(
    post_adr045_end, post_adr045_start_index
)
post_adr045_workflow_steps = workflow_behavior[
    post_adr045_start_index:post_adr045_end_index
]
require(
    post_adr045_workflow_steps.count("\n      - name: ") == 12
    and hashlib.sha256(
        post_adr045_workflow_steps.encode("utf-8")
    ).hexdigest()
        == "3b04ae774b4bd345e12f1834d1e60ac9466a957606a6f076ba586a11a07248dc",
    "ADR-046 through ADR-051 workflow step/list delta changed",
)
workflow_behavior = (
    workflow_behavior[:post_adr045_start_index]
    + workflow_behavior[post_adr045_end_index:]
)
integration_command = (
    "pnpm --dir apps/web exec playwright test call-revisions.spec.ts "
    "call-outcomes.spec.ts call-list-api.spec.ts --project=chromium-1280"
)
require(
    guard_end == parse_start
    and operator_guard_end == operator_parse_start
    and parse_source.count(harness_path.as_posix()) == 1
    and operator_parse_source.count(
        operator_harness_path.as_posix()
    ) == 1
    and parse_source.count(
        "[System.Management.Automation.Language.Parser]::ParseFile("
    ) == 1
    and operator_parse_source.count(
        "[System.Management.Automation.Language.Parser]::ParseFile("
    ) == 1
    and harness_command not in workflow_behavior
    and "pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1"
        not in workflow_behavior
    and "call-audit-integration:" in workflow_behavior
    and "CALL_AUDIT_PROVIDER: api" in workflow_behavior
    and "API_BASE_URL: http://localhost:8080" in workflow_behavior
    and compact(workflow_behavior).count(integration_command) == 1
    and workflow_behavior.count(
        "run: pnpm --dir apps/web test:e2e"
    ) == 1,
    "ADR-044/ADR-045 guards must be exactly adjacent to scoped parse steps while default 72-test and API integration behavior remain executable",
)

print(
    "Validated ADR-045 production-build PostgreSQL/Spring/Next/browser harness, "
    "shared atomic lock, pinned local Docker, isolated source/build outputs, "
    "sanitized child environments, exact offline providers, 127.0.0.1/no-proxy "
    "browser isolation, API-only evidence, owned cleanup, and scoped parse-only CI"
)
PYTHON
