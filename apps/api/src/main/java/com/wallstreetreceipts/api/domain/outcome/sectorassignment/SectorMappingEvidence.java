package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence.EffectiveInterval;

/** One reusable provider-node to WSR economic-activity mapping row. */
public record SectorMappingEvidence(
        String mappingEvidenceId,
        String providerEventId,
        String mappingPolicyVersion,
        String mappingPolicyDefinitionHash,
        String mappingSetId,
        String mappingSetVersion,
        String mappingSetDefinitionHash,
        String taxonomyId,
        String taxonomyVersion,
        String taxonomyDefinitionHash,
        String providerId,
        String providerSchemeId,
        String providerSchemeRevision,
        String providerNodeId,
        String providerNodeLabel,
        ProviderNodeDefinition providerNodeDefinition,
        MappingDisposition mappingDisposition,
        String mappingSourceId,
        String mappingSourceRevision,
        String provenanceId,
        EffectiveInterval effectiveInterval,
        Instant availableAt,
        Instant capturedAt) {

    /** Exact source definition state; absence is explicit rather than null. */
    public sealed interface ProviderNodeDefinition
            permits Recorded, NotPublished {
    }

    /** Exact provider-published definition and its supplied language tag. */
    public record Recorded(
            String value,
            String languageTag) implements ProviderNodeDefinition {

        public Recorded {
            SectorAssetClassificationEvidence.requireProviderEvidenceText(
                    value, "value");
            SectorAssetClassificationEvidence.requireCanonicalText(
                    languageTag, "languageTag");
        }
    }

    /** The provider did not publish a definition for this node. */
    public record NotPublished() implements ProviderNodeDefinition {
    }

    /** Closed canonical mapping disposition. */
    public sealed interface MappingDisposition
            permits Mapped, NotMapped {
    }

    /** The provider node maps to one claimed canonical WSR leaf ID. */
    public record Mapped(
            String canonicalNodeId) implements MappingDisposition {

        public Mapped {
            SectorAssetClassificationEvidence.requireCanonicalText(
                    canonicalNodeId, "canonicalNodeId");
        }
    }

    /** The provider node is affirmatively not mapped for one closed reason. */
    public record NotMapped(
            NotMappedReason reason) implements MappingDisposition {

        public NotMapped {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /** Closed reasons supplied by the ADR-028 mapping policy. */
    public enum NotMappedReason {
        NO_CANONICAL_EQUIVALENT,
        PROVIDER_NODE_TOO_BROAD,
        PROVIDER_DEFINITION_UNAVAILABLE
    }

    public SectorMappingEvidence {
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingEvidenceId, "mappingEvidenceId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                providerEventId, "providerEventId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingPolicyVersion, "mappingPolicyVersion");
        SectorAssetClassificationEvidence.requireSha256(
                mappingPolicyDefinitionHash, "mappingPolicyDefinitionHash");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSetId, "mappingSetId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSetVersion, "mappingSetVersion");
        SectorAssetClassificationEvidence.requireSha256(
                mappingSetDefinitionHash, "mappingSetDefinitionHash");
        SectorAssetClassificationEvidence.requireCanonicalText(
                taxonomyId, "taxonomyId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                taxonomyVersion, "taxonomyVersion");
        SectorAssetClassificationEvidence.requireSha256(
                taxonomyDefinitionHash, "taxonomyDefinitionHash");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerId, "providerId");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerSchemeId, "providerSchemeId");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerSchemeRevision, "providerSchemeRevision");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerNodeId, "providerNodeId");
        SectorAssetClassificationEvidence.requireProviderEvidenceText(
                providerNodeLabel, "providerNodeLabel");
        Objects.requireNonNull(providerNodeDefinition,
                "providerNodeDefinition must not be null");
        Objects.requireNonNull(mappingDisposition,
                "mappingDisposition must not be null");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSourceId, "mappingSourceId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSourceRevision, "mappingSourceRevision");
        SectorAssetClassificationEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        Objects.requireNonNull(effectiveInterval,
                "effectiveInterval must not be null");
        SectorAssetClassificationEvidence.requireEvidenceTimeline(
                availableAt, capturedAt);
    }
}
