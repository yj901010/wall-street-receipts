python <<'PYTHON'
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

web_source_root = Path("apps/web/src")
i18n_directory = web_source_root / "lib/i18n"
required_core_paths = {
    i18n_directory / "config.ts",
    i18n_directory / "messages.ts",
    i18n_directory / "server.ts",
    web_source_root / "app/actions/locale.ts",
    web_source_root / "app/layout.tsx",
    web_source_root / "app/not-found.tsx",
    web_source_root / "components/locale-provider.tsx",
    web_source_root / "components/locale-switcher.tsx",
    web_source_root / "components/site-header.tsx",
}
missing_core_paths = sorted(
    (path.as_posix() for path in required_core_paths if not path.is_file())
)
require(
    not missing_core_paths,
    f"Missing bilingual SSR production boundaries: {missing_core_paths}",
)

discovered_i18n_paths = {
    path
    for path in i18n_directory.rglob("*")
    if path.is_file()
    and path.suffix in {".ts", ".tsx"}
    and ".test." not in path.name
    and ".spec." not in path.name
}
require(
    {i18n_directory / name for name in ("config.ts", "messages.ts", "server.ts")}
    <= discovered_i18n_paths,
    "Bilingual core discovery must retain config, messages, and server boundaries",
)
required_catalog_paths = {
    web_source_root / "app/analysts/messages.ts",
    web_source_root / "app/calls/messages.ts",
    web_source_root / "app/institutions/messages.ts",
    web_source_root / "app/market/messages.ts",
    web_source_root / "app/markets/sp500/messages.ts",
    web_source_root / "app/methodology/messages.ts",
    web_source_root / "app/screener/messages.ts",
    web_source_root / "components/dashboard-messages.ts",
    web_source_root / "components/market-map-messages.ts",
}
discovered_catalog_paths = {
    path
    for path in web_source_root.rglob("*.ts")
    if (
        path.name == "messages.ts"
        or path.name.endswith("-messages.ts")
    )
}
discovered_catalog_paths.discard(i18n_directory / "messages.ts")
require(
    required_catalog_paths <= discovered_catalog_paths,
    "Missing required bilingual route/component catalogs: "
    f"{sorted(path.as_posix() for path in required_catalog_paths - discovered_catalog_paths)}",
)
localization_paths = (
    discovered_i18n_paths
    | discovered_catalog_paths
    | required_core_paths
)
localization_sources = {
    path: path.read_text(encoding="utf-8") for path in localization_paths
}

config_path = i18n_directory / "config.ts"
messages_path = i18n_directory / "messages.ts"
server_path = i18n_directory / "server.ts"
action_path = web_source_root / "app/actions/locale.ts"
layout_path = web_source_root / "app/layout.tsx"
root_not_found_path = web_source_root / "app/not-found.tsx"
provider_path = web_source_root / "components/locale-provider.tsx"
switcher_path = web_source_root / "components/locale-switcher.tsx"
header_path = web_source_root / "components/site-header.tsx"

config_source = localization_sources[config_path]
messages_source = localization_sources[messages_path]
server_source = localization_sources[server_path]
action_source = localization_sources[action_path]
layout_source = localization_sources[layout_path]
root_not_found_source = localization_sources[root_not_found_path]
provider_source = localization_sources[provider_path]
switcher_source = localization_sources[switcher_path]
header_source = localization_sources[header_path]

