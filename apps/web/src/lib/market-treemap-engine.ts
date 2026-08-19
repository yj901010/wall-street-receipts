import type {
  MarketTreemapCell,
  MarketTreemapMetric,
  MarketTreemapSnapshot,
} from "@/lib/providers/market-treemap-provider";
import {
  layoutTreemap,
  type TreemapLayoutNode,
  type TreemapNode,
  type TreemapRect,
} from "./treemap-layout";

export const MARKET_TREEMAP_CANVAS: TreemapRect = {
  x: 0,
  y: 0,
  width: 1200,
  height: 660,
};

const palette = {
  negative: "#8a3438",
  neutral: "#303841",
  positive: "#216b50",
} as const;

export type MarketTreemapMetricTone = "negative" | "neutral" | "positive" | "unavailable";
export type MarketTreemapLabelDensity = "compact" | "full" | "hidden";

export type MarketTreemapCellPresentation = {
  metricDisplay: string;
  metricTone: MarketTreemapMetricTone;
  backgroundColor: string;
  paletteStrength: number;
};

export type MarketTreemapNodeValue =
  | { kind: "sector"; label: string }
  | { kind: "industry"; label: string; sectorLabel: string }
  | {
      kind: "cell";
      label: string;
      sectorLabel: string;
      industryLabel: string;
      cell: MarketTreemapCell;
    };

export function marketTreemapLabelDensity(rect: TreemapRect): MarketTreemapLabelDensity {
  if (rect.width < 90 || rect.height < 72) return "hidden";
  if (rect.width < 170 || rect.height < 110) return "compact";
  return "full";
}

function hexChannels(hex: string): readonly [number, number, number] {
  return [
    Number.parseInt(hex.slice(1, 3), 16),
    Number.parseInt(hex.slice(3, 5), 16),
    Number.parseInt(hex.slice(5, 7), 16),
  ];
}

function interpolateHex(from: string, to: string, amount: number): string {
  const left = hexChannels(from);
  const right = hexChannels(to);
  const channel = (index: number) => Math.round(left[index] + (right[index] - left[index]) * amount);
  return `rgb(${channel(0)}, ${channel(1)}, ${channel(2)})`;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

export function presentMarketTreemapCell(
  cell: MarketTreemapCell,
  metric: MarketTreemapMetric,
): MarketTreemapCellPresentation {
  if (cell.priceChangePercent === null) {
    return {
      metricDisplay: metric.missingDisplay,
      metricTone: "unavailable",
      backgroundColor: palette.neutral,
      paletteStrength: 0,
    };
  }

  const value = Object.is(cell.priceChangePercent, -0) ? 0 : cell.priceChangePercent;
  if (value === 0) {
    return {
      metricDisplay: "0%",
      metricTone: "neutral",
      backgroundColor: palette.neutral,
      paletteStrength: 0,
    };
  }

  const negative = value < 0;
  const scaleEdge = negative ? Math.abs(metric.scaleMinimum) : metric.scaleMaximum;
  const paletteStrength = clamp(Math.abs(value) / scaleEdge, 0, 1);

  return {
    metricDisplay: `${value > 0 ? "+" : ""}${String(value)}%`,
    metricTone: negative ? "negative" : "positive",
    backgroundColor: interpolateHex(
      palette.neutral,
      negative ? palette.negative : palette.positive,
      paletteStrength,
    ),
    paletteStrength,
  };
}

function classificationKey(value: string | null): string {
  return value === null ? "null" : `value:${value}`;
}

export function buildMarketTreemapHierarchy(
  snapshot: MarketTreemapSnapshot,
): readonly TreemapNode<MarketTreemapNodeValue>[] {
  const sectors = new Map<
    string,
    {
      label: string;
      industries: Map<string, { label: string; cells: MarketTreemapCell[] }>;
    }
  >();

  for (const cell of snapshot.cells) {
    const sectorKey = classificationKey(cell.sector);
    const sector = sectors.get(sectorKey) ?? {
      label: cell.sector ?? snapshot.geometry.unclassifiedDisplay,
      industries: new Map(),
    };
    sectors.set(sectorKey, sector);

    const industryKey = classificationKey(cell.industry);
    const industry = sector.industries.get(industryKey) ?? {
      label: cell.industry ?? snapshot.geometry.unclassifiedDisplay,
      cells: [],
    };
    sector.industries.set(industryKey, industry);
    industry.cells.push(cell);
  }

  return [...sectors.values()].map((sector) => ({
    id: `sector:${sector.label}`,
    value: { kind: "sector", label: sector.label },
    children: [...sector.industries.values()].map((industry) => ({
      id: `sector:${sector.label}/industry:${industry.label}`,
      value: { kind: "industry", label: industry.label, sectorLabel: sector.label },
      children: industry.cells.map((cell) => ({
        id: cell.assetId,
        value: {
          kind: "cell",
          label: cell.ticker,
          sectorLabel: sector.label,
          industryLabel: industry.label,
          cell,
        },
        weight: cell.syntheticMarketCapProxy,
      })),
    })),
  }));
}

export function layoutMarketTreemap(
  snapshot: MarketTreemapSnapshot,
  bounds: TreemapRect = MARKET_TREEMAP_CANVAS,
): readonly TreemapLayoutNode<MarketTreemapNodeValue>[] {
  return layoutTreemap(buildMarketTreemapHierarchy(snapshot), bounds);
}

export function marketTreemapPaletteStops(metric: MarketTreemapMetric) {
  return [
    metric.scaleMinimum,
    metric.scaleMinimum / 2,
    0,
    metric.scaleMaximum / 2,
    metric.scaleMaximum,
  ].map((value) => ({
    value,
    ...presentMarketTreemapCell(
      {
        assetId: "palette-stop",
        ticker: "NA",
        sector: null,
        industry: null,
        syntheticMarketCapProxy: 1,
        priceChangePercent: value,
        timestamp: "1970-01-01T00:00:00Z",
        dataMode: "DEMO",
        provenanceId: "palette-stop",
      },
      metric,
    ),
  }));
}
