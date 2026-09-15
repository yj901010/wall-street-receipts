import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ReferenceQuotePage, { dynamic, metadata } from "./page";

const mocks = vi.hoisted(() => ({ getLocale: vi.fn() }));
vi.mock("@/lib/i18n/server", () => ({ getLocale: mocks.getLocale }));
afterEach(() => vi.unstubAllEnvs());

describe("reference-only pilot route", () => {
  it("is dynamic and not indexed", () => {
    expect(dynamic).toBe("force-dynamic");
    expect(metadata.robots).toEqual({ index: false, follow: false });
  });
  it("defaults to no connection with no invented timestamps or DEMO prices", async () => {
    vi.stubEnv("VUNELIX_REFERENCE_WIDGET", undefined);
    mocks.getLocale.mockResolvedValue("ko");
    render(await ReferenceQuotePage());
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("AAPL");
    expect(screen.getByRole("status")).toHaveTextContent("꺼져 있습니다");
    expect(screen.getByText(/KST 변환이나 최신성 검증/)).toBeVisible();
    expect(document.querySelector("time, table, iframe, script")).toBeNull();
    expect(screen.getByRole("link", { name: /공급자 이용약관/ })).toHaveAttribute("rel", "noopener noreferrer");
  });
  it("enables consent, not automatic loading, in English", async () => {
    vi.stubEnv("VUNELIX_REFERENCE_WIDGET", "enabled");
    mocks.getLocale.mockResolvedValue("en");
    render(await ReferenceQuotePage());
    expect(screen.getByRole("button", { name: "Load external Vunelix widget" })).toBeVisible();
    expect(screen.getByText(/not proof of Nasdaq-only or consolidated/)).toBeVisible();
    expect(screen.getByText(/target hits, MFE\/MAE or rankings/)).toBeVisible();
    expect(document.querySelector("script")).toBeNull();
  });
});
