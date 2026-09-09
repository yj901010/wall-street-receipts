import { test, expect } from "@playwright/test";
const id = (n: number) => `00000000-0000-0000-0000-${n.toString(16).padStart(12, "0")}`;

test("DEMO PostgreSQL evidence through real Spring and production operator UI", async ({ page, context, request, baseURL }, info) => {
  const token = process.env.WSR_CPI_BROWSER_TOKEN!;
  const phase = process.env.WSR_CPI_BROWSER_PHASE!;
  const queries: string[] = [];
  const pageErrors: string[] = [];
  const external: string[] = [];
  page.on("pageerror", error => pageErrors.push(error.message));
  page.on("request", req => {
    if (req.url().includes("/operator/cpi/query")) queries.push(req.url());
    if (!req.url().startsWith(baseURL + "/")) external.push(req.url());
  });
  const document = await page.goto("/operator/cpi");
  expect(document!.status()).toBe(200);
  expect(document!.headers()["cache-control"]).toContain("no-store");
  await expect(page.getByRole("heading", { name: "CPI 수집 이력" })).toBeVisible();
  await expect(page.getByText("아직 조회하지 않았습니다.")).toBeVisible();
  expect(queries).toHaveLength(0);
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("운영자 토큰")).toBeFocused();
  expect(await page.getByLabel("운영자 토큰").evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe("none");

  async function query(selection = "", credential = token) {
    // Test pacing respects the real listener's process-wide one-second limiter; production has no retries.
    await page.waitForTimeout(1100);
    await page.getByLabel("수집 시도 UUID (선택)").fill(selection);
    await page.getByLabel("운영자 토큰").fill(credential);
    const pending = page.waitForResponse(response => response.url().includes("/operator/cpi/query"));
    await page.getByRole("button", { name: "이력 조회" }).click();
    const response = await pending;
    expect(response.headers()["cache-control"]).toBe("no-store");
    await expect(page.getByLabel("운영자 토큰")).toHaveValue("");
    return response;
  }
  const initial = await query();
  if (phase === "unavailable") {
    expect(initial.status()).toBe(503);
    await expect(page.getByRole("main").getByRole("alert")).toContainText("대체 데이터는 사용하지 않았습니다");
    await expect(page.locator("tbody tr")).toHaveCount(0);
    await expect(page.getByRole("main")).not.toContainText("permission denied");
    // The real UI intentionally cancels non-200 bodies. Check the wire envelope
    // using another real request, not by trying to recover a cancelled browser body.
    await page.waitForTimeout(1100);
    const unavailable = await request.get("/operator/cpi/query", {
      headers: { Authorization: `Bearer ${token}`, "X-WSR-Operator": "cpi-read-v1" },
    });
    expect(unavailable.status()).toBe(503);
    expect(unavailable.headers()["cache-control"]).toBe("no-store");
    expect(await unavailable.json()).toEqual({ error: "CPI_OPERATOR_QUERY_UNAVAILABLE" });
  } else {
    expect(initial.status()).toBe(200);
    const evidence = await initial.json();
    expect(evidence.metadata.dataMode).toBe("UNVERIFIED");
    expect(evidence.metadata.evidenceMode).toBe("PERSISTED_ATTEMPT_RECORDS");
    expect(evidence.metadata.observedAtKst).toBe("2026-09-09T00:02:00.123456789+09:00");
    await expect(page.getByText("2026-09-09 00:02:00.123456789 KST")).toBeVisible();
    if (phase === "empty") {
      expect(evidence.attempts).toEqual([]); expect(evidence.hasMore).toBe(false);
      await expect(page.getByText(/저장된 수집 시도 기록이 없습니다/)).toBeVisible();
    } else {
      expect(evidence.limit).toBe(20); expect(evidence.hasMore).toBe(true);
      expect(evidence.attempts).toHaveLength(20);
      // PostgreSQL ordering is started DESC, canonical UUID DESC for each tied pair.
      const expected = Array.from({ length: 20 }, (_, i) => id(i % 2 === 0 ? i + 2 : i));
      expect(evidence.attempts.map((row: { attemptId: string }) => row.attemptId)).toEqual(expected);
      await expect(page.locator("tbody tr")).toHaveCount(20);
      const first = page.locator("tbody tr").first();
      await expect(first).toContainText(id(2)); await expect(first).toContainText("상태 미확인");
      await expect(first).toContainText("완료 기록 없음");
      await expect(page.locator("tbody")).toContainText("저장 기록");
      await expect(page.locator("tbody")).toContainText("실패 단계: 수신");
      await expect(page.locator("tbody")).toContainText("실패 단계: 파싱");
      await expect(page.locator("tbody")).toContainText("건너뜀");
      await expect(page.locator("tbody")).toContainText("다음 실행 보장 아님");
      await expect(page.getByText("2026-09-10 00:00:00 KST")).toBeVisible();
      await expect(page.getByText("2026-09-08 23:59:59.123456 KST").first()).toBeVisible();
      if (phase === "seeded") {
        const exact = await query(id(24));
        expect(exact.status()).toBe(200);
        expect((await exact.json()).attempt.attemptId).toBe(id(24));
        await expect(page.locator("tbody tr")).toHaveCount(1);
        await expect(page.locator("tbody tr")).toContainText(id(24));
        const saved = await query(id(1));
        expect(saved.status()).toBe(200);
        expect((await saved.json()).attempt.terminal.captureId).toBe(id(1000));
        await expect(page.locator("tbody tr")).toContainText(id(1000));
        const missing = await query(id(99));
        expect(missing.status()).toBe(404);
        await expect(page.getByRole("main").getByRole("alert")).toContainText("기록을 찾을 수 없거나");
        await expect(page.locator("tbody tr")).toHaveCount(0);
        await query(); await expect(page.locator("tbody tr")).toHaveCount(20);
        // Different valid-shaped ephemeral token: exercise real Spring authentication after successful data.
        const wrong = await query("", Buffer.alloc(32, 255).toString("base64"));
        expect(wrong.status()).toBe(401);
        await expect(page.getByRole("main").getByRole("alert")).toContainText("운영자 토큰을 확인");
        await expect(page.locator("tbody tr")).toHaveCount(0);
        const mutation = await request.post("/operator/cpi/query", { headers: { Authorization: `Bearer ${token}`, "X-WSR-Operator": "cpi-read-v1" } });
        expect(mutation.status()).toBe(405);
        expect((await request.get("/internal/v1/sec/collection-attempts")).status()).toBe(404);
      }
    }
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  expect(await context.cookies()).toEqual([]);
  expect(queries.every(url => !url.includes(token) && !url.includes("token="))).toBe(true);
  expect(external).toEqual([]); expect(pageErrors).toEqual([]);
  // DEMO screenshot only, after checking the password is empty. No trace/video/response attachments.
  await expect(page.getByLabel("운영자 토큰")).toHaveValue("");
  await page.screenshot({ path: info.outputPath("demo-cleared.png"), fullPage: true });
  await page.getByRole("button", { name: "입력·결과 지우기" }).click();
  await expect(page.getByLabel("수집 시도 UUID (선택)")).toHaveValue("");
  await expect(page.locator("tbody tr")).toHaveCount(0);
});
