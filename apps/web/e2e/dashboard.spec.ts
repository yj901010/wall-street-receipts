import { expect, test } from "@playwright/test";
import {
  collectRuntimeErrors,
  expectNoPageOverflow,
  expectNoRuntimeErrors,
} from "./runtime-assertions";

test.describe("dashboard responsive regression", () => {
  test("renders the DEMO dashboard without page overflow or runtime errors", async ({ page }) => {
    const runtimeErrors = collectRuntimeErrors(page);
    const response = await page.goto("/");

    expect(response?.ok()).toBe(true);
    await expect(
      page.getByRole("heading", { name: "Market evidence, frozen at the call." }),
    ).toBeVisible();
    await expect(page.getByText("DEMO", { exact: true }).first()).toBeVisible();

    await expectNoPageOverflow(page);
    expectNoRuntimeErrors(runtimeErrors);
  });
});
