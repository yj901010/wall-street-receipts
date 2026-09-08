import { expect, test, type Locator, type Page } from "@playwright/test";
import {
  activateEnglishLocale,
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

const MANIFEST_ID = "cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd";
const CUTOFF = "2026-08-25T03:30:00.123456Z";
const ROUTE = "/research/sec/filing-history";
const FIXTURE_MODE = process.env.SEC_MANIFEST_AUDIT_PROVIDER !== "api";
const API_SUCCESS_FLAG = process.env.PLAYWRIGHT_SEC_MANIFEST_API_SUCCESS;
if (API_SUCCESS_FLAG !== undefined && API_SUCCESS_FLAG !== "true") {
  throw new Error("PLAYWRIGHT_SEC_MANIFEST_API_SUCCESS accepts only exact true or absence.");
}
if (
  API_SUCCESS_FLAG === "true" &&
  (
    process.env.SEC_MANIFEST_AUDIT_PROVIDER !== "api" ||
    process.env.SEC_MANIFEST_AUDIT_SYNTHETIC_DEMO_MANIFEST_ID !== MANIFEST_ID
  )
) {
  throw new Error(
    "SEC manifest API success requires exact API mode and the pinned synthetic DEMO identity.",
  );
}
const API_SUCCESS_MODE = !FIXTURE_MODE && API_SUCCESS_FLAG === "true";

function collectBrowserApiRequests(page: Page) {
  const calls: string[] = [];
  const apiOrigin = process.env.API_BASE_URL
    ? new URL(process.env.API_BASE_URL).origin
    : "http://localhost:8080";
  page.on("request", (request) => {
    if (new URL(request.url()).origin === apiOrigin) calls.push(request.url());
  });
  return calls;
}

async function submitLookup(page: Page, input: Locator) {
  const nextDocument = page.waitForResponse((response) => response.request().isNavigationRequest()
    && response.request().resourceType() === "document"
    && new URL(response.url()).pathname === ROUTE);
  await input.press("Enter");
  // The visible SSR shell can precede the end of its React stream. Do not start
  // another navigation while this document's transport is still being consumed.
  await (await nextDocument).finished();
  await page.waitForLoadState("load");
}

async function refineFromResult(page: Page, locale: "ko" | "en") {
  const disclosure = page.locator("details");
  const toggle = disclosure.locator("summary");
  await expect(disclosure).not.toHaveAttribute("open");
  await expect(toggle).toHaveText(locale === "ko" ? "조회 조건 수정" : "Edit lookup conditions");
  const before = page.url();
  await toggle.focus();
  await page.keyboard.press("Tab");
  await page.keyboard.press("Shift+Tab");
  await expectVisibleKeyboardFocus(toggle);
  await toggle.press("Enter");
  await expect(disclosure).toHaveAttribute("open", "");
  await page.keyboard.press("Tab");
  const manifest = disclosure.getByLabel("Manifest ID", { exact: true });
  const cutoff = disclosure.getByLabel(locale === "ko" ? "평가 기준 원본 조회 키(UTC)" : "Original lookup key (UTC)");
  await expectVisibleKeyboardFocus(manifest);
  await expect(manifest).toHaveValue(MANIFEST_ID);
  await expect(cutoff).toHaveValue(CUTOFF);
  await manifest.fill("b".repeat(64));
  expect(page.url()).toBe(before);
  await expect(page.getByText(MANIFEST_ID).first()).toBeVisible();
  await expectNoPageOverflow(page);
  await manifest.fill(MANIFEST_ID);
  await cutoff.fill("2026-02-29T03:30:00.123456Z");
  await submitLookup(page, cutoff);
  await expect(page.locator('p[role="alert"]')).toBeVisible();
  await expect(page.locator("details")).toHaveCount(0);
  const correction = page.getByLabel(locale === "ko" ? "평가 기준 원본 조회 키(UTC)" : "Original lookup key (UTC)");
  await expect(correction).toHaveAttribute("aria-invalid", "true");
  await expect(page.getByLabel("Manifest ID", { exact: true })).toHaveValue(MANIFEST_ID);
  await correction.fill(CUTOFF);
  await submitLookup(page, correction);
  await expect(page.locator("details")).toBeAttached();
  await expect(page.locator("details")).not.toHaveAttribute("open");
  expect([...new URL(page.url()).searchParams.entries()]).toEqual([
    ["manifestId", MANIFEST_ID], ["evaluationAsOf", CUTOFF], ["view", "summary"],
  ]);
  await expect(page.getByText("2026-08-25 12:30:00.123456 KST").first()).toBeVisible();
}

async function openFailedInputs(page: Page, locale: "ko" | "en", id: string, instant: string) {
  const disclosure = page.locator("details");
  await expect(disclosure).not.toHaveAttribute("open");
  const toggle = disclosure.locator("summary");
  await expect(toggle).toHaveText(locale === "ko"
    ? "실패한 조회의 입력값 확인·수정" : "Review or edit the failed lookup inputs");
  const before = page.url();
  await toggle.focus();
  await page.keyboard.press("Tab");
  await page.keyboard.press("Shift+Tab");
  await expectVisibleKeyboardFocus(toggle);
  await toggle.press("Enter");
  await expect(disclosure).toContainText(locale === "ko" ? "확인된 증거가 아닙니다" : "not verified evidence");
  await expect(disclosure).toContainText(locale === "ko" ? "입력한 조회 키" : "Attempted lookup keys");
  await expect(disclosure).not.toContainText(locale === "ko" ? "알려진 증거 식별자" : "Known evidence identity");
  await page.keyboard.press("Tab");
  const manifest = disclosure.getByLabel("Manifest ID", { exact: true });
  const cutoff = disclosure.getByLabel(locale === "ko" ? "평가 기준 원본 조회 키(UTC)" : "Original lookup key (UTC)");
  await expectVisibleKeyboardFocus(manifest);
  await expect(manifest).toHaveValue(id);
  await expect(cutoff).toHaveValue(instant);
  await manifest.fill("b".repeat(64));
  expect(page.url()).toBe(before);
  await expect(page.getByText(id, { exact: true })).toHaveCount(0);
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.locator(".mode-badge")).toHaveCount(0);
  await expectNoPageOverflow(page);
  await manifest.fill(id);
  return { manifest, cutoff };
}

