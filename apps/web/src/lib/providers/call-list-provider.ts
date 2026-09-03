import type {
  AnalystCallPage,
  CallsQuery,
} from "./calls-provider";

export const CALL_LIST_METADATA_NOT_EXPOSED_REASON = "LIST_API_HAS_NO_DATASET_METADATA" as const;

export type ReturnedPageEvidence = {
  scope: "RETURNED_PAGE";
  latestCallCapturedAt: string | null;
  callProvenanceIds: readonly string[];
};

export type AvailableCallListDatasetEvidence = {
  availability: "AVAILABLE";
  asOf: string;
  source: string;
  disclaimer: string;
};

export type NotExposedCallListDatasetEvidence = {
  availability: "NOT_EXPOSED";
  reason: typeof CALL_LIST_METADATA_NOT_EXPOSED_REASON;
  asOf: null;
  source: null;
  disclaimer: null;
};

export type CallListDatasetEvidence =
  | AvailableCallListDatasetEvidence
  | NotExposedCallListDatasetEvidence;

export type CallListSnapshot = AnalystCallPage & {
  dataMode: "DEMO";
  returnedPageEvidence: ReturnedPageEvidence;
  datasetEvidence: CallListDatasetEvidence;
};

export interface CallListProvider {
  list(query?: CallsQuery): Promise<CallListSnapshot>;
}
