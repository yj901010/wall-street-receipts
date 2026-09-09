// @vitest-environment node
import { afterEach, expect, it, vi } from "vitest";
vi.mock("./view", () => ({ OperatorCpiView: () => null }));
vi.mock("next/navigation", () => ({ notFound: () => { throw new Error("NOT_FOUND"); } }));
import Page from "./page";
const state = globalThis as Record<symbol, unknown>;
const key = Symbol.for("wsr.cpi.operator.loopback.v1");
afterEach(() => { delete state[key]; vi.unstubAllEnvs(); });
it("is absent from public Next even when an environment switch or bearer is present", () => {
  vi.stubEnv("OPERATOR_API_ENABLED", "true");
  vi.stubEnv("CPI_OPERATOR_UI_PORT", "3400");
  expect(() => Page()).toThrow("NOT_FOUND");
  state[key] = true; expect(Page()).toBeTruthy();
});
