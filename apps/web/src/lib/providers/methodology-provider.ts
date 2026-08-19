import type { DataMode } from "@/lib/data-mode";

export const METHODOLOGY_STATUSES = ["MODEL_ONLY", "ACTIVE", "RETIRED"] as const;

export type MethodologyStatus = (typeof METHODOLOGY_STATUSES)[number];

export type ScoringMethodology = {
  methodologyId: string;
  methodologyVersion: string;
  schemaVersion: "1.0.0";
  definitionHash: string;
  status: MethodologyStatus;
  effectiveAt: string;
  dataMode: DataMode;
  capturedAt: string;
  provenanceId: string;
};

export type MethodologyCatalog = {
  asOf: string;
  dataMode: DataMode;
  source: string;
  disclaimer: string;
  items: ScoringMethodology[];
};

export interface MethodologyProvider {
  catalog(): Promise<MethodologyCatalog>;
}