compact_config = re.sub(r"\s+", "", config_source)
require(
    'SUPPORTED_LOCALES=["ko","en"]asconst' in compact_config
    and 'DEFAULT_LOCALE:Locale="ko"' in compact_config,
    "Presentation locales must be exactly ordered ko, en with Korean default",
)
require(
    'LOCALE_COOKIE_NAME="wsr_locale"' in compact_config,
    "Locale cookie name must remain exactly wsr_locale",
)
max_age_match = re.search(
    r"LOCALE_COOKIE_MAX_AGE_SECONDS\s*=\s*([^;]+);",
    config_source,
)
require(max_age_match is not None, "Missing locale cookie Max-Age constant")
max_age_expression = re.sub(r"\s+", "", max_age_match.group(1))
require(
    max_age_expression in {"31536000", "60*60*24*365"},
    f"Locale cookie must expire in exactly one year: {max_age_expression}",
)
require(
    "httpOnly:true" in compact_config
    and "maxAge:LOCALE_COOKIE_MAX_AGE_SECONDS" in compact_config
    and 'path:"/"' in compact_config
    and 'sameSite:"lax"' in compact_config,
    "Locale cookie base options must lock HttpOnly/Path/SameSite/Max-Age",
)
require(
    "returnisLocale(value)?value:DEFAULT_LOCALE;" in compact_config,
    "Missing and unsupported locale values must resolve to Korean",
)
require(
    "if(!isLocale(value)){thrownewError(" in compact_config,
    "Locale mutation parsing must reject unsupported input",
)

compact_server = re.sub(r"\s+", "", server_source)
require(
    'from"next/headers"' in compact_server
    and "constcookieStore=awaitcookies();" in compact_server
    and "parseLocale(cookieStore.get(LOCALE_COOKIE_NAME)?.value)" in compact_server,
    "SSR locale must resolve only through the exact server cookie boundary",
)
require(
    "headers(" not in compact_server.lower()
    and "accept-language" not in compact_server.lower(),
    "SSR locale must not infer request-language headers",
)

compact_action = re.sub(r"\s+", "", action_source)
require(
    action_source.lstrip().startswith('"use server";')
    and 'constlocaleEntries=formData.getAll("locale");' in compact_action
    and "if(localeEntries.length!==1){thrownewError(" in compact_action
    and "requireLocale(localeEntries[0])" in compact_action
    and "cookieStore.set(LOCALE_COOKIE_NAME,locale,{" in compact_action
    and "...LOCALE_COOKIE_OPTIONS" in compact_action
    and 'secure:process.env.NODE_ENV==="production"' in compact_action,
    "Locale action must validate exact input and set the production-safe cookie",
)
require(
    not any(
        token in compact_action.lower()
        for token in ("redirect(", "router.", "window.location", "history.")
    ),
    "Locale mutation must not redirect or mutate URL state",
)

compact_layout = re.sub(r"\s+", "", layout_source)
require(
    "constlocale=awaitgetLocale();" in compact_layout
    and "<htmllang={locale}" in compact_layout
    and "<LocaleProviderlocale={locale}>" in compact_layout
    and "getCommonMessages(awaitgetLocale())" in compact_layout,
    "Root layout and metadata must share the resolved SSR locale",
)
compact_root_not_found = re.sub(r"\s+", "", root_not_found_source)
require(
    "getCommonMessages(awaitgetLocale()).notFound" in compact_root_not_found
    and "<SiteHeader/>" in compact_root_not_found
    and "messages.eyebrow" in root_not_found_source
    and "messages.title" in root_not_found_source
    and "messages.body" in root_not_found_source
    and 'href="/"' in root_not_found_source
    and 'href="/calls"' in root_not_found_source
    and "DEMO" not in root_not_found_source,
    "Root not-found must be locale-aware, mode-neutral, and route-stable",
)
compact_messages = re.sub(r"\s+", "", messages_source)
require(
    'NAVIGATION_ITEMS=[' in compact_messages
    and re.search(r"[가-힣]", messages_source) is not None
    and "Record<Locale,CommonMessages>" in compact_messages
    and "ko," in compact_messages
    and "en," in compact_messages,
    "Typed colocated Korean and English application catalogs are required",
)
require(
    messages_source.count('koreanOptionLabel: "한국어"') == 2
    and messages_source.count('englishOptionLabel: "English"') == 2,
    "Locale controls must retain stable 한국어/English autonym names",
)
require(
    messages_source.count("notFound: {") == 3
    and "찾을 수 없는 경로" in messages_source
    and "페이지를 찾을 수 없습니다." in messages_source
    and "Unknown route" in messages_source
    and "Page not found." in messages_source,
    "Common catalog must type and publish exact ko/en root not-found copy",
)
navigation_match = re.search(
    r"NAVIGATION_ITEMS\s*=\s*\[(?P<body>.*?)\]\s*as const",
    messages_source,
    flags=re.DOTALL,
)
require(navigation_match is not None, "Missing closed navigation catalog keys")
navigation_items = re.findall(r'"([a-z]+)"', navigation_match.group("body"))
require(
    navigation_items
    == [
        "dashboard", "market", "calls", "institutions", "analysts",
        "maps", "screener", "methodology",
    ],
    f"Bilingual navigation keys/order changed: {navigation_items}",
)