test("keeps exact SEC manifest evidence SSR-only, bilingual, and responsive", async ({
  context,
  page,
}) => {
  const runtimeErrors = collectRuntimeErrors(page);
  const browserApiRequests = collectBrowserApiRequests(page);
  await context.clearCookies();

  const response = await page.goto("/methodology");
  expect(response?.ok()).toBe(true);
  const primary = page.getByRole("navigation", { name: "주요 탐색" });
  await primary.getByRole("link", { name: "방법론", exact: true }).focus();
  await page.keyboard.press("Tab");
  const secLink = primary.getByRole("link", { name: "SEC 증거", exact: true });
  await expect(secLink).toHaveAttribute("href", ROUTE);
  await expectVisibleKeyboardFocus(secLink);
  await expect(secLink).toBeInViewport();
  await expectNoPageOverflow(page);
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(page.getByRole("button", { name: "한국어" }));
  await page.keyboard.press("Shift+Tab");
  await expectVisibleKeyboardFocus(secLink);
  await secLink.press("Enter");
  await expect(page).toHaveURL(new RegExp(`${ROUTE}$`));
  await expect(secLink).toHaveAttribute("aria-current", "page");
  await expect(primary.locator('[aria-current="page"]')).toHaveCount(1);
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  await expect(page.getByRole("heading", { name: "SEC 제출 이력 manifest 감사" }))
    .toBeVisible();
  const form = page.getByRole("form", { name: "정확한 manifest와 기준 시각을 입력하세요." });
  await expect(form.getByLabel("Manifest ID")).toHaveAttribute("pattern", "[0-9a-f]{64}");
  await expect(form.getByLabel("평가 기준 원본 조회 키(UTC)"))
    .toHaveAttribute("type", "text");
  await expect(page.getByRole("table")).toHaveCount(0);

  if (!FIXTURE_MODE) {
    await expect(page.locator(".mode-badge")).toHaveCount(0);
    await expect(page.getByRole("link", { name: "합성 DEMO 요약 열기" })).toHaveCount(0);
  }

  if (!FIXTURE_MODE && !API_SUCCESS_MODE) {
    await form.getByLabel("Manifest ID").fill(MANIFEST_ID);
    await form.getByLabel("평가 기준 원본 조회 키(UTC)").fill(CUTOFF);
    await submitLookup(page, form.getByLabel("평가 기준 원본 조회 키(UTC)"));
    await expect(page.getByRole("heading", {
      name: "정확한 manifest 응답을 검증할 수 없습니다.",
    })).toBeVisible();
    await expect(page.getByText("합성 DEMO · 실제 SEC 자료 아님")).toHaveCount(0);
    await expect(page.getByText(MANIFEST_ID, { exact: true })).toHaveCount(0);
    const failed = await openFailedInputs(page, "ko", MANIFEST_ID, CUTOFF);
    await failed.cutoff.fill("2026-02-29T03:30:00.123456Z");
    await submitLookup(page, failed.cutoff);
    await expect(page.locator('p[role="alert"]')).toBeVisible();
    await expect(page.locator("details")).toHaveCount(0);
    await expect(page.getByLabel("Manifest ID", { exact: true })).toHaveValue(MANIFEST_ID);
    await expect(page.getByLabel("평가 기준 원본 조회 키(UTC)")).toHaveAttribute("aria-invalid", "true");
    expect([...new URL(page.url()).searchParams.keys()]).toEqual(["manifestId", "evaluationAsOf", "view"]);
    await expectNoPageOverflow(page);
    expect(browserApiRequests).toEqual([]);
    const boundaryErrors = runtimeErrors.filter((error) => error.startsWith("console error:")
      && error.includes("SEC manifest audit API summary request failed."));
    expect(boundaryErrors).toHaveLength(1);
    // A fully consumed failed SSR stream may also report this exact recoverable
    // server-render error before the normal error boundary. Admit no other error.
    const streamedErrors = runtimeErrors.filter((error) =>
      /^pageerror: Switched to client rendering because the server rendering errored:\s+SEC manifest audit API summary request failed\.$/.test(error));
    expect(streamedErrors.length).toBeLessThanOrEqual(1);
    expect(runtimeErrors).toHaveLength(1 + streamedErrors.length);
    return;
  }

  if (FIXTURE_MODE) {
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");
    await page.getByRole("link", { name: "합성 DEMO 요약 열기" }).click();
  } else {
    await page.goto(
      `${ROUTE}?manifestId=${MANIFEST_ID}`
      + `&evaluationAsOf=${encodeURIComponent(CUTOFF)}&view=summary`,
    );
    await expect(page.locator(".mode-badge")).toHaveText("DEMO");
    await expect(page.getByRole("link", { name: "합성 DEMO 요약 열기" })).toHaveCount(0);
  }
  await expect(page).toHaveURL(new RegExp(`manifestId=${MANIFEST_ID}.*view=summary`));
  await expect(page.getByText("합성 DEMO · 실제 SEC 자료 아님")).toBeVisible();
  await expect(page.getByText("ROOT_RELATIVE_SELECTED_REFERENCES_ONLY")).toBeVisible();
  await expect(page.getByText("NOT_CLAIMED")).toBeVisible();
  await expect(page.getByText(MANIFEST_ID).first()).toBeVisible();
  const cutoff = page.getByText("2026-08-25 12:30:00.123456 KST").first();
  await expect(cutoff).toBeVisible();
  await expect(cutoff).toHaveAttribute("datetime", CUTOFF);

  await page.getByRole("link", { name: "Descriptor", exact: true }).click();
  const descriptorRegion = page.getByRole("region", {
    name: "광고된 historical descriptor",
  });
  await expect(descriptorRegion).toContainText("CIK0000320193-submissions-002.json");
  await expect(descriptorRegion).toContainText("CIK0000320193-submissions-001.json");
  await expect(descriptorRegion).toContainText("SELECTED_EXACT_CAPTURE");

  // The result editor preserves exact keys but resubmits a summary, not this tab.
  await refineFromResult(page, "ko");

  await page.getByRole("link", { name: "Accession 비교" }).click();
  const comparisonRegion = page.getByRole("region", { name: "Accession occurrence 비교" });
  await expect(comparisonRegion).toContainText("MULTIPLE_OCCURRENCES_EXACT_AGREEMENT");
  await expect(comparisonRegion).toContainText("MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT");
  const occurrenceTab = page.getByRole("link", { name: "원본 occurrence" });
  await occurrenceTab.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(comparisonRegion);

  await occurrenceTab.focus();
  await occurrenceTab.press("Enter");
  const occurrenceRegion = page.getByRole("region", { name: "Manifest 원본 occurrence" });
  await expect(occurrenceRegion).toContainText(
    "https://www.sec.gov/Archives/edgar/data/320193/000032019326000001/form10q.htm",
  );
  await expect(page.locator('a[href^="https://www.sec.gov/"]')).toHaveCount(0);
  await expect(occurrenceRegion.getByText("NA").first()).toBeVisible();

  const searchBeforeLocale = new URL(page.url()).search;
  const korean = page.getByRole("button", { name: "한국어" });
  const english = page.getByRole("button", { name: "English" });
  await korean.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(english);
  await activateEnglishLocale(context, page, english);
  expect(new URL(page.url()).search).toBe(searchBeforeLocale);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("heading", { name: "SEC filing-history manifest audit" }))
    .toBeVisible();
  await expect(page.getByText("Synthetic DEMO · not observed SEC data")).toBeVisible();
  const englishPrimary = page.getByRole("navigation", { name: "Primary navigation" });
  await expect(englishPrimary.getByRole("link", { name: "SEC evidence" }))
    .toHaveAttribute("aria-current", "page");
  await expect(englishPrimary.locator('[aria-current="page"]')).toHaveCount(1);
  await expect(page.getByText(MANIFEST_ID).first()).toBeVisible();
  await expect(page.getByText("2026-08-25 12:30:00.123456 KST").first()).toBeVisible();
  await expect(page.getByText("ROOT_RECENT").first()).toBeVisible();

  await refineFromResult(page, "en");

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});

