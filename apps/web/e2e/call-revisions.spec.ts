import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
  expectVisibleKeyboardFocus,
} from "./runtime-assertions";

function collectExternalRequests(page: import("@playwright/test").Page) {
  const browserApiRequests: string[] = [];
  page.on("request", (request) => {
    const url = new URL(request.url());
    if (url.hostname === "localhost" && url.port === "8080") browserApiRequests.push(request.url());
  });
  return browserApiRequests;
}

test("renders populated and known-empty revision responses bilingually through the server aggregate", async ({
  context,
  page,
}) => {
  const runtimeErrors = collectRuntimeErrors(page);
  const browserApiRequests = collectExternalRequests(page);
  await context.clearCookies();

  await page.goto("/calls/demo-call-002");
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
  const koreanTimeline = page.getByRole("heading", { name: "콜 변경 이력" }).locator("..").locator("..");
  await expect(koreanTimeline.getByText("변경 이벤트 2건")).toBeVisible();
  await expect(page.getByRole("article", { name: "변경 1 · CORRECTION" })).toContainText(
    "demo-call-revision-001",
  );
  await expect(page.getByRole("article", { name: "변경 2 · CANCELLATION" })).toContainText(
    "demo-call-revision-002",
  );
  await expect(page.getByText(/상단 상태는 변경 불가 원본 이벤트 필드/)).toBeVisible();
  await expect(page.getByText("ACTIVE", { exact: true })).toBeVisible();
  await expect(page.getByText("BULLISH", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("$235.00", { exact: true })).toBeVisible();
  const correction = page.getByRole("article", { name: "변경 1 · CORRECTION" });
  await expect(correction.getByText("정정 목표가", { exact: true }).locator("..").locator("dd")).toHaveText("232");
  await expect(correction.getByText("정정 통화", { exact: true }).locator("..").locator("dd")).toHaveText("USD");
  await expectNoPageOverflow(page);

  const englishButton = page.getByRole("button", { name: "English" });
  await englishButton.focus();
  await expectVisibleKeyboardFocus(englishButton);
  await englishButton.press("Enter");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("heading", { name: "Call revision history" })).toBeVisible();
  await expect(page.getByText(/not a current or effective stance/)).toBeVisible();
  await expect(page.getByRole("article", { name: "Revision 1 · CORRECTION" })).toContainText(
    "Synthetic target correction for deterministic lifecycle testing.",
  );

  await page.goto("/calls/demo-call-001");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("heading", { name: "Call revision history" })).toBeVisible();
  await expect(page.getByText("0 revision events")).toBeVisible();
  await expect(page.getByRole("status")).toContainText(
    "This response contains no recorded correction or cancellation events.",
  );
  await expect(page.getByRole("article")).toHaveCount(0);

  await expectNoPageOverflow(page);
  expect(browserApiRequests).toEqual([]);
  expectNoRuntimeErrors(runtimeErrors);
});