for catalog_path in sorted(discovered_catalog_paths, key=lambda path: path.as_posix()):
    catalog_source = localization_sources[catalog_path]
    catalog_imports = re.findall(
        r'from\s+["\']([^"\']+)["\']',
        catalog_source,
    )
    require(
        catalog_imports == ["@/lib/i18n/config"],
        f"Route catalog must import only the Locale type: "
        f"{catalog_path} -> {catalog_imports}",
    )
    require(
        "Record<Locale," in catalog_source
        and re.search(r"\bconst\s+ko\s*=", catalog_source) is not None
        and re.search(r"\bconst\s+en\s*=", catalog_source) is not None
        and re.search(r"[가-힣]", catalog_source) is not None
        and re.search(
            r"export function get[A-Za-z0-9]+Messages\(locale: Locale\)",
            catalog_source,
        ) is not None,
        f"Route catalog must be a closed typed ko/en projection: {catalog_path}",
    )
    require(
        "Record<string" not in catalog_source
        and "as any" not in catalog_source,
        f"Route catalog must not weaken its typed locale keys: {catalog_path}",
    )

compact_provider = re.sub(r"\s+", "", provider_source)
compact_switcher = re.sub(r"\s+", "", switcher_source)
require(
    '"useclient";' in compact_provider
    and "getCommonMessages(locale)" in compact_provider,
    "Client presentation context must be derived from the closed catalog",
)
require(
    '"useclient";' in compact_switcher
    and "action={setLocaleAction}" in compact_switcher
    and "useFormStatus()" in compact_switcher
    and "aria-pressed={activeLocale===locale}" in compact_switcher
    and "disabled={pending}" in compact_switcher
    and "lang={locale}" in compact_switcher
    and 'locale="ko"' in compact_switcher
    and 'locale="en"' in compact_switcher
    and 'shortLabel="KO"' in compact_switcher
    and 'shortLabel="EN"' in compact_switcher,
    "Locale control must be a semantic pending-aware exact KO/EN server form",
)
require(
    'import{useEffect,useRef}from"react"' in compact_switcher
    and "constformRef=useRef<HTMLFormElement>(null)" in compact_switcher
    and "constpreviousLocale=useRef(locale)" in compact_switcher
    and "if(previousLocale.current===locale)return;" in compact_switcher
    and 'querySelector<HTMLButtonElement>(`button[name="locale"][value="${locale}"]`)'
    in compact_switcher
    and "?.focus()" in compact_switcher
    and "previousLocale.current=locale" in compact_switcher
    and "ref={formRef}" in compact_switcher,
    "Locale switch must restore focus to the selected per-language control",
)
require(
    "useLocale()" in header_source
    and "<LocaleSwitcher/>" in re.sub(r"\s+", "", header_source)
    and "messages.navigation." in header_source,
    "Global navigation must consume the resolved locale and expose the switch",
)

