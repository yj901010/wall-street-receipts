import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithLocale } from "@/test/render-with-locale";
import NotFound from "./not-found";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

describe("root not found", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders the Korean-default mode-neutral boundary", async () => {
    renderWithLocale(await NotFound());

    const main = screen.getByRole("main");
    expect(within(main).getByRole("heading", { name: "페이지를 찾을 수 없습니다." }))
      .toBeInTheDocument();
    expect(within(main).getByText("찾을 수 없는 경로", { exact: true })).toBeInTheDocument();
    expect(within(main).queryByText("DEMO", { exact: true })).not.toBeInTheDocument();
    expect(within(main).getByRole("link", { name: "대시보드로 돌아가기" }))
      .toHaveAttribute("href", "/");
    expect(within(main).getByRole("link", { name: "콜 기록 열기" }))
      .toHaveAttribute("href", "/calls");
  });

  it("renders the selected English catalog without changing routes", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await NotFound(), "en");

    expect(screen.getByRole("heading", { name: "Page not found." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Return to dashboard" }))
      .toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "Open call records" }))
      .toHaveAttribute("href", "/calls");
  });
});
