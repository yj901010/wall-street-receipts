import { expect, test } from "@playwright/test";
test("DEMO call links to a truthful disabled read-only scoring audit", async ({page}) => {
  await page.goto("/calls/demo-call-001");await page.getByRole("link",{name:"DEMO 평가 기록",exact:true}).click();
  await expect(page).toHaveURL(/\/calls\/demo-call-001\/scoring-receipts$/);
  await expect(page.getByText("부분 평가 · 완성 점수 아님")).toBeVisible();
  await expect(page.getByText("평가 기록 API 연결이 꺼져 있습니다.")).toBeVisible();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByText("이 DEMO 발언에 저장된 평가 기록이 없습니다.")).toHaveCount(0);
  const input=page.getByLabel("기록 UUID (선택)");await input.focus();await expect(input).toBeFocused();
  await input.fill("bad-id");await page.getByRole("button",{name:"조회",exact:true}).click();
  await expect(page.getByText("올바른 기록 UUID 하나만 입력하세요.")).toBeVisible();
  await expect(input).toHaveAttribute("aria-invalid","true");
  await page.getByRole("link",{name:"최근 기록",exact:true}).click();await expect(input).toHaveValue("");
  await expect(page.getByText("평가 기록 API 연결이 꺼져 있습니다.")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
test("duplicate or unknown query fields are not silently discarded",async({page})=>{
  for(const query of ["receiptId=a&receiptId=b","unexpected=DO_NOT_ECHO"]){
    await page.goto("/calls/demo-call-001/scoring-receipts?"+query);
    await expect(page.getByText("올바른 기록 UUID 하나만 입력하세요.")).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
    await expect(page.locator("body")).not.toContainText("DO_NOT_ECHO");
  }
});