required_unit_test_paths = {
    i18n_directory / "config.test.ts",
    i18n_directory / "messages.test.ts",
    i18n_directory / "server.test.ts",
    web_source_root / "app/actions/locale.test.ts",
    web_source_root / "app/layout.test.tsx",
    web_source_root / "app/not-found.test.tsx",
    web_source_root / "components/locale-provider.test.tsx",
    web_source_root / "components/site-header.test.tsx",
}
missing_unit_test_paths = sorted(
    path.as_posix() for path in required_unit_test_paths if not path.is_file()
)
require(
    not missing_unit_test_paths,
    f"Missing bilingual unit contract tests: {missing_unit_test_paths}",
)
unit_test_sources = {
    path: path.read_text(encoding="utf-8") for path in required_unit_test_paths
}
config_test_source = unit_test_sources[i18n_directory / "config.test.ts"]
require(
    'expect(SUPPORTED_LOCALES).toEqual(["ko", "en"])' in config_test_source
    and 'expect(DEFAULT_LOCALE).toBe("ko")' in config_test_source
    and "31_536_000" in config_test_source
    and 'new File([], "locale")' in config_test_source
    and 'expect(parseLocale(value)).toBe("ko")' in config_test_source,
    "Locale config tests must cover the closed set, exact year, and invalid fallback",
)
messages_test_source = unit_test_sources[i18n_directory / "messages.test.ts"]
require(
    "exact recursive parity" in messages_test_source
    and "expect(Object.keys(COMMON_MESSAGES)).toEqual([\"ko\", \"en\"])"
    in messages_test_source
    and "without translating product evidence tokens" in messages_test_source
    and "/DEMO|provenance|source path|NA/" in messages_test_source,
    "Catalog tests must lock locale parity and canonical-token isolation",
)
server_test_source = unit_test_sources[i18n_directory / "server.test.ts"]
for cookie_case in ('[undefined, "ko"]', '["fr", "ko"]', '["KO", "ko"]', '["en", "en"]'):
    require(
        cookie_case in server_test_source,
        f"Server locale tests are missing the required cookie case: {cookie_case}",
    )
action_test_source = unit_test_sources[web_source_root / "app/actions/locale.test.ts"]
for action_marker in (
    'it.each(["ko", "en"])', "secure: false", "secure: true",
    "rejects a missing locale field", "rejects unsupported input",
    "rejects duplicate locale fields", 'new File([], "locale")',
):
    require(
        action_marker in action_test_source,
        f"Locale action tests are missing the required mutation: {action_marker}",
    )
layout_test_source = unit_test_sources[web_source_root / "app/layout.test.tsx"]
require(
    "renderToStaticMarkup" in layout_test_source
    and '`<html lang="${locale}"' in layout_test_source
    and "generateMetadata()" in layout_test_source,
    "Layout tests must lock server-rendered document language and metadata",
)
root_not_found_test_source = unit_test_sources[
    web_source_root / "app/not-found.test.tsx"
]
require(
    "Korean-default mode-neutral boundary" in root_not_found_test_source
    and "페이지를 찾을 수 없습니다." in root_not_found_test_source
    and "DEMO" in root_not_found_test_source
    and "not.toBeInTheDocument()" in root_not_found_test_source
    and "selected English catalog" in root_not_found_test_source
    and "Page not found." in root_not_found_test_source
    and 'toHaveAttribute("href", "/")' in root_not_found_test_source
    and 'toHaveAttribute("href", "/calls")' in root_not_found_test_source,
    "Root not-found tests must lock ko/en copy, neutrality, and exact routes",
)
locale_provider_test_source = unit_test_sources[
    web_source_root / "components/locale-provider.test.tsx"
]
require(
    "ko:대시보드" in locale_provider_test_source
    and "en:Dashboard" in locale_provider_test_source
    and "fails closed" in locale_provider_test_source,
    "Locale provider tests must cover both catalogs and missing wiring",
)
header_test_source = unit_test_sources[
    web_source_root / "components/site-header.test.tsx"
]
require(
    "exact Korean navigation" in header_test_source
    and "renders the English catalog" in header_test_source
    and 'getByText("DEMO")' in header_test_source
    and 'submitted.get("locale")' in header_test_source
    and 'toBe("en")' in header_test_source,
    "Header tests must lock bilingual navigation, DEMO, and exact action input",
)
for focus_marker in (
    'toHaveAttribute("lang", "ko")',
    'toHaveAttribute("lang", "en")',
    "disables both choices and announces only while the Server Action is pending",
    "restores focus to the selected language after the server locale prop changes",
    'getByRole("button", { name: "English" })).toHaveFocus()',
):
    require(
        focus_marker in header_test_source,
        f"Locale control tests are missing focus/lang/pending evidence: {focus_marker}",
    )

