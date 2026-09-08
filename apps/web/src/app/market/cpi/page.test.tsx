import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithLocale } from "@/test/render-with-locale";
import { cpiFixture } from "@/test/cpi-fixture";
import { CpiView } from "./cpi-view";
import CpiError from "./error";
describe("CPI page", () => {
  it("shows KST retrieval, monthly/non-PIT caution, missing months and explicit synthetic status", () => {
    renderWithLocale(<CpiView locale="ko" state={{ kind: "ready", snapshot: cpiFixture() }} />);
    expect(screen.getByRole("heading", { name: "미국 소비자물가" })).toBeVisible();
    expect(screen.getByText("2026-09-08 10:00:00 KST")).toBeVisible();
    expect(screen.getByText(/DEMO · 합성/)).toBeVisible();
    expect(screen.getByText(/최초 발표 당시의 빈티지가 아니므로/)).toBeVisible();
    expect(screen.getByRole("rowheader", { name: "2025-10" }).closest("tr")).toHaveTextContent("자료 없음자료 없음자료 없음자료 없음");
    expect(screen.getByRole("region", { name: /월별 이력/ })).toHaveAttribute("tabindex", "0");
    expect(screen.getByText("BLS API v2")).toHaveAttribute("href", "https://api.bls.gov/publicAPI/v2/timeseries/data/");
    expect(screen.getByText("0".repeat(64))).toBeInTheDocument();
  });
  it.each(["disabled", "empty"] as const)("renders %s without any substitute observation", kind => {
    renderWithLocale(<CpiView locale="ko" state={{ kind }} />);
    expect(screen.getByRole("status")).toBeVisible(); expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("OBSERVED_MONTHLY")).not.toBeInTheDocument();
  });
  it("shows English copy and an age warning without claiming a newer release", () => {
    const snapshot = cpiFixture(); snapshot.servedAt = "2026-09-17T01:00:00Z";
    renderWithLocale(<CpiView locale="en" state={{ kind: "ready", snapshot }} />, "en");
    expect(screen.getByRole("heading", { name: "US consumer prices" })).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("not proof that a newer release exists");
  });
  it("recovers errors without printing internal error text", () => {
    const reset = vi.fn(); renderWithLocale(<CpiError error={new Error("private secret")} reset={reset} />);
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" })); expect(reset).toHaveBeenCalledOnce();
    expect(screen.queryByText("private secret")).not.toBeInTheDocument();
  });
});
