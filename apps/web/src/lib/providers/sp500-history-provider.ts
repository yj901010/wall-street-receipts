import type {
  AnalystCallView,
  AssetSummary,
  PageMetadata,
} from "./calls-provider";

export type Sp500HistorySnapshot = {
  dataMode: "DEMO";
  asOf: string;
  source: string;
  disclaimer: string;
  asset: AssetSummary;
  items: AnalystCallView[];
  page: PageMetadata;
};

export interface Sp500HistoryProvider {
  history(): Promise<Sp500HistorySnapshot>;
}
