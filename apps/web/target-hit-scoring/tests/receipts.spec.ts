import { expect, test } from "@playwright/test";
const path = "/calls/demo-call/target-hit-scoring-receipts";
const phase = process.env.WSR_TARGET_HIT_SCORING_PHASE;
test("DEMO command receipts through real Spring and production audit screen", async ({page,browser,baseURL},testInfo) => {
  const issues:string[]=[];page.on("pageerror",e=>issues.push(e.message));
  await page.goto(path);
  await expect(page.getByRole("heading",{name:"DEMO 목표가 도달 평가 기록",exact:true})).toBeVisible();
  await expect(page.getByText("부분 평가 · 완성 점수 아님")).toBeVisible();
  const input=page.getByLabel("기록 UUID (선택)"); await input.focus(); await expect(input).toBeFocused();
  if(phase==="empty") {
    await expect(page.getByText("이 DEMO 발언에 저장된 목표가 도달 평가 기록이 없습니다.")).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
  } else if(phase==="unavailable") {
    await expect(page.getByText("목표가 도달 평가 기록을 검증하여 불러오지 못했습니다.")).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0); await expect(page.getByText("0.000000000000")).toHaveCount(0);
  } else {
    await expect(page.getByRole("table").getByRole("row")).toHaveCount(7);
    await expect(page.getByText("종가 대기",{exact:true}).first()).toBeVisible();
    await expect(page.getByText("산출 불가",{exact:true}).first()).toBeVisible();
    const region=page.getByRole("region",{name:"목표가 도달 평가 기록 표 (가로 스크롤)"});await region.focus();await expect(region).toBeFocused();
    const reasons=region.locator("td small").filter({hasText:" → "});
    expect(await reasons.count()).toBeGreaterThan(0);
    expect(await reasons.evaluateAll(items=>items.every(item=>getComputedStyle(item).whiteSpace==="normal" && item.scrollWidth<=item.clientWidth+1))).toBe(true);
    await region.press("ArrowRight");
    await expect.poll(()=>region.evaluate(element=>element.scrollLeft)).toBeGreaterThan(0);
    await region.evaluate(element=>{element.scrollLeft=element.scrollWidth;});
    await expect(page.getByRole("columnheader",{name:"목표가 도달",exact:true})).toBeInViewport();
    await expect(page.getByText("미도달",{exact:true})).toBeVisible();
    await expect(page.locator("body")).not.toContainText("DO_NOT_PUBLISH_FUTURE");
    await page.screenshot({path:testInfo.outputPath("demo-receipt-metrics.png"),fullPage:true});
    await region.evaluate(element=>{element.scrollLeft=0;});
    await page.screenshot({path:testInfo.outputPath("demo-receipt-list.png"),fullPage:true});
    const id=process.env.WSR_TARGET_HIT_SCORING_RECEIPT_ID!;expect(id).toMatch(/^[0-9a-f-]{36}$/);
    await input.fill(id);await page.getByRole("button",{name:"조회",exact:true}).click();
    await expect(page.getByRole("heading",{name:"선택한 기록",exact:true})).toBeVisible();
    await expect(page.getByText("0.200000000000",{exact:true})).toBeVisible();
    await expect(page.getByText("0.250000000000",{exact:true})).toBeVisible();
    await expect(page.getByText("0.100000000000",{exact:true})).toBeVisible();
    await expect(page.getByText("-0.050000000000",{exact:true})).toBeVisible();
    await expect(page.getByRole("heading",{name:"선택된 벤치마크 근거",exact:true})).toBeVisible();
    await expect(page.getByText("BENCHMARK_ASSIGNMENT / demo-b-assignment")).toBeVisible();
    await expect(page.getByText("SECTOR_MAPPING / demo-s-map")).toBeVisible();
    await expect(page.getByRole("heading",{name:"선택된 목표가 도달 근거",exact:true})).toBeVisible();
    await expect(page.getByText("HIGH / 160",{exact:true})).toBeVisible();
    await expect(page.getByText("160 / 80",{exact:true})).toBeVisible();
    await expect(page.getByText("CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION",{exact:true})).toBeVisible();
    await expect(page.getByText(process.env.WSR_TARGET_HIT_SCORING_INPUT_HASH!,{exact:true})).toBeVisible();
    await expect(page.getByText("receipt-snapshot",{exact:true})).toBeVisible();
    await page.screenshot({path:testInfo.outputPath("demo-receipt-selected.png"),fullPage:true});
    await page.goto(path+"?receiptId=1-1-1-1-1"); await expect(input).toHaveAttribute("aria-invalid","true");
    await input.fill(id);await page.getByRole("button",{name:"조회",exact:true}).click();
    await expect(page.getByRole("heading",{name:"선택한 기록",exact:true})).toBeVisible();
    await page.goto(path+"?receiptId=00000000-0000-0000-0000-000000000000");
    await expect(page.getByText("이 발언에 연결된 DEMO 기록을 찾을 수 없습니다.")).toBeVisible();
    await page.getByRole("link",{name:"최근 기록",exact:true}).click();await expect(page.getByRole("table").getByRole("row")).toHaveCount(7);
    await page.goto("/calls/demo-call");await page.getByRole("link",{name:"DEMO 목표가 도달 평가 기록",exact:true}).click();
    await expect(page).toHaveURL(new RegExp(path+"$"));
    const plain=await browser.newContext({baseURL,viewport:testInfo.project.use.viewport});
    await plain.addCookies([{name:"wsr_locale",value:"en",url:baseURL!}]);
    try {
      const english=await plain.newPage();await english.goto(path);
      await expect(english.getByRole("heading",{name:"DEMO target-hit scoring receipts",exact:true})).toBeVisible();
      await english.getByLabel("Receipt UUID (optional)").fill(id);await english.getByRole("button",{name:"Query",exact:true}).click();
      await expect(english.getByRole("heading",{name:"Selected receipt",exact:true})).toBeVisible();
      await expect(english.getByText("0.200000000000",{exact:true})).toBeVisible();
    } finally { await plain.close(); }
    const noScript=await browser.newContext({javaScriptEnabled:false,baseURL,viewport:testInfo.project.use.viewport});
    try {
      const noJs=await noScript.newPage();await noJs.goto(path);
      // Playwright text locators deliberately skip noscript elements; inspect the visible HTML fallback directly.
      await expect(noJs.locator("noscript p")).toBeVisible();
      await expect(noJs.locator("noscript p")).toContainText("Enable JavaScript to display this streamed audit page.");
      await expect(noJs.getByRole("table")).toHaveCount(0);
    } finally { await noScript.close(); }
  }
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  expect(issues).toEqual([]);
});
