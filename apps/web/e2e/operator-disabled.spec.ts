import { expect, test } from "@playwright/test";
test("public Next never exposes the dedicated CPI operator screen or bridge", async ({ request }) => {
  for (const path of ["/operator/cpi", "/operator/cpi/query"]) {
    const response = await request.get(path, { headers: { "X-WSR-Operator": "cpi-read-v1" } });
    expect(response.status()).toBe(404);
    expect(await response.text()).not.toContain('name="token"');
  }
});
