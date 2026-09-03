import type { DataMode } from "@/lib/data-mode";
import type {
  AnalystCallDetail,
  CallContext,
  CallDirection,
} from "./calls-provider";

export const CALL_REVISION_TYPES = ["CORRECTION", "CANCELLATION"] as const;
export const CALL_OUTCOME_HORIZONS = ["D1", "W1", "M1", "M3", "M6", "Y1"] as const;
export const CALL_OUTCOME_EVALUATION_STATUSES = ["PENDING", "INCOMPLETE"] as const;
export const CALL_OUTCOME_REASON_CODES = ["HORIZON_NOT_REACHED", "HORIZON_DATA_MISSING"] as const;

export type CallRevisionType = (typeof CALL_REVISION_TYPES)[number];
export type CallOutcomeHorizon = (typeof CALL_OUTCOME_HORIZONS)[number];
export type CallOutcomeEvaluationStatus = (typeof CALL_OUTCOME_EVALUATION_STATUSES)[number];
export type CallOutcomeReasonCode = (typeof CALL_OUTCOME_REASON_CODES)[number];

export type CorrectedCallTerms = {
  direction: CallDirection;
  originalRating: string | null;
  previousTarget: number | null;
  target: number | null;
  currency: string | null;
  targetDate: string | null;
};

export type CallRevision = {
  revisionId: string;
  schemaVersion: "1.0.0";
  callId: string;
  supersedesRevisionId: string | null;
  sequenceNumber: number;
  provider: string;
  providerEventId: string;
  revisionType: CallRevisionType;
  eventTime: string;
  processingTime: string;
  correctedTerms: CorrectedCallTerms | null;
  reason: string;
  sourceReferenceId: string;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

/**
 * P2 publishes the immutable audit record only. Calculated and excluded
 * projections remain deliberately outside this web boundary until P3.
 */
export type CallOutcome = {
  outcomeId: string;
  schemaVersion: "1.0.0";
  callId: string;
  horizon: CallOutcomeHorizon;
  basisRevisionId: string | null;
  cancellationRevisionId: null;
  snapshotId: string | null;
  methodologyId: string;
  methodologyVersion: string;
  methodologyDefinitionHash: string;
  inputFingerprint: string;
  sequenceNumber: number;
  supersedesOutcomeId: string | null;
  evaluationStatus: CallOutcomeEvaluationStatus;
  reasonCode: CallOutcomeReasonCode;
  eventTime: string;
  processingTime: string;
  assetReturn: null;
  benchmarkReturn: null;
  sectorReturn: null;
  alpha: null;
  sectorAlpha: null;
  mfe: null;
  mae: null;
  targetHit: null;
  directionalWin: null;
  targetError: null;
  dataComplete: false;
  dataMode: "DEMO";
  capturedAt: string;
  provenanceId: string;
};

export type CallAuditSnapshot = {
  detail: AnalystCallDetail;
  context: CallContext;
  revisions: readonly CallRevision[];
  outcomes: readonly CallOutcome[];
};

export interface CallAuditProvider {
  findById(callId: string): Promise<CallAuditSnapshot | null>;
}
