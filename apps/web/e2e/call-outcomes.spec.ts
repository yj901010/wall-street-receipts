import { expect, test, type Page } from "@playwright/test";
import {
  activateEnglishLocale,
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

function collectExternalRequests(page: Page) {
  const browserApiRequests: string[] = [];
  const configuredApiOrigin = process.env.API_BASE_URL
    ? new URL(process.env.API_BASE_URL).origin
    : "http://localhost:8080";
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.origin === configuredApiOrigin) browserApiRequests.push(request.url());
  });
  return browserApiRequests;
}

test("renders populated and known-empty outcome audit responses bilingually through one server aggregate", async ({
  context,
  page,
}) => {
  const runtimeErrors = collectRuntimeErrors(page);
  const browserApiRequests = collectExternalRequests(page);
  await context.clearCookies();

  await page.goto("/calls/demo-call-001");
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  const koreanOutcome = page.locator('section[aria-labelledby="outcome-title"]');
  await expect(koreanOutcome.getByRole("heading", { name: "성과 감사 이력" })).toBeVisible();
  await expect(koreanOutcome.getByText("감사 전용 · DEMO")).toBeVisible();
  await expect(koreanOutcome.getByText("성과 기록 4건")).toBeVisible();
  await expect(koreanOutcome.getByText(/JSON null로만 허용/)).toBeVisible();

  const koreanRecords = koreanOutcome.getByRole("article");
  await expect(koreanRecords).toHaveCount(4);
  await expect(koreanRecords.nth(0)).toContainText("outcome-demo-call-001-d1-v1-001");
  await expect(koreanRecords.nth(1)).toContainText("outcome-demo-call-001-d1-v1-002");
  await expect(koreanRecords.nth(2)).toContainText("outcome-demo-call-001-d1-v2-001");
  await expect(koreanRecords.nth(3)).toContainText("outcome-demo-call-001-m1-v1-001");
  const firstRecord = koreanRecords.nth(0);
  await expect(firstRecord.getByText("방법론 정의 해시")).toBeVisible();
  await expect(firstRecord.getByText("입력 지문")).toBeVisible();
  await expect(firstRecord.getByText(
    "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
  )).toBeVisible();
  await expect(firstRecord.getByText(
    "b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6",
  )).toBeVisible();
  await expect(firstRecord.getByText("HORIZON_DATA_MISSING")).toBeVisible();
  await expect(koreanRecords.nth(3).getByText("HORIZON_NOT_REACHED")).toBeVisible();
  await expect(firstRecord.getByText("false", { exact: true })).toBeVisible();
  await expect(firstRecord.getByText("NA", { exact: true })).toHaveCount(13);

  const outcomeSectionMetrics = await koreanOutcome.evaluate((element) => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth,
  }));
  const firstRecordMetrics = await firstRecord.evaluate((element) => ({
    clientWidth: element.clientWidth,
    scrollWidth: element.scrollWidth,
  }));
  expect(outcomeSectionMetrics.scrollWidth).toBeLessThanOrEqual(outcomeSectionMetrics.clientWidth + 1);
  expect(firstRecordMetrics.scrollWidth).toBeLessThanOrEqual(firstRecordMetrics.clientWidth + 1);

  const methodologyHashEvidence = firstRecord.locator("dd").filter({
    hasText: "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
  });
  const inputFingerprintEvidence = firstRecord.locator("dd").filter({
    hasText: "b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6",
  });
  for (const evidence of [methodologyHashEvidence, inputFingerprintEvidence]) {
    const evidenceMetrics = await evidence.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
    }));
    expect(evidenceMetrics.scrollWidth).toBeLessThanOrEqual(evidenceMetrics.clientWidth + 1);
  }

  const firstRecordBox = await firstRecord.boundingBox();
  const methodologyHashBox = await methodologyHashEvidence.boundingBox();
  const inputFingerprintBox = await inputFingerprintEvidence.boundingBox();
  expect(firstRecordBox).not.toBeNull();
  expect(methodologyHashBox).not.toBeNull();
  expect(inputFingerprintBox).not.toBeNull();
  for (const evidenceBox of [methodologyHashBox!, inputFingerprintBox!]) {
    expect(evidenceBox.x).toBeGreaterThanOrEqual(firstRecordBox!.x - 1);
    expect(evidenceBox.x + evidenceBox.width).toBeLessThanOrEqual(
      firstRecordBox!.x + firstRecordBox!.width + 1,
    );
  }

  const macroRegion = page.getByRole("region", { name: "거시 관측 증거 표" });
  await macroRegion.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(koreanOutcome);
  await expectNoPageOverflow(page);

  const koreanButton = page.getByRole("button", { name: "한국어" });
  const englishButton = page.getByRole("button", { name: "English" });
  await koreanButton.focus();
  await page.keyboard.press("Tab");
  await expectVisibleKeyboardFocus(englishButton);
  await activateEnglishLocale(context, page, englishButton);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");

  const englishOutcome = page.locator('section[aria-labelledby="outcome-title"]');
  await expect(englishOutcome.getByRole("heading", { name: "Outcome audit history" })).toBeVisible();
  await expect(englishOutcome.getByText("AUDIT ONLY · DEMO")).toBeVisible();
  await expect(englishOutcome.getByText("4 outcome records")).toBeVisible();
  const englishFirstRecord = englishOutcome.getByRole("article", {
    name: "Outcome record 1 · D1 · methodology 1.0.0 · INCOMPLETE",
  });
  await expect(englishFirstRecord.getByText("Methodology definition hash")).toBeVisible();
  await expect(englishFirstRecord.getByText("Input fingerprint")).toBeVisible();
  await expect(englishOutcome.getByText(/never folded or substituted as a latest, current, or effective outcome/))
    .toBeVisible();
  await expect(englishOutcome.getByText("Methodology not active", { exact: true })).toHaveCount(0);
  await expect(englishOutcome.getByText("Latest outcome", { exact: true })).toHaveCount(0);
  await expectNoPageOverflow(page);

  await page.goto("/calls/demo-call-002");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  const emptyOutcome = page.locator('section[aria-labelledby="outcome-title"]');
  await expect(emptyOutcome.getByText("0 outcome records")).toBeVisible();
  await expect(emptyOutcome.getByRole("status")).toContainText(
    "No outcome event is recorded in this audit response. No other history or substitute result was shown.",
  );
  await expect(emptyOutcome.getByRole("article")).toHaveCount(0);
  await expect(emptyOutcome.getByText(/not inferred as an EXCLUDED outcome or any other result/)).toBeVisible();
  await expect(page.getByRole("article", { name: "Revision 2 · CANCELLATION" })).toBeVisible();
  await expect(page.getByText("ACTIVE", { exact: true })).toBeVisible();
  await expect(page.getByText("BULLISH", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("$235.00", { exact: true })).toBeVisible();
  await expectNoPageOverflow(page);

  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});
