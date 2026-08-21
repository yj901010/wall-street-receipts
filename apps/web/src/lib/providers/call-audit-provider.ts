import type { DataMode } from "@/lib/data-mode";
import type {
  AnalystCallDetail,
  CallContext,
  CallDirection,
} from "./calls-provider";

export const CALL_REVISION_TYPES = ["CORRECTION", "CANCELLATION"] as const;

export type CallRevisionType = (typeof CALL_REVISION_TYPES)[number];

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

export type CallAuditSnapshot = {
  detail: AnalystCallDetail;
  context: CallContext;
  revisions: readonly CallRevision[];
};

export interface CallAuditProvider {
  findById(callId: string): Promise<CallAuditSnapshot | null>;
}
