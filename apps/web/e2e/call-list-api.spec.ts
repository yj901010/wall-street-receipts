import { expect, test, type Page } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

function collectExternalRequests(page: Page) {
  const browserApiRequests: string[] = [];
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.hostname === "localhost" && url.port === "8080") {
      browserApiRequests.push(request.url());
    }
  });
  return browserApiRequests;
}

test("keeps filtered call-list API evidence response-bounded, bilingual, keyboard accessible, and server-only", async ({
  context,
  page,
}) => {
  const runtimeErrors = collectRuntimeErrors(page);
  const browserApiRequests = collectExternalRequests(page);
  const apiMode = process.env.CALL_AUDIT_PROVIDER === "api";
  await context.clearCookies();

  await page.goto("/calls");
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  await expect(page.getByRole("heading", { name: "애널리스트 콜" })).toBeVisible();
  await expect(page.locator('a[href="/calls/demo-call-002"]')).toBeVisible();
  await expect(page.getByText("이벤트 3건", { exact: true })).toBeVisible();

  const filteredUrl = "/calls?assetId=asset-nvda&ticker=nvda&institutionId=inst-gs" +
    "&analystId=analyst-demo-b&direction=BULLISH&status=ACTIVE&dataMode=DEMO" +
    "&from=2026-08-11&to=2026-08-11&page=0&size=1&sort=capturedAt&order=asc";
  await page.goto(filteredUrl);

  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  await expect(page.getByLabel("자산 ID (대소문자 정확히 일치)")).toHaveValue("asset-nvda");
  await expect(page.getByLabel("티커 (대소문자 구분 없음)")).toHaveValue("nvda");
  await expect(page.getByLabel("기관 ID (대소문자 정확히 일치)")).toHaveValue("inst-gs");
  await expect(page.getByLabel("애널리스트 ID (대소문자 정확히 일치)")).toHaveValue("analyst-demo-b");
  await expect(page.getByLabel("방향")).toHaveValue("BULLISH");
  await expect(page.getByLabel("상태")).toHaveValue("ACTIVE");
  await expect(page.getByLabel("시작일")).toHaveValue("2026-08-11");
  await expect(page.getByLabel("종료일(UTC)")).toHaveValue("2026-08-11");
  await expect(page.getByLabel("행 수")).toHaveValue("1");
  await expect(page.getByLabel("정렬 기준")).toHaveValue("capturedAt");
  await expect(page.getByLabel("정렬 순서")).toHaveValue("asc");
  await expect(page.locator('a[href="/calls/demo-call-002"]')).toBeVisible();
  await expect(page.getByRole("table", { name: "필터링된 애널리스트 콜 이벤트" }))
    .toContainText("Goldman Sachs");
  await expect(page.getByText(/현재 응답 페이지에 반환된 콜만 요약/)).toBeVisible();

  const datasetKo = page.getByRole("region", { name: "콜 데이터셋 출처 정보" });
  if (apiMode) {
    await expect(datasetKo.getByText("NOT_EXPOSED")).toBeVisible();
    await expect(datasetKo.getByText(/LIST_API_HAS_NO_DATASET_METADATA/)).toBeVisible();
    await expect(datasetKo.getByText(/현재 페이지에서 이를 추론하지 않습니다/)).toBeVisible();
    await expect(datasetKo.getByText("NA")).toHaveCount(2);
  } else {
    await expect(datasetKo.getByText("제공됨")).toBeVisible();
  }

  const ticker = page.getByLabel("티커 (대소문자 구분 없음)");
  const assetId = page.getByLabel("자산 ID (대소문자 정확히 일치)");
  await ticker.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(assetId);

  const returnedProvenance = page
    .getByText("반환된 콜 출처 계보", { exact: true })
    .locator("..")
    .locator("dd");
  await returnedProvenance.evaluate((element) => {
    element.textContent = Array.from(
      { length: 100 },
      (_, index) => `provenance-${String(index).padStart(3, "0")}-${"x".repeat(111)}`,
    ).join(", ");
  });
  await expectNoPageOverflow(page);

  const koreanButton = page.getByRole("button", { name: "한국어" });
  const englishButton = page.getByRole("button", { name: "English" });
  await koreanButton.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(englishButton);
  await englishButton.press("Enter");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  expect(new URL(page.url()).search).toBe(new URL(`http://example.test${filteredUrl}`).search);
  await expect(page.getByLabel("Ticker (case-insensitive)")).toHaveValue("nvda");
  await expect(page.getByLabel("Asset ID (exact case)")).toHaveValue("asset-nvda");
  await expect(page.getByText(/returned response page/)).toBeVisible();

  const datasetEn = page.getByRole("region", { name: "Call dataset provenance" });
  if (apiMode) {
    await expect(datasetEn.getByText("NOT_EXPOSED")).toBeVisible();
    await expect(datasetEn.getByText(/does not infer them from returned rows/)).toBeVisible();
    await expect(datasetEn.getByText(/LIST_API_HAS_NO_DATASET_METADATA/)).toBeVisible();
  } else {
    await expect(datasetEn.getByText("AVAILABLE")).toBeVisible();
  }

  await page.goto("/calls?size=1&sort=eventTime&order=desc&page=0");
  await expect(page.locator('a[href="/calls/demo-call-002"]')).toBeVisible();
  const sourceLink = page.getByRole("link", { name: "DEMO equity interview" });
  const next = page.getByRole("link", { name: "Next" });
  await sourceLink.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(next);
  await next.press("Enter");
  await expect(page).toHaveURL(/\/calls\?size=1&sort=eventTime&order=desc&page=1$/);
  await expect(page.locator('a[href="/calls/demo-call-001"]')).toBeVisible();

  await page.goto("/calls?ticker=TSLA&size=1&sort=eventTime&order=desc&page=0");
  await expect(page.getByRole("status")).toContainText(
    "This response contains no items matching these filters.",
  );
  await expect(page.getByRole("status")).toContainText(
    "this empty response is not a dataset-completeness claim",
  );
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByText(/0 result pages · requested page 1/)).toBeVisible();
  const returnedEmpty = page.locator('dl[aria-label="Returned-page call evidence"]');
  await expect(returnedEmpty.getByText("Latest returned call capture").locator("..").locator("dd"))
    .toHaveText("NA");
  await expect(returnedEmpty.getByText("Returned call provenance").locator("..").locator("dd"))
    .toHaveText("NA");
  await expect(returnedEmpty.getByText("Mode").locator("..").locator("dd"))
    .toHaveText("DEMO");

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});
