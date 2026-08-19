import type {
  AnalystCallPage,
  AnalystCallView,
  AssetSummary,
  CallsMetadata,
  CallsProvider,
} from "./calls-provider";
import type {
  Sp500HistoryProvider,
  Sp500HistorySnapshot,
} from "./sp500-history-provider";

export const SP500_HISTORY_QUERY = {
  assetId: "asset-spx",
  page: 0,
  size: 25,
  sort: "eventTime",
  order: "desc",
} as const;

const utcInstantPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/;

function fixtureString(value: unknown, owner: string, maximum = 512) {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > maximum ||
    value.trim() !== value
  ) {
    throw new Error(`${owner} is invalid.`);
  }
  return value;
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

function compareCodePoints(left: string, right: string) {
  const leftCharacters = Array.from(left);
  const rightCharacters = Array.from(right);
  const comparedLength = Math.min(leftCharacters.length, rightCharacters.length);

  for (let index = 0; index < comparedLength; index += 1) {
    const leftPoint = leftCharacters[index].codePointAt(0)!;
    const rightPoint = rightCharacters[index].codePointAt(0)!;
    if (leftPoint !== rightPoint) return leftPoint < rightPoint ? -1 : 1;
  }

  return leftCharacters.length < rightCharacters.length
    ? -1
    : leftCharacters.length > rightCharacters.length
      ? 1
      : 0;
}

function sameAsset(candidate: AssetSummary, expected: AssetSummary) {
  return (
    candidate.assetId === expected.assetId &&
    candidate.assetType === expected.assetType &&
    candidate.canonicalName === expected.canonicalName &&
    candidate.ticker === expected.ticker
  );
}

function canonicalAsset(metadata: CallsMetadata) {
  const matches = metadata.facets.assets.filter(
    (candidate) => candidate.assetId === SP500_HISTORY_QUERY.assetId,
  );
  if (matches.length !== 1) {
    throw new Error("S&P 500 history metadata must contain one asset-spx facet.");
  }
  return matches[0];
}

function assertPage(page: AnalystCallPage) {
  const expectedTotalPages = Math.ceil(page.page.totalElements / SP500_HISTORY_QUERY.size);
  const expectedItems = Math.min(SP500_HISTORY_QUERY.size, page.page.totalElements);
  if (
    page.page.number !== SP500_HISTORY_QUERY.page ||
    page.page.size !== SP500_HISTORY_QUERY.size ||
    page.page.sort.field !== SP500_HISTORY_QUERY.sort ||
    page.page.sort.order !== SP500_HISTORY_QUERY.order ||
    !Number.isSafeInteger(page.page.totalElements) ||
    page.page.totalElements < 0 ||
    page.page.totalPages !== expectedTotalPages ||
    page.page.first !== true ||
    page.page.last !== (expectedTotalPages <= 1) ||
    page.items.length !== expectedItems
  ) {
    throw new Error("S&P 500 history provider did not honor the fixed page contract.");
  }
}

function assertView(
  view: AnalystCallView,
  asset: AssetSummary,
  source: string,
  catalogAsOf: bigint,
) {
  const { call, institution, analyst, source: evidence } = view;
  if (
    call.assetId !== asset.assetId ||
    !sameAsset(view.asset, asset) ||
    call.institutionId !== institution.institutionId ||
    call.analystId !== (analyst?.analystId ?? null) ||
    call.sourceReferenceId !== evidence.reference.sourceReferenceId ||
    evidence.reference.sourceDocumentId !== evidence.document.sourceDocumentId
  ) {
    throw new Error(`S&P 500 history call ${call.callId} has inconsistent canonical joins.`);
  }
  if (
    call.dataMode !== "DEMO" ||
    evidence.document.dataMode !== "DEMO" ||
    evidence.reference.dataMode !== "DEMO"
  ) {
    throw new Error(`S&P 500 history call ${call.callId} has inconsistent DEMO mode.`);
  }
  if (
    call.provenanceId !== source ||
    evidence.document.provenanceId !== source ||
    evidence.reference.provenanceId !== source
  ) {
    throw new Error(`S&P 500 history call ${call.callId} has inconsistent provenance.`);
  }

  const eventTime = instant(call.eventTime, `S&P 500 history call ${call.callId}`);
  const processingTime = instant(call.processingTime, `S&P 500 history call ${call.callId}`);
  const capturedAt = instant(call.capturedAt, `S&P 500 history call ${call.callId}`);
  const documentCapturedAt = instant(
    evidence.document.capturedAt,
    `S&P 500 history source document ${evidence.document.sourceDocumentId}`,
  );
  const referenceCapturedAt = instant(
    evidence.reference.capturedAt,
    `S&P 500 history source reference ${evidence.reference.sourceReferenceId}`,
  );
  if (
    eventTime > processingTime ||
    processingTime > capturedAt ||
    documentCapturedAt > capturedAt ||
    referenceCapturedAt > capturedAt ||
    capturedAt > catalogAsOf
  ) {
    throw new Error(`S&P 500 history call ${call.callId} has invalid evidence chronology.`);
  }
}

function assertItems(
  items: readonly AnalystCallView[],
  asset: AssetSummary,
  source: string,
  asOf: string,
) {
  const catalogAsOf = instant(asOf, "S&P 500 call catalog");
  const callIds = new Set<string>();

  items.forEach((view, index) => {
    if (callIds.has(view.call.callId)) {
      throw new Error(`S&P 500 history contains duplicate call ${view.call.callId}.`);
    }
    callIds.add(view.call.callId);
    assertView(view, asset, source, catalogAsOf);

    const previous = items[index - 1]?.call;
    if (!previous) return;
    const previousEventTime = instant(
      previous.eventTime,
      `S&P 500 history call ${previous.callId}`,
    );
    const eventTime = instant(view.call.eventTime, `S&P 500 history call ${view.call.callId}`);
    if (
      eventTime > previousEventTime ||
      (eventTime === previousEventTime &&
        compareCodePoints(previous.callId, view.call.callId) > 0)
    ) {
      throw new Error(
        "S&P 500 history calls are not ordered by event time descending and call ID ascending.",
      );
    }
  });
}

export class FixtureSp500HistoryProvider implements Sp500HistoryProvider {
  constructor(private readonly calls: CallsProvider) {}

  async history(): Promise<Sp500HistorySnapshot> {
    const [metadata, page] = await Promise.all([
      this.calls.metadata(),
      this.calls.list(SP500_HISTORY_QUERY),
    ]);

    if (metadata.dataMode !== "DEMO") {
      throw new Error("S&P 500 history requires exact DEMO call metadata.");
    }
    const asOf = fixtureString(metadata.asOf, "S&P 500 call catalog as-of");
    const source = fixtureString(metadata.source, "S&P 500 call catalog source", 128);
    const disclaimer = fixtureString(metadata.disclaimer, "S&P 500 call catalog disclaimer", 768);
    const asset = canonicalAsset(metadata);

    assertPage(page);
    assertItems(page.items, asset, source, asOf);

    return {
      dataMode: "DEMO",
      asOf,
      source,
      disclaimer,
      asset,
      items: page.items,
      page: page.page,
    };
  }
}
