python <<'PYTHON'
import hashlib
import json
import os
import shutil
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def normalized_bytes(path):
    return path.read_bytes().replace(b"\r\n", b"\n")

def digest(content):
    return hashlib.sha256(content).hexdigest()

def replace_once(source, current, historical, context):
    require(source.count(current) == 1, context)
    return source.replace(current, historical, 1)

pom_path = Path("apps/api/pom.xml")
call_list_path = Path("apps/web/e2e/call-list-api.spec.ts")
call_outcomes_path = Path("apps/web/e2e/call-outcomes.spec.ts")
call_revisions_path = Path("apps/web/e2e/call-revisions.spec.ts")
runtime_assertions_path = Path("apps/web/e2e/runtime-assertions.ts")
i18n_path = Path("apps/web/e2e/i18n.spec.ts")
sp500_history_path = Path("apps/web/e2e/sp500-history.spec.ts")
playwright_config_path = Path("apps/web/playwright.config.ts")
tsconfig_path = Path("apps/web/tsconfig.json")
current_hashes = {
    pom_path:
        "35cb3a3bc7634d14ac5f63178b17c6934ee748ce0c9912b96ae2ce3beaea3393",
    call_list_path:
        "b5b79d713561e9fcc5c31e183ffe9a1e96b53fdfd6e1ca900202e200e013eca3",
    call_outcomes_path:
        "ae90386897355661d1af5178c4f3bf9a66c28dc635e25122d52373f4132a62c4",
    call_revisions_path:
        "a9a246bda781562ffc2e0a8b46176e73351a4556b2cf15c5f0ce20515feaeef1",
    runtime_assertions_path:
        "f0afcf6aaedb03eae69e1a5ef0c7453c723c297a73f7532ccc989d9ba750cda8",
    i18n_path:
        "f741e1f592ba4cfb4b164f52ca50d15dff4f3cdb8ba9ff73b48988f1a19824eb",
    sp500_history_path:
        "ee894a8065ad7e9ad97d9f67826dd0ef44d43cbcf4d65934ce5b54636e56ac84",
    playwright_config_path:
        "592b7414e438fef6b0a74687fb842590aea43e52b4eed9c5b581312bc334dc15",
    tsconfig_path:
        "ad3a848e4b89a610fcd76f68816be0436be6090c30fb196b19e345084d276a7e",
}
require(
    len(current_hashes) == 9,
    "Pre-ADR-045 historical delta inventory changed",
)
for path, expected_hash in current_hashes.items():
    require(path.is_file(), f"Pre-ADR-045 current path is missing: {path}")
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"Pre-ADR-045 current bytes changed: {path}",
    )

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr045-current-view"
)
require(
    not projection_root.exists(),
    f"Pre-ADR-045 projection custody already exists: {projection_root}",
)
projection_root.mkdir(parents=True)
for path in current_hashes:
    custody_path = projection_root / path
    custody_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, custody_path)
(projection_root / "manifest.json").write_text(
    json.dumps(
        {path.as_posix(): value for path, value in current_hashes.items()},
        sort_keys=True,
    ),
    encoding="utf-8",
)
(projection_root / ".prepared").write_text(
    "Exact current bytes saved before the pre-ADR-045 projection\n",
    encoding="utf-8",
)

pom = normalized_bytes(pom_path)
build_directory_property = (
    b"        <wsr.build.directory>${project.basedir}/target"
    b"</wsr.build.directory>\n"
)
build_directory = b"        <directory>${wsr.build.directory}</directory>\n"
require(
    pom.count(build_directory_property) == 1
    and pom.count(build_directory) == 1,
    "ADR-045 POM delta changed",
)
pom_path.write_bytes(
    pom.replace(build_directory_property, b"", 1)
    .replace(build_directory, b"", 1)
)

locale_import = b"  activateEnglishLocale,\n"
configured_origin = (
    b"  const configuredApiOrigin = process.env.API_BASE_URL\n"
    b"    ? new URL(process.env.API_BASE_URL).origin\n"
    b"    : \"http://localhost:8080\";\n"
)
locale_call = b"  await activateEnglishLocale(context, page, englishButton);\n"
historical_locale_call = b"  await englishButton.press(\"Enter\");\n"
call_list = normalized_bytes(call_list_path)
call_list = replace_once(
    call_list, locale_import, b"", "ADR-045 call-list import delta changed"
)
call_list = replace_once(
    call_list, configured_origin, b"",
    "ADR-045 call-list API-origin delta changed",
)
call_list = replace_once(
    call_list,
    b"    if (url.origin === configuredApiOrigin) {\n"
    b"      browserApiRequests.push(request.url());\n"
    b"    }\n",
    b"    if (url.hostname === \"localhost\" && url.port === \"8080\") {\n"
    b"      browserApiRequests.push(request.url());\n"
    b"    }\n",
    "ADR-045 call-list request-observer delta changed",
)
call_list = replace_once(
    call_list, locale_call, historical_locale_call,
    "ADR-045 call-list locale delta changed",
)
call_list_path.write_bytes(call_list)