required_page_test_paths = {
    web_source_root / "app/page.test.tsx",
    web_source_root / "app/analysts/page.test.tsx",
    web_source_root / "app/calls/page.test.tsx",
    web_source_root / "app/calls/[id]/page.test.tsx",
    web_source_root / "app/institutions/page.test.tsx",
    web_source_root / "app/maps/[universe]/page.test.tsx",
    web_source_root / "app/market/page.test.tsx",
    web_source_root / "app/markets/sp500/page.test.tsx",
    web_source_root / "app/methodology/page.test.tsx",
    web_source_root / "app/screener/page.test.tsx",
}
discovered_page_test_paths = set((web_source_root / "app").rglob("page.test.tsx"))
require(
    required_page_test_paths <= discovered_page_test_paths,
    "Missing required bilingual route tests: "
    f"{sorted(path.as_posix() for path in required_page_test_paths - discovered_page_test_paths)}",
)
for page_test_path in sorted(
    discovered_page_test_paths,
    key=lambda path: path.as_posix(),
):
    page_test_source = page_test_path.read_text(encoding="utf-8")
    require(
        "renderWithLocale" in page_test_source
        and re.search(r"[가-힣]", page_test_source) is not None
        and '"en"' in page_test_source,
        f"Route tests must exercise Korean-default and English presentation: "
        f"{page_test_path}",
    )
discovered_not_found_test_paths = set(
    (web_source_root / "app").rglob("not-found.test.tsx")
)
require(
    web_source_root / "app/not-found.test.tsx" in discovered_not_found_test_paths,
    "Recursive bilingual route tests must include the root not-found boundary",
)
for not_found_test_path in sorted(
    discovered_not_found_test_paths,
    key=lambda path: path.as_posix(),
):
    not_found_test_source = not_found_test_path.read_text(encoding="utf-8")
    require(
        "renderWithLocale" in not_found_test_source
        and re.search(r"[가-힣]", not_found_test_source) is not None
        and '"en"' in not_found_test_source,
        f"Not-found tests must exercise Korean-default and English presentation: "
        f"{not_found_test_path}",
    )

i18n_e2e_path = Path("apps/web/e2e/i18n.spec.ts")
require(i18n_e2e_path.is_file(), f"Missing bilingual raw SSR E2E: {i18n_e2e_path}")
i18n_e2e_source = i18n_e2e_path.read_text(encoding="utf-8")
for e2e_marker in (
    'test.describe("Korean-default bilingual SSR"',
    "renders Korean in raw SSR for missing or invalid preferences",
    'headers: { "accept-language": "en-US,en;q=0.9" }',
    'name: "wsr_locale"',
    'value: "fr"',
    'value: "ko"',
    'const rawKorean = await page.request.get("/")',
    'expect(await rawKorean.text()).toContain(\'<html lang="ko"\')',
    "'<html lang=\"ko\"'",
    "persists English through the real server action, reload, navigation, and a revisited context",
    'const initialUrl = "/calls?assetId=asset-spx&order=desc#calls-page-title"',
    '.getByRole("button", { name: "English" })',
    '.press("Enter")',
    "httpOnly: true", 'path: "/"', 'sameSite: "Lax"',
    "300 * 24 * 60 * 60",
    'page.request.get("/methodology")',
    "'<html lang=\"en\"'",
    "expect(canonicalLinksAfter).toEqual(canonicalLinksBefore)",
    "await page.reload()", "const storageState = await context.storageState()",
    "await browser.newContext", 'revisitedPage.goto("/market")',
    '.getByRole("button", { name: "한국어" })',
    "keeps the locale control keyboard reachable without overflowing the responsive header",
    'toHaveAttribute("lang", "ko")',
    'toHaveAttribute("lang", "en")',
    "optionHeights", "height >= 24",
    "localizes unknown routes without inventing a data mode or evidence",
    'page.goto("/route-that-is-not-published")',
    "페이지를 찾을 수 없습니다.",
    'page.goto("/another-unpublished-route")',
    "Page not found.",
    'page.locator(".mode-badge")).toHaveCount(0)',
    "expectNoPageOverflow(page)", "expectNoRuntimeErrors",
    "expect(externalRequests).toEqual([])",
):
    require(
        e2e_marker in i18n_e2e_source,
        f"Bilingual E2E is missing required SSR/revisit evidence: {e2e_marker}",
    )
