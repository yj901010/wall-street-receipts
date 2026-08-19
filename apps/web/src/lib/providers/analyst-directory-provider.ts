import type { DataMode } from "@/lib/data-mode";

export type AnalystDirectoryProvenance = {
  id: string;
  sourceType: "LOCAL_SPECIFICATION";
  sourcePaths: string[];
  capturedAt: string;
  synthetic: true;
  licenseClass: "INTERNAL_DEMO";
};

export type AnalystDirectoryIdentity = {
  analystId: string;
  canonicalName: string;
  active: boolean;
  dataMode: DataMode;
  effectiveAt: string;
  capturedAt: string;
  provenanceId: string;
};

export type AnalystDirectorySnapshot = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
  dataMode: DataMode;
  generatedAt: string;
  provenance: AnalystDirectoryProvenance;
  analysts: AnalystDirectoryIdentity[];
};

export interface AnalystDirectoryProvider {
  directory(): Promise<AnalystDirectorySnapshot>;
}
