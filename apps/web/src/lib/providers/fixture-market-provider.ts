import type { CallsProvider } from "./calls-provider";
import type { DashboardSnapshot, MarketProvider } from "./market-provider";
import type {
  MarketTreemapProvider,
  MarketTreemapSnapshot,
  MarketTreemapUniverse,
} from "./market-treemap-provider";

const previewUniverses = ["sp500", "nasdaq100"] as const satisfies readonly MarketTreemapUniverse[];
const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;

function compareCodePoints(left: string, right: string) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function instant(value: string, owner: string) {
  const match = utcInstantPattern.exec(value);
  if (!match) {
    throw new Error(`${owner} has an invalid UTC instant.`);
  }

  const parsed = Date.parse(`${match[1]}Z`);

  if (!Number.isFinite(parsed) || new Date(parsed).toISOString() !== `${match[1]}.000Z`) {
    throw new Error(`${owner} has an invalid UTC instant.`);
  }

  const microseconds = (match[2] ?? "").padEnd(6, "0");
  return BigInt(parsed) * 1_000n + BigInt(microseconds || "0");
}

function assertCalls(
  callPage: Awaited<ReturnType<CallsProvider["list"]>>,
  dataMode: DashboardSnapshot["dataMode"],
) {
  if (
    callPage.page.number !== 0 ||
    callPage.page.size !== 3 ||
    callPage.page.sort.field !== "eventTime" ||
    callPage.page.sort.order !== "desc"
  ) {
    throw new Error("Dashboard calls provider did not honor the requested page contract.");
  }
  if (callPage.items.length > 3 || callPage.items.length > callPage.page.totalElements) {
    throw new Error("Dashboard calls provider returned an invalid item count.");
  }

  const callIds = new Set<string>();
  callPage.items.forEach(({ call, source }, index) => {
    if (callIds.has(call.callId)) {
      throw new Error(`Dashboard calls contain duplicate call ${call.callId}.`);
    }
    callIds.add(call.callId);

    if (
      call.dataMode !== dataMode ||
      source.document.dataMode !== dataMode ||
      source.reference.dataMode !== dataMode
    ) {
      throw new Error("Dashboard calls have inconsistent data mode.");
    }

    const eventTime = instant(call.eventTime, `Dashboard call ${call.callId}`);
    const previous = callPage.items[index - 1]?.call;
    if (!previous) return;

    const previousEventTime = instant(previous.eventTime, `Dashboard call ${previous.callId}`);
    if (
      eventTime > previousEventTime ||
      (eventTime === previousEventTime && compareCodePoints(previous.callId, call.callId) > 0)
    ) {
      throw new Error("Dashboard calls are not ordered by event time descending and call ID ascending.");
    }
  });
}

function assertPreview(
  snapshot: MarketTreemapSnapshot,
  expectedUniverse: MarketTreemapUniverse,
  dataMode: DashboardSnapshot["dataMode"],
) {
  if (snapshot.universe !== expectedUniverse) {
    throw new Error(
      `Dashboard map provider returned ${snapshot.universe} for ${expectedUniverse}.`,
    );
  }
  if (snapshot.mode !== "PRICE_CHANGE") {
    throw new Error(`Dashboard map ${snapshot.universe} is not PRICE_CHANGE evidence.`);
  }
  if (snapshot.dataMode !== dataMode || snapshot.cells.some((cell) => cell.dataMode !== dataMode)) {
    throw new Error(`Dashboard map ${snapshot.universe} has inconsistent data mode.`);
  }
  if (
    snapshot.coverage.kind !== "SAMPLE" ||
    snapshot.coverage.completeUniverse !== false ||
    snapshot.coverage.cellCount !== snapshot.cells.length ||
    snapshot.cells.length === 0
  ) {
    throw new Error(`Dashboard map ${snapshot.universe} has invalid populated SAMPLE coverage.`);
  }

  const asOf = instant(snapshot.asOf, `Dashboard map ${snapshot.universe}`);
  const capturedAt = instant(
    snapshot.provenance.capturedAt,
    `Dashboard map ${snapshot.universe} provenance`,
  );
  const generatedAt = instant(snapshot.generatedAt, `Dashboard map ${snapshot.universe}`);
  if (asOf > capturedAt || capturedAt > generatedAt) {
    throw new Error(`Dashboard map ${snapshot.universe} has invalid evidence chronology.`);
  }

  const assetIds = new Set<string>();
  snapshot.cells.forEach((cell) => {
    if (assetIds.has(cell.assetId)) {
      throw new Error(`Dashboard map ${snapshot.universe} has duplicate asset ${cell.assetId}.`);
    }
    assetIds.add(cell.assetId);

    if (cell.provenanceId !== snapshot.provenance.id) {
      throw new Error(`Dashboard map ${snapshot.universe} has inconsistent cell provenance.`);
    }
    if (instant(cell.timestamp, `Dashboard map cell ${cell.assetId}`) > asOf) {
      throw new Error(`Dashboard map ${snapshot.universe} contains evidence after its as-of time.`);
    }
  });
}

export class FixtureMarketProvider implements MarketProvider {
  constructor(
    private readonly calls: CallsProvider,
    private readonly treemaps: MarketTreemapProvider,
  ) {}

  async dashboard(): Promise<DashboardSnapshot> {
    const [callPage, callMetadata, sp500, nasdaq100] = await Promise.all([
      this.calls.list({ page: 0, size: 3, sort: "eventTime", order: "desc" }),
      this.calls.metadata(),
      this.treemaps.findByUniverse(previewUniverses[0]),
      this.treemaps.findByUniverse(previewUniverses[1]),
    ]);

    if (callMetadata.dataMode !== "DEMO") {
      throw new Error("Dashboard fixture composition requires DEMO data mode.");
    }

    assertCalls(callPage, callMetadata.dataMode);

    assertPreview(sp500, previewUniverses[0], callMetadata.dataMode);
    assertPreview(nasdaq100, previewUniverses[1], callMetadata.dataMode);

    if (sp500.provenance.id === nasdaq100.provenance.id) {
      throw new Error("Dashboard map previews must retain distinct provenance.");
    }

    return {
      dataMode: callMetadata.dataMode,
      latestCalls: {
        items: callPage.items,
        asOf: callMetadata.asOf,
        dataMode: callMetadata.dataMode,
        source: callMetadata.source,
        disclaimer: callMetadata.disclaimer,
      },
      mapPreviews: [sp500, nasdaq100],
      marketBoard: { status: "NOT_PUBLISHED", missingDisplay: "NA" },
      eventCalendar: { status: "NOT_PUBLISHED", missingDisplay: "NA" },
      ranking: { status: "P3_DEFERRED", missingDisplay: "NA" },
    };
  }
}