require(
    i18n_e2e_source.count('getByRole("button", { name: "English" })') >= 3
    and i18n_e2e_source.count('getByRole("button", { name: "한국어" })') >= 2,
    "Bilingual E2E must keep stable 한국어/English autonym controls",
)
playwright_source = Path("apps/web/playwright.config.ts").read_text(encoding="utf-8")
for viewport_marker in (
    "viewport: { width: 1440, height: 1000 }",
    "viewport: { width: 1280, height: 900 }",
    "viewport: { width: 390, height: 844 }",
):
    require(
        viewport_marker in playwright_source,
        f"Bilingual responsive gate is missing viewport: {viewport_marker}",
    )

tokens_path = web_source_root / "styles/tokens.css"
globals_path = web_source_root / "app/globals.css"
require(tokens_path.is_file() and globals_path.is_file(), "Missing shared visual-system CSS")
tokens_source = tokens_path.read_text(encoding="utf-8")
globals_source = globals_path.read_text(encoding="utf-8")
require("color-scheme: light;" in tokens_source, "Product canvas must be light")
parsed_tokens = dict(
    re.findall(r"^\s*(--[a-z0-9-]+):\s*([^;]+);", tokens_source, flags=re.MULTILINE)
)
expected_visual_tokens = {
    "--color-canvas": "#fdfdfc",
    "--color-surface": "#ffffff",
    "--color-surface-raised": "#f4f4f2",
    "--color-surface-muted": "#f7f7f5",
    "--color-line": "#dededb",
    "--color-line-strong": "#b8b8b3",
    "--color-text": "#282827",
    "--color-text-muted": "#70706c",
    "--color-accent": "#174f78",
    "--color-positive": "#00633f",
    "--color-negative": "#a60008",
    "--color-warning": "#785b13",
    "--color-map-positive": "#c8d8d1",
    "--color-map-negative": "#e5cece",
    "--color-map-neutral": "#e4e6e4",
    "--radius-sm": "4px",
    "--radius-md": "4px",
    "--page-width": "1600px",
}
for token, expected in expected_visual_tokens.items():
    require(
        parsed_tokens.get(token) == expected,
        f"White editorial visual token changed: {token}={parsed_tokens.get(token)!r}",
    )
require(
    '"Noto Sans KR"' in parsed_tokens.get("--font-sans", "")
    and "Consolas" in parsed_tokens.get("--font-mono", ""),
    "Visual system must retain Korean UI and compact mono evidence fonts",
)
forbidden_visual_fragments = (
    "linear-gradient", "radial-gradient", "conic-gradient",
    "backdrop-filter", "glassmorphism", "neon",
)
combined_visual_source = f"{tokens_source}\n{globals_source}".lower()
for fragment in forbidden_visual_fragments:
    require(
        fragment not in combined_visual_source,
        f"White editorial system contains forbidden visual effect: {fragment}",
    )
