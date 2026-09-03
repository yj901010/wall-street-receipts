import {
  isSecManifestAuditInstant,
  isSecManifestAuditManifestId,
} from "@/lib/providers/sec-manifest-audit-query";

type RawValues = Record<string, string | string[] | undefined>;
export type LocatorInputError = "required" | "duplicate" | "notRetained" | "format";
export type LocatorFieldFeedback = { value: string; error: LocatorInputError | null };
export type LocatorFeedback = {
  manifestId: LocatorFieldFeedback;
  evaluationAsOf: LocatorFieldFeedback;
};

export const LOCATOR_INPUT_LIMIT = 64;

function field(
  value: RawValues[string],
  validate: (value: string) => boolean,
): LocatorFieldFeedback {
  if (value === undefined || value === "") return { value: "", error: "required" };
  if (Array.isArray(value)) return { value: "", error: "duplicate" };
  // Text inputs strip line breaks. Retain only bounded printable ASCII verbatim;
  // never select, truncate, trim, case-fold, or normalize an invalid lookup key.
  if (value.length > LOCATOR_INPUT_LIMIT || Array.from(value).some((character) => {
    const code = character.charCodeAt(0);
    return code < 32 || code > 126;
  })) {
    return { value: "", error: "notRetained" };
  }
  return { value, error: validate(value) ? null : "format" };
}

// Presentation only: this does not repair route state or authorize a provider read.
// null means the untouched locator, not an invalid submission with missing fields.
export function locatorFeedback(values: RawValues | null): LocatorFeedback {
  if (values === null) {
    return {
      manifestId: { value: "", error: null },
      evaluationAsOf: { value: "", error: null },
    };
  }
  return {
    manifestId: field(values.manifestId, isSecManifestAuditManifestId),
    evaluationAsOf: field(values.evaluationAsOf, isSecManifestAuditInstant),
  };
}
