import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Locale } from "@/lib/i18n/config";
import { FixtureCallsProvider } from "@/lib/providers";
import { renderWithLocale } from "@/test/render-with-locale";
import CallDetailLoading from "./loading";
import CallNotFound from "./not-found";
import CallDetailPage from "./page";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));
const providers = vi.hoisted(() => ({
  callsProvider: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

vi.mock("@/lib/providers", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/providers")>(),
  callsProvider: providers.callsProvider,
}));

type SourceLocation = {
  page: number | null;
  startMs: number | null;
  endMs: number | null;
};

class SourceLocationFixtureProvider extends FixtureCallsProvider {
  constructor(private readonly location: SourceLocation) {
    super();
  }

  override async findById(id: string) {
    const detail = await super.findById(id);

    if (!detail || id !== "demo-call-002") {
      return detail;
    }

    return {
      ...detail,
      source: {
        ...detail.source,
        reference: {
          ...detail.source.reference,
          ...this.location,
        },
      },
    };
  }
}

async function renderDetail(id: string, locale: Locale = "ko") {
  i18nServer.getLocale.mockResolvedValue(locale);
  return renderWithLocale(
    await CallDetailPage({ params: Promise.resolve({ id }) }),
    locale,
  );
}

describe("CallDetailPage", () => {
  beforeEach(() => {
    i18nServer.getLocale.mockReset();
    i18nServer.getLocale.mockResolvedValue("ko");
    providers.callsProvider.mockReset();
    providers.callsProvider.mockImplementation(() => new FixtureCallsProvider());
  });

  it("separates Korean-labelled event and processing time while preserving source provenance", async () => {
    await renderDetail("demo-call-002");

    expect(screen.getByRole("heading", { name: "Goldman Sachs · NVDA 콜" })).toBeInTheDocument();
    expect(screen.getByText("DEMO Buy")).toBeInTheDocument();
    expect(screen.getByText("2분")).toBeInTheDocument();
    expect(screen.getByText("+$25.00 (+11.9%)")).toBeInTheDocument();
    expect(screen.getByText("$183.42")).toBeInTheDocument();
    expect(screen.getAllByText("Aug 11, 2026, 2:20 PM UTC").length).toBeGreaterThan(0);

    const eventSection = screen.getByRole("heading", { name: "콜 사실" }).closest("section");
    expect(eventSection).not.toBeNull();
    expect(within(eventSection!).getByText("이벤트 시각")).toBeInTheDocument();
    expect(within(eventSection!).getByText("처리 시각")).toBeInTheDocument();

    const sourceSection = screen.getByRole("heading", { name: "출처 추적 정보" }).closest("section");
    expect(sourceSection).not.toBeNull();
    expect(within(sourceSection!).getByText("DEMO Channel")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("INTERNAL_DEMO")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("source-ref-demo-002")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("fixture")).toBeInTheDocument();
    expect(within(sourceSection!).getByText("참조 출처 계보")).toBeInTheDocument();
    expect(within(sourceSection!).getAllByText("fixture-analyst-calls-v1")).toHaveLength(2);
    expect(within(sourceSection!).getByText("페이지 / 시간 오프셋")).toBeInTheDocument();
    expect(within(sourceSection!).getByRole("link", { name: "정규 출처 열기" })).toHaveAttribute(
      "href",
      "https://example.invalid/demo-call-002",
    );
    expect(sourceSection).toHaveAttribute("id", "source");
  });

  it.each([
    {
      english: "Page 7 · 1250–3750 ms",
      korean: "7페이지 · 1250–3750 ms",
      location: { page: 7, startMs: 1250, endMs: 3750 },
    },
    {
      english: "From 1250 ms",
      korean: "1250 ms부터",
      location: { page: null, startMs: 1250, endMs: null },
    },
    {
      english: "Until 3750 ms",
      korean: "3750 ms까지",
      location: { page: null, startMs: null, endMs: 3750 },
    },
  ] satisfies Array<{
    english: string;
    korean: string;
    location: SourceLocation;
  }>)("localizes source location $korean without changing numeric evidence", async ({
    english,
    korean,
    location,
  }) => {
    providers.callsProvider.mockImplementation(() => new SourceLocationFixtureProvider(location));

    const koreanView = await renderDetail("demo-call-002", "ko");
    expect(screen.getByText(korean)).toBeInTheDocument();
    koreanView.unmount();

    await renderDetail("demo-call-002", "en");
    expect(screen.getByText(english)).toBeInTheDocument();
  });

  it("renders unavailable source metadata as NA without a canonical source link", async () => {
    await renderDetail("demo-call-003");

    const sourceSection = screen.getByRole("heading", { name: "출처 추적 정보" }).closest("section");
    expect(sourceSection).not.toBeNull();

    for (const label of ["발행처", "외부 ID", "발행 시각", "콘텐츠 해시"]) {
      const term = within(sourceSection!).getByText(label);
      expect(term.nextElementSibling).toHaveTextContent(/^NA$/);
    }

    expect(within(sourceSection!).queryByRole("link", { name: "정규 출처 열기" })).not.toBeInTheDocument();
    expect(within(sourceSection!).getByText("정규 출처 URL: NA")).toBeInTheDocument();
  });

  it("labels the snapshot immutable and renders unavailable values as NA", async () => {
    await renderDetail("demo-call-002");

    expect(screen.getByText("변경 불가 시점 기준 기록")).toBeInTheDocument();
    expect(screen.getAllByText("추가 전용, 수정 경로 없음").length).toBeGreaterThan(0);
    expect(screen.getByText("스냅샷 처리 시각")).toBeInTheDocument();
    expect(screen.getByText("fixture-market-snapshots-v1")).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThan(5);
    expect(screen.getByText(/버전이 있는 방법론으로 계산되기 전까지 성과 값은 NA/)).toBeInTheDocument();
  });

  it("renders accessible point-in-time macro and scheduled-event evidence without derived claims", async () => {
    await renderDetail("demo-call-001");

    const macroSection = screen.getByRole("heading", { name: "거시 컨텍스트" }).closest("section");
    expect(macroSection).not.toBeNull();
    expect(within(macroSection!).getByText("macro-snapshot-demo-001")).toBeInTheDocument();
    expect(within(macroSection!).getAllByText("fixture-call-contexts-v1").length).toBeGreaterThan(1);

    const macroRegion = within(macroSection!).getByRole("region", {
      name: "거시 관측 증거 표",
    });
    expect(macroRegion).toHaveAttribute("tabindex", "0");
    expect(within(macroRegion).getByRole("table", {
      name: "애널리스트 콜 이벤트 시점의 거시 관측값",
    })).toBeInTheDocument();
    expect(within(macroRegion).getAllByRole("columnheader")).toHaveLength(11);

    const ppiRow = within(macroRegion).getByRole("row", { name: /PPI_YOY/ });
    expect(ppiRow.querySelector('[data-field="value"]')).toHaveTextContent(/^NA$/);
    expect(ppiRow.querySelector('[data-field="vintage-end"]')).toHaveTextContent(/^NA$/);
    expect(within(ppiRow).getByText("source-ref-demo-macro-inflation-original-001")).toBeInTheDocument();

    const cpiRow = within(macroRegion).getByRole("row", { name: /CPI_YOY macro-observation-demo-cpi-original-001/ });
    expect(cpiRow.querySelector('[data-field="value"]')).toHaveTextContent(/^3.1$/);
    expect(within(macroSection!).queryByText("macro-observation-demo-cpi-revision-001")).not.toBeInTheDocument();

    const eventSection = screen.getByRole("heading", { name: "예정 이벤트 컨텍스트" }).closest("section");
    expect(eventSection).not.toBeNull();
    expect(within(eventSection!).getByText("event-context-demo-001")).toBeInTheDocument();
    expect(within(eventSection!).getByText("source-ref-demo-event-calendar-001")).toBeInTheDocument();

    const earnings = within(eventSection!).getByText("실적 발표");
    const nextCpi = within(eventSection!).getByText("다음 CPI");
    expect(earnings.nextElementSibling).toHaveTextContent(/^NA$/);
    expect(nextCpi.nextElementSibling).toHaveTextContent("Aug 12, 2026, 12:30 PM UTC");
    expect(within(macroSection!).queryByText(/proximity|regime|score|days? until|근접|국면|점수/i)).not.toBeInTheDocument();
    expect(within(eventSection!).queryByText(/proximity|regime|score|days? until|근접|국면|점수/i)).not.toBeInTheDocument();
  });

  it("keeps known-empty call context explicit and substitutes no values", async () => {
    await renderDetail("demo-call-002");

    const macroSection = screen.getByRole("heading", { name: "거시 컨텍스트" }).closest("section");
    const eventSection = screen.getByRole("heading", { name: "예정 이벤트 컨텍스트" }).closest("section");
    expect(macroSection).not.toBeNull();
    expect(eventSection).not.toBeNull();

    for (const section of [macroSection!, eventSection!]) {
      expect(within(section).getByText("확인된 빈 상태 · DEMO")).toBeInTheDocument();
      expect(within(section).getByRole("status")).toHaveTextContent("누락 값은 NA로 유지됩니다.");
      expect(within(section).getByText("출처").nextElementSibling).toHaveTextContent(/^NA$/);
      expect(within(section).getByText("출처 계보").nextElementSibling).toHaveTextContent(/^NA$/);
    }

    expect(within(macroSection!).queryByRole("table")).not.toBeInTheDocument();
    expect(within(eventSection!).queryByLabelText("관측된 예정 이벤트 시각")).not.toBeInTheDocument();
  });

  it("renders English UI while keeping date, money, IDs, source evidence, and NA identical", async () => {
    await renderDetail("demo-call-002", "en");

    expect(screen.getByRole("heading", { name: "Goldman Sachs on NVDA" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Call facts" })).toBeInTheDocument();
    expect(screen.getByText("2 minutes")).toBeInTheDocument();
    expect(screen.getByText("+$25.00 (+11.9%)")).toBeInTheDocument();
    expect(screen.getByText("$183.42")).toBeInTheDocument();
    expect(screen.getAllByText("Aug 11, 2026, 2:20 PM UTC").length).toBeGreaterThan(0);
    expect(screen.getByText("source-ref-demo-002")).toBeInTheDocument();
    expect(screen.getAllByText("NA").length).toBeGreaterThan(5);
    expect(screen.getByRole("link", { name: "Open canonical source" })).toHaveAttribute(
      "href",
      "https://example.invalid/demo-call-002",
    );
  });

  it("localizes detail loading and not-found states in Korean", async () => {
    renderWithLocale(await CallDetailLoading(), "ko");
    expect(screen.getByRole("heading", { name: "콜 증거를 불러오는 중…" })).toBeInTheDocument();

    renderWithLocale(await CallNotFound(), "ko");
    expect(screen.getByRole("heading", { name: "이 이벤트는 픽스처 원장에 없습니다." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "애널리스트 콜로 돌아가기" })).toHaveAttribute(
      "href",
      "/calls",
    );
  });
});
