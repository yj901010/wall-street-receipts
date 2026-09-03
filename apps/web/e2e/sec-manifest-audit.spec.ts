import { expect, test, type Page } from "@playwright/test";
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
    await page.goto(
      `${ROUTE}?manifestId=${MANIFEST_ID}`
      + `&evaluationAsOf=${encodeURIComponent(CUTOFF)}&view=summary`,
    );
    await expect(page.getByRole("heading", {
      name: "정확한 manifest 응답을 검증할 수 없습니다.",
    })).toBeVisible();
    await expect(page.getByText("합성 DEMO · 실제 SEC 자료 아님")).toHaveCount(0);
    await expect(page.getByText(MANIFEST_ID, { exact: true })).toHaveCount(0);
    await expectNoPageOverflow(page);
    expect(browserApiRequests).toEqual([]);
    expect(runtimeErrors).toHaveLength(1);
    expect(runtimeErrors[0]).toContain("SEC manifest audit API summary request failed");
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

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
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

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});