require(
    "background: var(--color-canvas);" in globals_source
    and "border-bottom: 1px solid var(--color-line);" in globals_source
    and "outline: 2px solid var(--color-accent);" in globals_source
    and globals_source.count("font-variant-numeric: tabular-nums;") >= 2
    and globals_source.count("overflow-x: auto;") >= 3,
    "Editorial layout must retain white canvas, thin rules, focus, mono numbers, and containment",
)
for contrast_marker in (
    ".locale-switcher-option {",
    "min-width: 30px;",
    "min-height: 24px;",
    ".map-cell-metric > span {",
    ".map-cell-metric small {",
    ".map-metric-positive .map-cell-metric strong {",
    "color: var(--color-positive);",
    ".map-metric-negative .map-cell-metric strong {",
    "color: var(--color-negative);",
    ".map-metric-unavailable .map-cell-metric strong {",
    ".treemap-cell {",
    ".treemap-cell-copy span {",
    ".treemap-cell-copy small {",
    ".treemap-metric-unavailable .treemap-cell-copy span {",
):
    require(
        contrast_marker in globals_source,
        f"Editorial contrast/touch-target override is missing: {contrast_marker}",
    )
expected_contrast_rules = {
    ".map-cell-metric > span": "var(--color-text)",
    ".map-cell-metric small": "var(--color-text)",
    ".map-metric-positive .map-cell-metric strong": "var(--color-positive)",
    ".map-metric-negative .map-cell-metric strong": "var(--color-negative)",
    ".map-metric-unavailable .map-cell-metric strong": "var(--color-text)",
    ".treemap-cell": "var(--color-text)",
    ".treemap-cell-copy span": "var(--color-text)",
    ".treemap-cell-copy small": "var(--color-text)",
    ".treemap-metric-unavailable .treemap-cell-copy span": "var(--color-text)",
}
for selector, color in expected_contrast_rules.items():
    require(
        re.search(
            re.escape(selector)
            + r"\s*\{[^}]*\bcolor:\s*"
            + re.escape(color)
            + r";",
            globals_source,
            flags=re.DOTALL,
        ) is not None,
        f"Editorial colored-surface text contrast changed: {selector} -> {color}",
    )
require(
    all(
        marker in globals_source
        for marker in (
            "@media (max-width: 1120px)",
            "@media (max-width: 900px)",
            "@media (max-width: 520px)",
            "@media (prefers-reduced-motion: reduce)",
        )
    ),
    "Editorial visual system must retain responsive and reduced-motion boundaries",
)
radius_values = set(re.findall(r"border-radius:\s*([^;]+);", globals_source))
require(
    {"2px", "var(--radius-sm)"} <= radius_values
    and radius_values <= {"2px", "var(--radius-sm)", "var(--radius-md)"},
    f"Editorial controls must remain near-square: {sorted(radius_values)}",
)

forbidden_global_fragments = (
    "localstorage", "sessionstorage", "navigator.language",
    "navigator.languages", "accept-language", "geolocation",
    "@google-cloud/translate", "googletrans", "deepl", "i18next",
)
production_web_paths = tuple(
    sorted(
        (
            path
            for path in web_source_root.rglob("*")
            if path.is_file()
            and path.suffix in {".ts", ".tsx"}
            and ".test." not in path.name
            and ".spec." not in path.name
        ),
        key=lambda path: path.as_posix(),
    )
)
for source_path in production_web_paths:
    lowered = source_path.read_text(encoding="utf-8").lower()
    for fragment in forbidden_global_fragments:
        require(
            fragment not in lowered,
            f"Web locale must not use inference/storage/remote translation: "
            f"{source_path} -> {fragment}",
        )

forbidden_localization_imports = (
    ".json", "fixtures", "/providers", "calls-provider", "outcome",
    "market-board", "market-map", "market-treemap", "market-snapshot",
    "call-context", "@/app/api", "axios",
)
for source_path, source in localization_sources.items():
    imports = re.findall(r'from\s+["\']([^"\']+)["\']', source)
    for imported in imports:
        require(
            not any(fragment in imported.lower() for fragment in forbidden_localization_imports),
            f"Localization source crosses canonical/provider evidence: "
            f"{source_path} -> {imported}",
        )
    compact = re.sub(r"\s+", "", source).lower()
    require(
        "fetch(" not in compact
        and "xmlhttprequest" not in compact
        and "eventsource(" not in compact
        and "websocket(" not in compact,
        f"Localization source must not use remote translation transport: {source_path}",
    )

