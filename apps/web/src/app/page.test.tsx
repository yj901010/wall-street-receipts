import { fireEvent, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DashboardView } from "@/components/dashboard-view";
import { marketProvider } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import DashboardError from "./error";
import DashboardLoading from "./loading";
import DashboardPage from "./page";

vi.mock("@/lib/i18n/server", () => ({
  getLocale: vi.fn(async () => "ko"),
}));

function sectionForHeading(name: string) {
  const section = screen.getByRole("heading", { name }).closest("section");
  expect(section).not.toBeNull();
  return section!;
}

describe("DashboardPage", () => {
  it("renders independently sourced canonical calls and both DEMO map previews", async () => {
    renderWithLocale(await DashboardPage());

    expect(
      screen.getByRole("heading", { name: "추론으로 빈칸을 채우지 않은 시장 증거." }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Dataset provenance")).not.toBeInTheDocument();
    expect(screen.getByText(/하나의 공통 기준 시각이나 소스를 합성하지 않습니다/i))
      .toBeInTheDocument();

    const callSection = sectionForHeading("이 픽스처의 최신 콜");
    const callProvenance = within(callSection).getByLabelText("대시보드 콜 섹션 출처");
    expect(callProvenance).toHaveTextContent("fixture-analyst-calls-v1");
    expect(callProvenance).toHaveTextContent("DEMO");
    expect(within(callProvenance).getByText("기준 시각", { exact: true })).toBeInTheDocument();
    expect(callProvenance).toHaveTextContent("Aug 18, 2026, 12:00 AM UTC");
    expect(callProvenance).toHaveTextContent("원본 이벤트 시각 내림차순");
    expect(within(callSection).getByText(/커밋된 DEMO 픽스처 안에서 가장 최근/i))
      .toBeInTheDocument();
    expect(within(callSection).getByText(/no record represents a real JPMorgan or Goldman Sachs/i))
      .toBeInTheDocument();

    const callsTable = within(callSection).getByRole("table", {
      name: "커밋된 DEMO 픽스처의 최신 애널리스트 콜",
    });
    const rows = within(callsTable).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(rows.map((row) => row.querySelector<HTMLAnchorElement>(".row-link")?.getAttribute("href")))
      .toEqual([
        "/calls/demo-call-002",
        "/calls/demo-call-001",
        "/calls/demo-call-003",
      ]);
    rows.forEach((row) => expect(row).toHaveTextContent("DEMO"));

    const nullableRow = rows[2];
    expect(within(nullableRow).getByText("MSFT")).toBeInTheDocument();
    expect(within(nullableRow).getByText("DEMO unattributed neutral outlook")).toHaveAttribute(
      "href",
      "/calls/demo-call-003#source",
    );
    expect(nullableRow).toHaveTextContent("NA → NA");
    expect(nullableRow).toHaveTextContent("NA · ACTIVE · DEMO");

    for (const [title, provenanceId] of [
      ["S&P 500 지도 미리보기", "fixture-market-treemap-sp500-v1"],
      ["Nasdaq 100 지도 미리보기", "fixture-market-treemap-nasdaq100-v1"],
    ] as const) {
      const preview = screen.getByRole("heading", { name: title }).closest("article");
      expect(preview).not.toBeNull();
      const previewProvenance = within(preview!).getByLabelText(
          new RegExp(`${title.replace(" 지도 미리보기", "")}.*출처`, "i"),
        );
      expect(previewProvenance).toHaveTextContent(provenanceId);
      expect(within(previewProvenance).getByText("기준 시각", { exact: true })).toBeInTheDocument();
      expect(within(previewProvenance).getByText("생성 시각", { exact: true })).toBeInTheDocument();
      expect(within(previewProvenance).getByText("수집 시각", { exact: true })).toBeInTheDocument();
      expect(previewProvenance).toHaveTextContent("Aug 19, 2026, 12:30 AM UTC");
      expect(within(previewProvenance).getAllByText("Aug 19, 2026, 1:00 AM UTC")).toHaveLength(2);
      expect(preview).toHaveTextContent("SAMPLE · 셀 3개");
      expect(preview).toHaveTextContent("false");
      expect(preview).toHaveTextContent("외부 섹터 1개 · 산업 3개");
      expect(preview).toHaveTextContent("SYNTHETIC_MARKET_CAP_PROXY");
      expect(preview).toHaveTextContent("144 relative");
      expect(preview).toHaveTextContent("AAPL");
      expect(preview).toHaveTextContent("NA");
      expect(preview).toHaveTextContent(/does not assert official index membership/i);
    }

    expect(screen.getByRole("link", { name: "S&P 500 지도 열기" })).toHaveAttribute(
      "href",
      "/maps/sp500",
    );
    expect(screen.getByRole("link", { name: "Nasdaq 100 지도 열기" })).toHaveAttribute(
      "href",
      "/maps/nasdaq100",
    );
    expect(screen.getByRole("note")).toHaveTextContent(
      /저장된 합성 티커 셀 3개.*공식 편입도 주장하지 않습니다/is,
    );
  });

  it("renders closed unavailable and deferred states without placeholder rows or metrics", async () => {
    renderWithLocale(await DashboardPage());

    const marketBoard = sectionForHeading("시장 보드");
    expect(within(marketBoard).getByText("게시되지 않음")).toBeInTheDocument();
    expect(within(marketBoard).getByLabelText("시장 보드 제공 상태")).toHaveTextContent(
      "NOT_PUBLISHED",
    );
    expect(within(marketBoard).getByLabelText("시장 보드 제공 상태")).toHaveTextContent("NA");
    expect(marketBoard).toHaveTextContent(/콜 이벤트 스냅샷.*현재 시세로 승격하지 않습니다/i);
    expect(within(marketBoard).queryByRole("row")).not.toBeInTheDocument();

    const eventCalendar = sectionForHeading("예정 이벤트");
    expect(within(eventCalendar).getByText("게시되지 않음")).toBeInTheDocument();
    expect(within(eventCalendar).getByLabelText("예정 이벤트 제공 상태"))
      .toHaveTextContent("NOT_PUBLISHED");
    expect(eventCalendar).toHaveTextContent(/과거 콜에 붙은 상태로 유지/i);
    expect(within(eventCalendar).queryByRole("row")).not.toBeInTheDocument();

    const ranking = sectionForHeading("순위 미리보기");
    expect(within(ranking).getByText("P3로 연기")).toBeInTheDocument();
    expect(within(ranking).getByLabelText("순위 미리보기 제공 상태"))
      .toHaveTextContent("P3_DEFERRED");
    expect(within(ranking).getByLabelText("순위 미리보기 제공 상태")).toHaveTextContent("NA");
    expect(within(ranking).queryByRole("row")).not.toBeInTheDocument();
    expect(within(ranking).queryByRole("table")).not.toBeInTheDocument();
    expect(within(ranking).getByRole("link", { name: "방법론 증거 검토" }))
      .toHaveAttribute("href", "/methodology");

    expect(screen.queryByText("5,278.52")).not.toBeInTheDocument();
    expect(screen.queryByText("18,752.34")).not.toBeInTheDocument();
    expect(screen.queryByText("13.72")).not.toBeInTheDocument();
    expect(screen.queryByText("Versioned local fixture v1")).not.toBeInTheDocument();
  });

  it("renders explicit call and map empty states without substituting another dataset", async () => {
    const snapshot = structuredClone(await marketProvider().dashboard());
    snapshot.latestCalls.items = [];
    snapshot.mapPreviews.forEach((preview) => {
      preview.cells = [];
      preview.coverage.cellCount = 0;
    });

    renderWithLocale(<DashboardView snapshot={snapshot} locale="ko" />);

    expect(screen.getByText("기록된 콜 이벤트가 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("기록된 S&P 500 미리보기 셀이 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("기록된 Nasdaq 100 미리보기 셀이 없습니다.")).toBeInTheDocument();
    expect(screen.getAllByText(/다른 유니버스의 셀로 대체하지 않았습니다/i)).toHaveLength(2);
    expect(screen.queryByRole("table", {
      name: "커밋된 DEMO 픽스처의 최신 애널리스트 콜",
    })).not.toBeInTheDocument();
  });

  it("provides explicit loading and recoverable error boundaries", async () => {
    const { unmount } = renderWithLocale(await DashboardLoading());
    expect(document.querySelector('[aria-busy="true"]')).toHaveTextContent(
      "독립 소스의 DEMO 섹션을 불러오는 중",
    );
    expect(screen.getByText(/공통 시각, 소스, 시세, 이벤트 또는 순위를 임의로 채우지 않습니다/i))
      .toBeInTheDocument();
    unmount();

    const reset = vi.fn();
    renderWithLocale(<DashboardError error={new Error("fixture failure")} reset={reset} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "픽스처 섹션을 구성할 수 없습니다.",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(/부분 시세.*대체 유니버스/i);
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(reset).toHaveBeenCalledOnce();
    expect(screen.getByRole("link", { name: "콜 원장 열기" })).toHaveAttribute(
      "href",
      "/calls",
    );
  });

  it("renders the English catalog without changing canonical evidence values", async () => {
    const snapshot = await marketProvider().dashboard();
    renderWithLocale(<DashboardView snapshot={snapshot} locale="en" />, "en");

    expect(screen.getByRole("heading", { name: "Market evidence, without inferred gaps." }))
      .toBeInTheDocument();
    expect(screen.getByText("Aug 18, 2026, 12:00 AM UTC")).toBeInTheDocument();
    expect(screen.getAllByText("fixture-market-treemap-sp500-v1").length).toBeGreaterThan(0);
    expect(screen.getAllByText("DEMO").length).toBeGreaterThan(0);
  });
});
