import { expect, test } from "@playwright/test";
import { expectNoPageOverflow, collectRuntimeErrors, expectNoRuntimeErrors } from "./runtime-assertions";

test("reference pilot is disabled by default with no external request or invented quote", async ({ page }) => {
  const external: string[] = [];
  const errors = collectRuntimeErrors(page);
  await page.route("**/*", async route => {
    if (new URL(route.request().url()).origin !== new URL(test.info().project.use.baseURL!).origin) {
      external.push(route.request().url());
      await route.abort();
    } else await route.continue();
  });
  await page.goto("/market/reference/AAPL");
  await expect(page.getByRole("status")).toContainText("외부 위젯 연결이 꺼져 있습니다");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex, nofollow");
  // Next's development toolbar lives outside the product's main landmark.
  await expect(page.getByRole("main").getByRole("button")).toHaveCount(0);
  await expect(page.locator("vunelix-symbol-overview, iframe, table, time")).toHaveCount(0);
  await expect(page.locator('script[data-wsr-reference]')).toHaveCount(0);
  await expectNoPageOverflow(page);
  expect(external).toEqual([]);
  expectNoRuntimeErrors(errors);
});
