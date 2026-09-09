// @vitest-environment node
import { afterEach, expect, it } from "vitest";
import { proxy, config } from "../proxy";
const state = globalThis as Record<symbol, unknown>;
const key = Symbol.for("wsr.cpi.operator.loopback.v1");
afterEach(() => { delete state[key]; });
it("denies operator paths with no-store before rendering and leaves public paths outside matcher", () => {
  expect(config.matcher).toBe("/operator/:path*");
  expect(proxy().status).toBe(404);
  expect(proxy().headers.get("cache-control")).toBe("no-store");
  state[key] = true;
  expect(proxy().headers.get("x-middleware-next")).toBe("1");
});