for path, context in (
    (call_outcomes_path, "call-outcomes"),
    (call_revisions_path, "call-revisions"),
):
    source = normalized_bytes(path)
    source = replace_once(
        source, locale_import, b"",
        f"ADR-045 {context} import delta changed",
    )
    source = replace_once(
        source, configured_origin, b"",
        f"ADR-045 {context} API-origin delta changed",
    )
    source = replace_once(
        source,
        b"    if (url.origin === configuredApiOrigin) "
        b"browserApiRequests.push(request.url());\n",
        b"    if (url.hostname === \"localhost\" && url.port === \"8080\") "
        b"browserApiRequests.push(request.url());\n",
        f"ADR-045 {context} request-observer delta changed",
    )
    source = replace_once(
        source, locale_call, historical_locale_call,
        f"ADR-045 {context} locale delta changed",
    )
    path.write_bytes(source)

runtime_assertions = normalized_bytes(runtime_assertions_path)
runtime_assertions = replace_once(
    runtime_assertions,
    b"import { expect, type BrowserContext, type Locator, type Page } "
    b"from \"@playwright/test\";\n",
    b"import { expect, type Locator, type Page } from \"@playwright/test\";\n",
    "ADR-045 runtime assertion import delta changed",
)
locale_function_start = b"\nexport async function activateEnglishLocale(\n"
require(
    runtime_assertions.count(locale_function_start) == 1
    and runtime_assertions.endswith(b"}\n"),
    "ADR-045 runtime locale helper delta changed",
)
runtime_assertions = runtime_assertions[
    :runtime_assertions.index(locale_function_start)
]
runtime_assertions_path.write_bytes(runtime_assertions)

i18n = normalized_bytes(i18n_path)
i18n_replacements = (
    (
        b"    await expect(page.locator(\"html\")).toHaveAttribute("
        b"\"lang\", \"en\", { timeout: 15_000 });\n",
        b"    await expect(page.locator(\"html\")).toHaveAttribute("
        b"\"lang\", \"en\");\n",
    ),
    (
        b"    if (new URL(page.url()).protocol === \"https:\") {\n"
        b"      expect(preference?.secure).toBe(true);\n"
        b"    }\n",
        b"",
    ),
    (
        b"      ignoreHTTPSErrors: testInfo.project.use.ignoreHTTPSErrors,\n",
        b"",
    ),
    (
        b"    const expectedNotFoundConsoleError =\n"
        b"      /^console error: Failed to load resource: the server responded "
        b"with a status of 404(?: \\(Not Found\\)| \\(\\))?$/;\n",
        b"    const expectedNotFoundConsoleError =\n"
        b"      \"console error: Failed to load resource: the server responded "
        b"with a status of 404 (Not Found)\";\n",
    ),
    (
        b"    expect(runtimeErrors.every((error) => "
        b"expectedNotFoundConsoleError.test(error)), runtimeErrors.join(\"\\n\"))\n"
        b"      .toBe(true);\n",
        b"    expect(runtimeErrors.every((error) => error === "
        b"expectedNotFoundConsoleError)).toBe(true);\n",
    ),
)
for current, historical in i18n_replacements:
    i18n = replace_once(
        i18n, current, historical,
        "ADR-046 i18n browser-rehearsal delta changed",
    )
i18n_path.write_bytes(i18n)

sp500_history = normalized_bytes(sp500_history_path)
sp500_history = replace_once(
    sp500_history,
    b"    }), { timeout: 15_000 }).toBe(true);\n",
    b"    })).toBe(true);\n",
    "ADR-046 S&P 500 history timeout delta changed",
)
sp500_history_path.write_bytes(sp500_history)

