import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Locale } from "@/lib/i18n/config";
import { FixtureCallsProvider } from "@/lib/providers";
import { FixtureCallAuditProvider } from "@/lib/providers/fixture-call-audit-provider";
import { renderWithLocale } from "@/test/render-with-locale";
import CallDetailLoading from "./loading";
import CallNotFound from "./not-found";
import CallDetailPage from "./page";

const i18nServer = vi.hoisted(() => ({
  getLocale: vi.fn(),
}));
const providers = vi.hoisted(() => ({
  callAuditProvider: vi.fn(),
}));

vi.mock("@/lib/i18n/server", () => ({
  getLocale: i18nServer.getLocale,
}));

vi.mock("@/lib/providers/call-audit-provider.server", () => ({
  callAuditProvider: providers.callAuditProvider,
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

class PrecisionAuditProvider extends FixtureCallAuditProvider {
  override async findById(id: string) {
    const audit = structuredClone(await super.findById(id));
    if (!audit || id !== "demo-call-002") return audit;
    const correction = audit.revisions[0];
    if (!correction?.correctedTerms) throw new Error("Expected correction fixture.");
    correction.eventTime = "2026-08-11T14:40:00.000001Z";
    correction.processingTime = "2026-08-11T14:42:00.000002Z";
    correction.capturedAt = "2026-08-11T14:42:00.000003Z";
    correction.correctedTerms.previousTarget = 210.123456;
    correction.correctedTerms.target = 232.987654;
    return audit;
  }
}

class OutcomePrecisionAuditProvider extends FixtureCallAuditProvider {
  override async findById(id: string) {
    const audit = structuredClone(await super.findById(id));
    if (!audit || id !== "demo-call-001") return audit;
    const outcome = audit.outcomes[0];
    const successor = audit.outcomes[1];
    if (!outcome || !successor) throw new Error("Expected outcome fixture lineage.");
    outcome.eventTime = "2026-08-11T20:00:00.000001Z";
    successor.eventTime = "2026-08-11T20:00:00.000001Z";
    outcome.processingTime = "2026-08-11T20:01:00.000002Z";
    outcome.capturedAt = "2026-08-11T20:01:00.000003Z";
    return audit;
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
    providers.callAuditProvider.mockReset();
    providers.callAuditProvider.mockImplementation(() => new FixtureCallAuditProvider());
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
    providers.callAuditProvider.mockImplementation(
      () => new FixtureCallAuditProvider(new SourceLocationFixtureProvider(location)),
    );

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
    expect(screen.getByText(/P2 감사 경계에서는 계산되지 않은 결과 필드/)).toBeInTheDocument();
  });

  it("renders all append-only outcome evidence in Spring response order without projection", async () => {
    await renderDetail("demo-call-001");

    const section = screen.getByRole("heading", { name: "성과 감사 이력" }).closest("section");
    expect(section).not.toBeNull();
    expect(within(section!).getByText("감사 전용 · DEMO")).toBeInTheDocument();
    expect(within(section!).getByText("성과 기록 4건")).toBeInTheDocument();
    expect(within(section!).getByText(/응답 순서를 그대로 보존한 추가 전용 이력/)).toBeInTheDocument();
    expect(within(section!).getByText(/JSON null로만 허용/)).toBeInTheDocument();

    const articles = within(section!).getAllByRole("article");
    expect(articles.map((article) => article.getAttribute("aria-label"))).toEqual([
      "성과 기록 1 · D1 · 방법론 1.0.0 · INCOMPLETE",
      "성과 기록 2 · D1 · 방법론 1.0.0 · INCOMPLETE",
      "성과 기록 1 · D1 · 방법론 2.0.0 · INCOMPLETE",
      "성과 기록 1 · M1 · 방법론 1.0.0 · PENDING",
    ]);
    expect(articles.map((article) => article.textContent)).toEqual([
      expect.stringContaining("outcome-demo-call-001-d1-v1-001"),
      expect.stringContaining("outcome-demo-call-001-d1-v1-002"),
      expect.stringContaining("outcome-demo-call-001-d1-v2-001"),
      expect.stringContaining("outcome-demo-call-001-m1-v1-001"),
    ]);
    expect(section).toHaveAttribute("tabindex", "0");

    const first = articles[0]!;
    const firstOutcomeLabels = Array.from(first.querySelectorAll("dt"), (node) => node.textContent);
    const firstOutcomeValues = Array.from(first.querySelectorAll("dd"), (node) => node.textContent);
    expect(firstOutcomeLabels).toEqual([
      "성과 ID",
      "스키마 버전",
      "콜 ID",
      "평가 구간",
      "기준 변경 ID",
      "취소 증거 ID",
      "스냅샷 ID",
      "방법론 ID",
      "방법론 버전",
      "방법론 정의 해시",
      "입력 지문",
      "계보 순번",
      "직전 성과 ID",
      "평가 상태",
      "사유 코드",
      "성과 이벤트 시각",
      "성과 처리 시각",
      "성과 수집 시각",
      "자산 수익률",
      "벤치마크 수익률",
      "섹터 수익률",
      "알파",
      "섹터 알파",
      "최대 유리 변동(MFE)",
      "최대 불리 변동(MAE)",
      "목표가 도달",
      "방향 적중",
      "목표 오차",
      "데이터 완결",
      "데이터 모드",
      "성과 출처 계보",
    ]);
    expect(firstOutcomeValues).toEqual([
      "outcome-demo-call-001-d1-v1-001",
      "1.0.0",
      "demo-call-001",
      "D1",
      "NA",
      "NA",
      "market-snapshot-demo-001",
      "standard-call-outcome",
      "1.0.0",
      "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
      "b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6",
      "1",
      "NA",
      "INCOMPLETE",
      "HORIZON_DATA_MISSING",
      "2026-08-11T20:00:00Z",
      "2026-08-11T20:01:00Z",
      "2026-08-11T20:01:00Z",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "NA",
      "false",
      "DEMO",
      "fixture-call-outcomes-v1",
    ]);
    expect(within(first).getByText("outcome-demo-call-001-d1-v1-001")).toBeInTheDocument();
    expect(within(first).getByText("standard-call-outcome")).toBeInTheDocument();
    expect(within(first).getByText("HORIZON_DATA_MISSING")).toBeInTheDocument();
    expect(within(first).getByText("market-snapshot-demo-001")).toBeInTheDocument();
    expect(within(first).getByText("방법론 정의 해시")).toBeInTheDocument();
    expect(within(first).getByText("입력 지문")).toBeInTheDocument();
    expect(within(first).getByText("false", { exact: true })).toBeInTheDocument();
    expect(within(first).getAllByText("NA")).toHaveLength(13);
    expect(within(first).getAllByText("2026-08-11T20:01:00Z")).toHaveLength(2);
    for (const rawTime of within(first).getAllByText("2026-08-11T20:01:00Z")) {
      expect(rawTime).toHaveAttribute("datetime", "2026-08-11T20:01:00Z");
    }
    expect(within(first).getAllByText("03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2").length)
      .toBeGreaterThan(0);
    expect(within(first).getByText("b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6"))
      .toBeInTheDocument();
    expect(within(articles[3]!).getByText("HORIZON_NOT_REACHED")).toBeInTheDocument();

    for (const unsupportedProjection of [
      "최신 성과", "현재 성과", "유효 성과", "성과 점수", "승패", "애널리스트 순위", "투자 조언",
      "방법론 활성", "방법론 비활성",
    ]) {
      expect(within(section!).queryByText(unsupportedProjection, { exact: true })).not.toBeInTheDocument();
    }
  });

  it("preserves raw outcome microseconds without presentation formatting", async () => {
    providers.callAuditProvider.mockImplementation(() => new OutcomePrecisionAuditProvider());
    await renderDetail("demo-call-001");

    const outcome = screen.getByRole("article", {
      name: "성과 기록 1 · D1 · 방법론 1.0.0 · INCOMPLETE",
    });
    for (const instant of [
      "2026-08-11T20:00:00.000001Z",
      "2026-08-11T20:01:00.000002Z",
      "2026-08-11T20:01:00.000003Z",
    ]) {
      expect(within(outcome).getByText(instant)).toHaveAttribute("datetime", instant);
    }
  });

  it("keeps a known-empty outcome response bounded and does not infer EXCLUDED from cancellation", async () => {
    await renderDetail("demo-call-002");

    const section = screen.getByRole("heading", { name: "성과 감사 이력" }).closest("section");
    expect(section).not.toBeNull();
    expect(within(section!).getByText("성과 기록 0건")).toBeInTheDocument();
    expect(within(section!).getByRole("status")).toHaveTextContent(
      "이 감사 응답에는 기록된 성과 이벤트가 없습니다. 다른 이력이나 대체 결과를 표시하지 않았습니다.",
    );
    expect(within(section!).queryByRole("article")).not.toBeInTheDocument();
    expect(within(section!).getByText(/취소를 EXCLUDED 성과나 다른 결과로 추론하지 않습니다/)).toBeInTheDocument();
    expect(screen.getByRole("article", { name: "변경 2 · CANCELLATION" })).toBeInTheDocument();
    expect(screen.getByText("ACTIVE", { exact: true })).toBeInTheDocument();
  });

  it("renders the append-only revision lineage in Korean without translating canonical evidence", async () => {
    await renderDetail("demo-call-002");

    const section = screen.getByRole("heading", { name: "콜 변경 이력" }).closest("section");
    expect(section).not.toBeNull();
    expect(within(section!).getByText("변경 이벤트 2건")).toBeInTheDocument();
    expect(within(section!).getByText(/상단 상태는 변경 불가 원본 이벤트 필드/)).toBeInTheDocument();

    const correction = within(section!).getByRole("article", { name: "변경 1 · CORRECTION" });
    expect(within(correction).getByText("demo-call-revision-001")).toBeInTheDocument();
    expect(within(correction).getByText("fixture-call-revision-001")).toBeInTheDocument();
    expect(within(correction).getByText("DEMO Buy (corrected)")).toBeInTheDocument();
    expect(within(correction).getByText("232")).toBeInTheDocument();
    expect(within(correction).getByText("2026-08-11T14:40:00Z")).toBeInTheDocument();
    const correctedTargetDate = within(correction).getByText("정정 목표 기준일");
    expect(correctedTargetDate.nextElementSibling).toHaveTextContent(/^NA$/);

    const cancellation = within(section!).getByRole("article", { name: "변경 2 · CANCELLATION" });
    expect(within(cancellation).getByText("demo-call-revision-002")).toBeInTheDocument();
    expect(within(cancellation).getByText("취소 이벤트에는 정정 조건이 없습니다.")).toBeInTheDocument();
    expect(within(cancellation).getByText("2026-08-11T15:00:00Z")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(screen.getAllByText("BULLISH", { exact: true }).length).toBeGreaterThan(0);
    expect(screen.getByText("$235.00")).toBeInTheDocument();
    for (const unsupportedProjection of ["유효 상태", "현재 성과", "애널리스트 순위", "투자 조언"]) {
      expect(within(section!).queryByText(unsupportedProjection, { exact: true })).not.toBeInTheDocument();
    }
  });

  it("keeps an empty revision response explicitly response-bounded", async () => {
    await renderDetail("demo-call-001");

    const section = screen.getByRole("heading", { name: "콜 변경 이력" }).closest("section");
    expect(section).not.toBeNull();
    expect(within(section!).getByText("변경 이벤트 0건")).toBeInTheDocument();
    expect(within(section!).getByRole("status")).toHaveTextContent(
      "이 조회 응답에는 기록된 정정 또는 취소 이벤트가 없습니다.",
    );
    expect(within(section!).queryByRole("article")).not.toBeInTheDocument();
  });

  it("renders revision microseconds and numeric evidence without presentation rounding", async () => {
    providers.callAuditProvider.mockImplementation(() => new PrecisionAuditProvider());
    await renderDetail("demo-call-002");

    const correction = screen.getByRole("article", { name: "변경 1 · CORRECTION" });
    const rawTime = within(correction).getByText("2026-08-11T14:40:00.000001Z");
    expect(rawTime).toHaveAttribute("datetime", "2026-08-11T14:40:00.000001Z");
    expect(within(correction).getByText("2026-08-11T14:42:00.000002Z")).toBeInTheDocument();
    expect(within(correction).getByText("2026-08-11T14:42:00.000003Z")).toBeInTheDocument();
    expect(within(correction).getByText("210.123456")).toBeInTheDocument();
    expect(within(correction).getByText("232.987654")).toBeInTheDocument();
    expect(within(correction).getByText("USD", { exact: true })).toBeInTheDocument();
    expect(screen.getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(screen.getAllByText("BULLISH", { exact: true }).length).toBeGreaterThan(0);
    expect(screen.getByText("$235.00")).toBeInTheDocument();
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

  it("renders the populated outcome audit in English without translating canonical evidence", async () => {
    await renderDetail("demo-call-001", "en");

    const section = screen.getByRole("heading", { name: "Outcome audit history" }).closest("section");
    expect(section).not.toBeNull();
    expect(within(section!).getByText("AUDIT ONLY · DEMO")).toBeInTheDocument();
    expect(within(section!).getByText("4 outcome records")).toBeInTheDocument();
    expect(within(section!).getByText(/accepted only as JSON null and displayed as NA/)).toBeInTheDocument();
    const first = within(section!).getByRole("article", {
      name: "Outcome record 1 · D1 · methodology 1.0.0 · INCOMPLETE",
    });
    expect(within(first).getByText("Outcome ID")).toBeInTheDocument();
    expect(within(first).getByText("Methodology definition hash")).toBeInTheDocument();
    expect(within(first).getByText("Input fingerprint")).toBeInTheDocument();
    expect(within(first).getByText("outcome-demo-call-001-d1-v1-001")).toBeInTheDocument();
    expect(within(first).getByText("HORIZON_DATA_MISSING")).toBeInTheDocument();
    expect(within(first).getByText("03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2"))
      .toBeInTheDocument();
    expect(within(first).getByText("b359ec47c7a5b17bc6a7ee18e82f1fe92eb100f9e2abee23a8e3c9aa7b94acd6"))
      .toBeInTheDocument();
    expect(within(first).getAllByText("NA")).toHaveLength(13);
    for (const unsupportedProjection of [
      "Latest outcome", "Current outcome", "Effective outcome", "Outcome score", "Win/loss", "Analyst rank",
      "Investment advice", "Methodology active", "Methodology inactive",
    ]) {
      expect(within(section!).queryByText(unsupportedProjection, { exact: true })).not.toBeInTheDocument();
    }
  });

  it("renders English UI while keeping date, money, IDs, source evidence, and NA identical", async () => {
    await renderDetail("demo-call-002", "en");

    expect(screen.getByRole("heading", { name: "Goldman Sachs on NVDA" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Call facts" })).toBeInTheDocument();
    expect(screen.getByText("2 minutes")).toBeInTheDocument();
    expect(screen.getByText("+$25.00 (+11.9%)")).toBeInTheDocument();
    expect(screen.getByText("$183.42")).toBeInTheDocument();
    expect(screen.getAllByText("Aug 11, 2026, 2:20 PM UTC").length).toBeGreaterThan(0);
    expect(screen.getAllByText("source-ref-demo-002").length).toBeGreaterThan(0);
    expect(screen.getAllByText("NA").length).toBeGreaterThan(5);
    expect(screen.getByRole("heading", { name: "Call revision history" })).toBeInTheDocument();
    expect(screen.getByRole("article", { name: "Revision 1 · CORRECTION" })).toHaveTextContent(
      "demo-call-revision-001",
    );
    expect(screen.getByRole("article", { name: "Revision 2 · CANCELLATION" })).toHaveTextContent(
      "demo-call-revision-002",
    );
    expect(screen.getByText("Cancellation events do not carry corrected terms.")).toBeInTheDocument();
    expect(screen.getByText(/not a current or effective stance/)).toBeInTheDocument();
    expect(screen.getByText("ACTIVE", { exact: true })).toBeInTheDocument();
    expect(screen.getAllByText("BULLISH", { exact: true }).length).toBeGreaterThan(0);
    expect(screen.getByText("$235.00")).toBeInTheDocument();
    const revisionSection = screen.getByRole("heading", { name: "Call revision history" }).closest("section");
    expect(revisionSection).not.toBeNull();
    for (const unsupportedProjection of ["Effective status", "Current outcome", "Analyst rank", "Investment advice"]) {
      expect(within(revisionSection!).queryByText(unsupportedProjection, { exact: true })).not.toBeInTheDocument();
    }
    const outcomeSection = screen.getByRole("heading", { name: "Outcome audit history" }).closest("section");
    expect(outcomeSection).not.toBeNull();
    expect(within(outcomeSection!).getByText("0 outcome records")).toBeInTheDocument();
    expect(within(outcomeSection!).getByRole("status")).toHaveTextContent(
      "No outcome event is recorded in this audit response. No other history or substitute result was shown.",
    );
    expect(within(outcomeSection!).queryByRole("article")).not.toBeInTheDocument();
    expect(within(outcomeSection!).queryByText("Methodology not active", { exact: true })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open canonical source" })).toHaveAttribute(
      "href",
      "https://example.invalid/demo-call-002",
    );
  });

  it("maps only a null whole-audit result to the localized not-found boundary", async () => {
    let thrown: unknown;
    try {
      await CallDetailPage({ params: Promise.resolve({ id: "missing-call" }) });
    } catch (error) {
      thrown = error;
    }
    expect(thrown).toMatchObject({ digest: expect.stringContaining("404") });
  });

  it("reads the coherent detail/context/revision/outcome aggregate exactly once on success", async () => {
    const fixture = new FixtureCallAuditProvider();
    const findById = vi.fn(fixture.findById.bind(fixture));
    providers.callAuditProvider.mockReturnValue({ findById });

    await renderDetail("demo-call-001");

    expect(providers.callAuditProvider).toHaveBeenCalledTimes(1);
    expect(findById).toHaveBeenCalledTimes(1);
    expect(findById).toHaveBeenCalledWith("demo-call-001");
  });

  it("propagates provider rejection so the route error boundary cannot publish partial evidence", async () => {
    const failure = new Error("outcome response malformed");
    providers.callAuditProvider.mockReturnValue({
      findById: vi.fn().mockRejectedValue(failure),
    });
    await expect(CallDetailPage({ params: Promise.resolve({ id: "demo-call-002" }) }))
      .rejects.toBe(failure);
  });

  it("localizes detail loading and not-found states in Korean", async () => {
    renderWithLocale(await CallDetailLoading(), "ko");
    expect(screen.getByRole("heading", { name: "콜 증거를 불러오는 중…" })).toBeInTheDocument();

    renderWithLocale(await CallNotFound(), "ko");
    expect(screen.getByRole("heading", { name: "이 이벤트는 정규 콜 원장에 없습니다." })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "애널리스트 콜로 돌아가기" })).toHaveAttribute(
      "href",
      "/calls",
    );
  });
});