provider_directory = web_source_root / "lib/providers"
for provider_file in provider_directory.rglob("*.ts"):
    if ".test." in provider_file.name or ".spec." in provider_file.name:
        continue
    provider_imports = re.findall(
        r'from\s+["\']([^"\']+)["\']',
        provider_file.read_text(encoding="utf-8"),
    )
    require(
        not any(
            "/i18n/" in imported
            or "locale-provider" in imported
            or "locale-switcher" in imported
            or "actions/locale" in imported
            for imported in provider_imports
        ),
        f"Canonical provider must remain locale-independent: {provider_file}",
    )

expected_schema_names = {
    "analyst-call-revision.schema.json", "analyst-call.schema.json",
    "call-context.schema.json", "call-outcome.schema.json",
    "event-context.schema.json", "macro-observation.schema.json",
    "macro-snapshot.schema.json", "market-board.schema.json",
    "market-map.schema.json", "market-snapshot.schema.json",
    "market-treemap.schema.json", "scoring-methodology.schema.json",
    "source-document.schema.json", "source-reference.schema.json",
}
expected_fixture_names = {
    "analyst-call-revisions.json", "analyst-calls.json", "call-contexts.json",
    "call-outcomes.json", "manifest.json", "market-board.json",
    "market-map-nasdaq100.json", "market-map.json", "market-snapshots.json",
    "market-treemap-nasdaq100.json", "market-treemap-sp500.json",
    "master-data.json", "timeline-nvda.json",
}
expected_migrations = {
    "V1__baseline.sql", "V2__analyst_calls.sql",
    "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
    "V5__call_contexts.sql",
    "V6__sec_filing_catalog_captures.sql",
    "V7__sec_historical_filing_segment_captures.sql",
    "V8__sec_filing_history_collection_manifests.sql",
    "V9__sec_filing_collection_attempts.sql",
}
require(
    {path.name for path in Path("schemas").glob("*.json")}
    == expected_schema_names,
    "Bilingual presentation must not add or remove a canonical schema",
)
require(
    {path.name for path in Path("fixtures/v1").glob("*.json")}
    == expected_fixture_names,
    "Bilingual presentation must not add or remove a canonical fixture",
)
require(
    {
        path.name
        for path in Path("apps/api/src/main/resources/db/migration").glob("*.sql")
    }
    == expected_migrations,
    "Bilingual presentation must not add or remove a Flyway migration",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
openapi_paths = re.findall(r"^  (/[^:]+):\s*$", openapi_source, flags=re.MULTILINE)
require(
    openapi_paths
    == [
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    ],
    f"Bilingual presentation must not change OpenAPI paths: {openapi_paths}",
)
manifest_source = Path("fixtures/v1/manifest.json").read_text(encoding="utf-8").lower()
require(
    all(marker not in manifest_source for marker in ("wsr_locale", "i18n", "translation")),
    "Presentation locale must not become canonical manifest evidence",
)
api_source_fragments = []
for api_path in Path("apps/api/src/main").rglob("*"):
    if api_path.is_file() and api_path.suffix.lower() in {".java", ".sql", ".yml", ".yaml"}:
        api_source_fragments.append(api_path.read_text(encoding="utf-8").lower())
joined_api_source = "\n".join(api_source_fragments)
require(
    all(
        marker not in joined_api_source
        for marker in ("wsr_locale", "accept-language", "translationprovider", "/i18n")
    ),
    "Bilingual presentation must not add an API localization boundary",
)

print(
    "Validated exact Korean-default SSR/cookie/catalog wiring, locale-independent "
    "canonical providers, and no schema/fixture/manifest/OpenAPI/API/Flyway expansion"
)
PYTHON