test.describe("native SEC lookup refinement", () => {
  test("resubmits exact keys from a child page through a native document GET", async ({ page }) => {
    test.skip(!FIXTURE_MODE && !API_SUCCESS_MODE, "Requires deterministic evidence, not a failed API.");
    await page.goto(`${ROUTE}?manifestId=${MANIFEST_ID}&evaluationAsOf=${encodeURIComponent(CUTOFF)}`
      + "&view=descriptors&page=1&size=1");
    const disclosure = page.locator("details");
    await expect(disclosure).not.toHaveAttribute("open");
    await disclosure.locator("summary").click();
    await expect(disclosure).toHaveAttribute("open", "");
    await expect(disclosure.getByLabel("Manifest ID", { exact: true })).toHaveValue(MANIFEST_ID);
    const nextCutoff = "2026-08-26T00:00:00.000001Z";
    await disclosure.getByLabel("평가 기준 원본 조회 키(UTC)").fill(nextCutoff);
    const submitted = page.waitForRequest((request) => request.isNavigationRequest()
      && request.resourceType() === "document" && new URL(request.url()).pathname === ROUTE);
    await disclosure.getByRole("button", { name: "정확 증거 열기" }).click();
    expect((await submitted).method()).toBe("GET");
    await expect(page.getByText("2026-08-26 09:00:00.000001 KST").first()).toBeVisible();
    expect([...new URL(page.url()).searchParams.entries()]).toEqual([
      ["manifestId", MANIFEST_ID], ["evaluationAsOf", nextCutoff], ["view", "summary"],
    ]);
    await expect(page.locator("details")).not.toHaveAttribute("open");
    await expect(page.getByText("합성 DEMO · 실제 SEC 자료 아님")).toBeVisible();
    await expectNoPageOverflow(page);
  });
});

