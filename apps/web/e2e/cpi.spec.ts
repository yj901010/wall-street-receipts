import { expect, test } from "@playwright/test";
test("CPI navigation is keyboard-accessible and off by default without fabricated values", async ({ page }) => {
  await page.goto("/market");
  const link = page.getByRole("link", { name: "미국 CPI · 저장된 월간 관측자료 →" });
  await link.focus(); await expect(link).toBeFocused(); await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/market\/cpi$/);
  await expect(page.getByRole("heading", { name: "미국 소비자물가" })).toBeVisible();
  await expect(page.getByRole("status")).toContainText("CPI 수집 연결이 설정되지 않았습니다");
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole("link", { name: "시장으로 돌아가기 →" }).click();
  await expect(page).toHaveURL(/\/market$/);
});