playwright_config = normalized_bytes(playwright_config_path)
playwright_header = (
    b"const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "
    b"\"http://localhost:3000\";\n"
    b"const externallyManagedWebServer = "
    b"process.env.PLAYWRIGHT_EXTERNAL_SERVER === \"true\";\n"
    b"const localProductionHttp = process.env.PLAYWRIGHT_LOCAL_PRODUCTION_HTTP;\n"
    b"const localProductionHttps = process.env.PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS;\n"
    b"const rehearsalNoRetries = process.env.PLAYWRIGHT_REHEARSAL_NO_RETRIES;\n\n"
    b"if (localProductionHttp !== undefined && localProductionHttp !== \"true\") {\n"
    b"  throw new Error(\"PLAYWRIGHT_LOCAL_PRODUCTION_HTTP must be exactly true when configured.\");\n"
    b"}\n"
    b"if (localProductionHttps !== undefined && localProductionHttps !== \"true\") {\n"
    b"  throw new Error(\"PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS must be exactly true when configured.\");\n"
    b"}\n"
    b"if (rehearsalNoRetries !== undefined && rehearsalNoRetries !== \"true\") {\n"
    b"  throw new Error(\"PLAYWRIGHT_REHEARSAL_NO_RETRIES must be exactly true when configured.\");\n"
    b"}\n"
)
historical_playwright_header = (
    b"const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "
    b"\"http://localhost:3000\";\n"
)
playwright_config = replace_once(
    playwright_config, playwright_header, historical_playwright_header,
    "ADR-045/ADR-046 Playwright header delta changed",
)
playwright_config = replace_once(
    playwright_config,
    b"  retries: rehearsalNoRetries === \"true\" ? 0 : "
    b"process.env.CI ? 2 : 0,\n",
    b"  retries: process.env.CI ? 2 : 0,\n",
    "ADR-046 Playwright retry delta changed",
)
playwright_config = replace_once(
    playwright_config,
    b"    ...(localProductionHttps === \"true\" ? "
    b"{ ignoreHTTPSErrors: true } : {}),\n"
    b"    ...(localProductionHttp === \"true\"\n"
    b"      ? { launchOptions: { args: [\"--no-proxy-server\"] } }\n"
    b"      : {}),\n",
    b"",
    "ADR-045/ADR-046 Playwright launch-option delta changed",
)
playwright_config = replace_once(
    playwright_config,
    b"  webServer: externallyManagedWebServer\n"
    b"    ? undefined\n"
    b"    : {\n"
    b"        command: \"pnpm dev\",\n"
    b"        url: baseURL,\n"
    b"        reuseExistingServer: !process.env.CI,\n"
    b"        timeout: 120_000,\n"
    b"      },\n",
    b"  webServer: {\n"
    b"    command: \"pnpm dev\",\n"
    b"    url: baseURL,\n"
    b"    reuseExistingServer: !process.env.CI,\n"
    b"    timeout: 120_000,\n"
    b"  },\n",
    "ADR-045 Playwright server delta changed",
)
playwright_config_path.write_bytes(playwright_config)

tsconfig = normalized_bytes(tsconfig_path)
tsconfig = replace_once(
    tsconfig,
    b'  "exclude": ["node_modules", ".wsr-local-full-stack-*"]\n',
    b'  "exclude": ["node_modules"]\n',
    "ADR-045 TypeScript exclusion delta changed",
)
tsconfig_path.write_bytes(tsconfig)

historical_hashes = {
    pom_path:
        "450d0c7202acf8cd69ad8f9e6ad551904d6f16c5238a053ea7bb7122e5799484",
    call_list_path:
        "ce8b2978d260fcc5caf3db877d232cd5b6bcd57eb2dd2c8c04287d49c37552d9",
    call_outcomes_path:
        "4fe15ad5e9bf6934e817e68a28c92d3d7916721370cc46040a68aa8aded44a3b",
    call_revisions_path:
        "cbfcb9c0c0be47013a40b1b6b5826665375500f41bb077ccf564a8518e0e4c8f",
    runtime_assertions_path:
        "c56b4508f059b948f9371ae031af3e095d33f09b6467d14d78ce15ee3d7a7fa1",
    i18n_path:
        "84bc474f54a552672f0fc8ff0c5bfe381b640e2ba9f742eee763039749644a52",
    sp500_history_path:
        "a5ce688a3d62f48357bc454df82e01be1e8c722f061392e5be2e894b5843fd54",
    playwright_config_path:
        "069512404a29749db1e168c8aca3adc2b98b28453c9dd9882d6c838338725c1d",
    tsconfig_path:
        "a1b145f809038d72826168f4a31f0ee8c48fe5ad451c8b75751499ee6a543da0",
}
for path, expected_hash in historical_hashes.items():
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"Pre-ADR-045 historical reverse projection changed: {path}",
    )
print(
    "Projected exact pre-ADR-045 POM and eight web test/config surfaces"
)
PYTHON