test("fails closed for malformed and unavailable exact SEC manifest requests", async ({
  context,
  page,
}) => {
  test.skip(
    !FIXTURE_MODE && !API_SUCCESS_MODE,
    "Exact-absence assertions require fixture mode or the isolated API success stack.",
  );
  const runtimeErrors = collectRuntimeErrors(page);
  const browserApiRequests = collectBrowserApiRequests(page);
  await context.clearCookies();

  await page.goto(`${ROUTE}?manifestId=${MANIFEST_ID}&evaluationAsOf=${encodeURIComponent(CUTOFF)}`
    + "&view=summary&ticker=NVDA");
  await expect(page.locator('p[role="alert"]')).toContainText(
    "조회 주소가 닫힌 문법과 맞지 않습니다.",
  );
  await expect(page.getByText("NVDA", { exact: true })).toHaveCount(0);

  const manifestInput = page.getByLabel("Manifest ID", { exact: true });
  const cutoffInput = page.getByLabel("평가 기준 원본 조회 키(UTC)", { exact: true });
  await expect(manifestInput).toHaveValue(MANIFEST_ID);
  await expect(cutoffInput).toHaveValue(CUTOFF);
  await expect(manifestInput).not.toHaveAttribute("aria-invalid");
  await expect(cutoffInput).not.toHaveAttribute("aria-invalid");
  const impossibleDate = "2026-02-29T03:30:00.123456Z";
  await cutoffInput.fill(impossibleDate);
  await submitLookup(page, cutoffInput);
  await expect(page.locator('p[role="alert"]')).toBeVisible();
  await expect(manifestInput).toHaveValue(MANIFEST_ID);
  await expect(cutoffInput).toHaveValue(impossibleDate);
  await expect(cutoffInput).toHaveAttribute("aria-invalid", "true");
  await expect(cutoffInput).toHaveAccessibleDescription(/실제 달력에 존재하는 UTC Z 시각/);
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(new URL(page.url()).searchParams.has("ticker")).toBe(false);
  await expectNoPageOverflow(page);

  const invalidSearch = new URL(page.url()).search;
  const english = page.getByRole("button", { name: "English" });
  await english.focus();
  await activateEnglishLocale(context, page, english);
  expect(new URL(page.url()).search).toBe(invalidSearch);
  const englishCutoff = page.getByLabel("Original lookup key (UTC)", { exact: true });
  await expect(manifestInput).toHaveValue(MANIFEST_ID);
  await expect(englishCutoff).toHaveValue(impossibleDate);
  await expect(englishCutoff).toHaveAccessibleDescription(/real-calendar UTC Z instant/);
  await englishCutoff.fill(CUTOFF);
  await englishCutoff.focus();
  await expectVisibleKeyboardFocus(englishCutoff);
  await submitLookup(page, englishCutoff);
  await expect(page.getByText("Synthetic DEMO · not observed SEC data")).toBeVisible();
  const repairedQuery = new URL(page.url()).searchParams;
  expect([...repairedQuery.entries()]).toEqual([
    ["manifestId", MANIFEST_ID], ["evaluationAsOf", CUTOFF], ["view", "summary"],
  ]);
  await expect(page.getByText("2026-08-25 12:30:00.123456 KST").first()).toBeVisible();

  await page.goto(`${ROUTE}?manifestId=${MANIFEST_ID}&manifestId=${MANIFEST_ID}`
    + `&evaluationAsOf=${encodeURIComponent(CUTOFF)}`);
  await expect(manifestInput).toHaveValue("");
  await expect(manifestInput).toHaveAccessibleDescription(/Duplicate values were not selected/);
  await expect(englishCutoff).toHaveValue(CUTOFF);
  await manifestInput.fill(MANIFEST_ID);
  await englishCutoff.fill("2026-08-26T03:30:00Z");
  await page.getByRole("link", { name: "Clear lookup inputs" }).click();
  await expect(page).toHaveURL(new RegExp(`${ROUTE}$`));
  await expect(manifestInput).toHaveValue("");
  await expect(englishCutoff).toHaveValue("");
  await expect(page.locator('p[role="alert"]')).toHaveCount(0);
  await expectNoPageOverflow(page);
  await context.clearCookies();

  await page.goto(
    `${ROUTE}?manifestId=${MANIFEST_ID}`
    + "&evaluationAsOf=2026-08-25T03%3A30%3A00.123455Z&view=summary",
  );
  await expect(page.getByRole("heading", { name: "이 정확한 manifest를 표시할 수 없습니다." }))
    .toBeVisible();
  await expect(page.getByText(MANIFEST_ID, { exact: true })).toHaveCount(0);
  const robots = await page.locator('meta[name="robots"]').evaluateAll((elements) =>
    elements.map((element) => element.getAttribute("content")));
  expect(robots.length).toBeGreaterThan(0);
  expect(robots.every((value) => /noindex/i.test(value ?? ""))).toBe(true);

  const missing = await openFailedInputs(page, "ko", MANIFEST_ID, "2026-08-25T03:30:00.123455Z");
  await missing.cutoff.fill(CUTOFF);
  await submitLookup(page, missing.cutoff);
  await expect(page.getByText("합성 DEMO · 실제 SEC 자료 아님")).toBeVisible();
  expect([...new URL(page.url()).searchParams.entries()]).toEqual([
    ["manifestId", MANIFEST_ID], ["evaluationAsOf", CUTOFF], ["view", "summary"],
  ]);
  await expect(page.getByText("2026-08-25 12:30:00.123456 KST").first()).toBeVisible();
  await activateEnglishLocale(context, page, page.getByRole("button", { name: "English" }));
  const missingId = "a".repeat(64);
  await page.goto(`${ROUTE}?manifestId=${missingId}&evaluationAsOf=${encodeURIComponent(CUTOFF)}`
    + "&view=occurrences&page=99&size=1");
  const missingEnglish = await openFailedInputs(page, "en", missingId, CUTOFF);
  await missingEnglish.manifest.fill(MANIFEST_ID);
  await submitLookup(page, missingEnglish.manifest);
  await expect(page.getByText("Synthetic DEMO · not observed SEC data")).toBeVisible();
  expect([...new URL(page.url()).searchParams.entries()]).toEqual([
    ["manifestId", MANIFEST_ID], ["evaluationAsOf", CUTOFF], ["view", "summary"],
  ]);
  await expect(page.locator("details")).not.toHaveAttribute("open");

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});
