import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Locale } from "@/lib/i18n/config";
import { renderWithLocale } from "@/test/render-with-locale";
import CallsError from "./error";
import CallsLoading from "./loading";
import CallsPage from "./page";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

async function renderPage(
  searchParams: Record<string, string> = {},
  locale: Locale = "ko",
) {
  i18nServer.getLocale.mockResolvedValue(locale);
  return renderWithLocale(
    await CallsPage({ searchParams: Promise.resolve(searchParams) }),
    locale,
  );
}

describe("CallsPage", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
    i18nServer.getLocale.mockResolvedValue("ko");
  });

  it("renders the Korean-default canonical DEMO ledger and accessible filters", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { name: "애널리스트 콜" })).toBeInTheDocument();
    expect(screen.getByRole("form", { name: "애널리스트 콜 필터" })).toBeInTheDocument();
    expect(screen.getByLabelText("티커")).toBeInTheDocument();
    expect(screen.getAllByText("DEMO").length).toBeGreaterThan(0);

    const table = screen.getByRole("table", { name: "필터링된 애널리스트 콜 이벤트" });
    const sourceLink = within(table).getByRole("link", { name: "DEMO equity interview" });
    const callRow = sourceLink.closest("tr");

    expect(callRow).not.toBeNull();
    expect(within(callRow!).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(callRow!).getByText("NVDA")).toBeInTheDocument();
    expect(within(callRow!).getByText("Aug 11, 2026, 2:20 PM UTC")).toBeInTheDocument();
    expect(within(callRow!).getByText("$210.00 → $235.00")).toBeInTheDocument();
    expect(sourceLink).toHaveAttribute("href", "/calls/demo-call-002#source");
  });

  it("renders English UI from an English server cookie without changing finance evidence", async () => {
    await renderPage({}, "en");

    expect(screen.getByRole("heading", { name: "Analyst calls" })).toBeInTheDocument();
    expect(screen.getByRole("form", { name: "Filter analyst calls" })).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Filtered analyst call events" });
    const sourceLink = within(table).getByRole("link", { name: "DEMO equity interview" });
    const callRow = sourceLink.closest("tr");

    expect(callRow).not.toBeNull();
    expect(within(callRow!).getByText("Goldman Sachs")).toBeInTheDocument();
    expect(within(callRow!).getByText("NVDA")).toBeInTheDocument();
    expect(within(callRow!).getByText("Aug 11, 2026, 2:20 PM UTC")).toBeInTheDocument();
    expect(within(callRow!).getByText("$210.00 → $235.00")).toBeInTheDocument();
    expect(sourceLink).toHaveAttribute("href", "/calls/demo-call-002#source");
  });

  it("renders zero-based provider pagination as Korean human page numbers", async () => {
    await renderPage({ size: "1", page: "0" });

    expect(screen.getByText(/1\/3페이지/)).toBeInTheDocument();
    const pagination = screen.getByRole("navigation", { name: "콜 목록 페이지" });
    expect(within(pagination).getByText("이전")).toHaveAttribute("aria-disabled", "true");
    expect(within(pagination).getByRole("link", { name: "다음" })).toHaveAttribute(
      "href",
      "/calls?size=1&page=1",
    );
  });

  it("renders an explicit Korean empty state without substitute records", async () => {
    await renderPage({ ticker: "TSLA" });

    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("이 필터와 일치하는 항목이 없습니다.");
    expect(screen.getByText(/합성 값으로 대체하지 않습니다/)).toBeInTheDocument();
  });

  it("localizes loading and recoverable client error states", async () => {
    renderWithLocale(await CallsLoading(), "ko");
    expect(screen.getByRole("heading", { name: "애널리스트 콜을 불러오는 중…" })).toBeInTheDocument();

    const reset = vi.fn();
    renderWithLocale(
      <CallsError error={new Error("fixture failed")} reset={reset} />,
      "ko",
    );
    expect(screen.getByRole("heading", { name: "픽스처를 읽을 수 없습니다." })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
