import type { DataMode } from "@/lib/data-mode";

export type InstitutionDirectoryProvenance = {
  id: string;
  sourceType: "LOCAL_SPECIFICATION";
  sourcePaths: string[];
  capturedAt: string;
  synthetic: true;
  licenseClass: "INTERNAL_DEMO";
};

export type InstitutionDirectoryIdentity = {
  institutionId: string;
  canonicalName: string;
  slug: string;
  country: string;
  active: boolean;
  dataMode: DataMode;
  effectiveAt: string;
  capturedAt: string;
  provenanceId: string;
};

export type InstitutionDirectorySnapshot = {
  schemaVersion: "1.0.0";
  fixtureVersion: "v1";
  dataMode: DataMode;
  generatedAt: string;
  provenance: InstitutionDirectoryProvenance;
  institutions: InstitutionDirectoryIdentity[];
};

export interface InstitutionDirectoryProvider {
  directory(): Promise<InstitutionDirectorySnapshot>;
}
