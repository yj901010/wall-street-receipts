import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithLocale } from "@/test/render-with-locale";
import MarketError from "./error";
import MarketLoading from "./loading";
import MarketPage from "./page";

const i18n = vi.hoisted(() => ({ getLocale: vi.fn() }));

vi.mock("@/lib/i18n/server", () => ({ getLocale: i18n.getLocale }));

describe("MarketPage", () => {
  beforeEach(() => {
    i18n.getLocale.mockReset();
    i18n.getLocale.mockResolvedValue("ko");
  });

  it("renders Korean-default publication UI with exact known-unavailable evidence", async () => {
    renderWithLocale(await MarketPage());

    expect(screen.getByRole("heading", {
      name: "글로벌 시장 보드는 게시되지 않았습니다.",
    })).toBeInTheDocument();
    const marketLink = screen.getByRole("link", { name: "시장" });
    expect(marketLink).toHaveAttribute("aria-current", "page");
    expect(marketLink).toHaveAttribute("href", "/market");
    expect(screen.getByRole("link", { name: "대시보드" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", {
      name: "기록된 S&P 500 콜 이벤트 이력 열기",
    })).toHaveAttribute("href", "/markets/sp500");

    const provenance = screen.getByLabelText("시장 보드 픽스처 출처");
    for (const value of ["1.0.0", "v1", "fixture-market-board-v1", "DEMO"]) {
      expect(within(provenance).getByText(value, { exact: true })).toBeInTheDocument();
    }
    expect(within(provenance).getAllByText("Aug 19, 2026, 2:00 AM UTC", { exact: true }))
      .toHaveLength(2);

    const publication = screen.getByRole("region", { name: "시장 보드 게시 상태" });
    expect(publication).toHaveAttribute("tabindex", "0");
    expect(within(publication).getByText("게시되지 않음", { exact: true })).toBeInTheDocument();

    const policy = within(publication).getByLabelText("시장 보드 게시 정책");
    expect(within(policy).getByText("게시 정책 · 시장 증거 아님", { exact: true })).toBeVisible();
    expect(policy).toHaveTextContent("지연, 장마감 또는 현재 호가 화면이 아닙니다");
    expect(policy).toHaveTextContent("콜 이벤트 스냅샷과 합성 지도 표본");
    expect(policy).toHaveTextContent("누락값을 0으로 바꾸지 않습니다");

    const status = within(publication).getByLabelText("게시되지 않은 시장 보드 상태");
    for (const value of [
      "NOT_PUBLISHED",
      "GLOBAL_MARKET_OVERVIEW",
      "NO_CANONICAL_GLOBAL_QUOTE_CATALOG",
    ]) {
      expect(status).toHaveTextContent(value);
    }
    expect(within(status).getAllByText("NA", { exact: true })).toHaveLength(2);

    const metadata = within(publication).getByLabelText("시장 보드 정책 메타데이터");
    expect(metadata).toHaveTextContent("시장 기준 시각");
    expect(metadata).toHaveTextContent("LOCAL_SPECIFICATION");
    expect(metadata).toHaveTextContent("INTERNAL_DEMO");
    expect(metadata).toHaveTextContent("true");

    const paths = within(publication).getByLabelText("시장 보드 소스 경로");
    const sourcePathItems = within(within(paths).getByRole("list")).getAllByRole("listitem");
    expect(sourcePathItems.map((item) => item.textContent)).toEqual([
      "schemas/market-board.schema.json",
      "quality/P2_ACCEPTANCE.md",
    ]);
    expect(within(publication).getByText(/Known-unavailable DEMO publication state only/))
      .toHaveTextContent("Not investment advice.");

    expect(within(publication).queryByRole("table")).not.toBeInTheDocument();
    for (const absent of ["5278.52", "183.42", "SPX", "NVDA"]) {
      expect(screen.queryByText(absent, { exact: true })).not.toBeInTheDocument();
    }
  });

  it("renders English cookie UI while preserving byte-stable policy evidence", async () => {
    i18n.getLocale.mockResolvedValue("en");
    renderWithLocale(await MarketPage(), "en");

    expect(screen.getByRole("heading", {
      name: "A global market board is not published.",
    })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Market board publication state" }))
      .toHaveTextContent("NO_CANONICAL_GLOBAL_QUOTE_CATALOG");
    expect(screen.getAllByText("Aug 19, 2026, 2:00 AM UTC", { exact: true })).toHaveLength(2);
    expect(screen.getByText(/Known-unavailable DEMO publication state only/))
      .toHaveTextContent("Not investment advice.");
  });

  it("keeps Korean DEMO navigation and no-fallback loading and error states", async () => {
    const loading = renderWithLocale(await MarketLoading());

    expect(screen.getByText("DEMO 게시 레코드를 불러오는 중…").closest("main"))
      .toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("DEMO", { selector: ".mode-badge" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "시장" })).toHaveAttribute("aria-current", "page");
    loading.unmount();

    const reset = vi.fn();
    renderWithLocale(<MarketError error={new Error("fixture failed")} reset={reset} />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "부분 호가, 콜 이벤트 스냅샷, 합성 지도 값 또는 애플리케이션 리터럴을 대신 표시하지 않습니다.",
    );
    expect(screen.getByRole("link", { name: "대시보드 증거로 돌아가기" }))
      .toHaveAttribute("href", "/");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
  });
});
