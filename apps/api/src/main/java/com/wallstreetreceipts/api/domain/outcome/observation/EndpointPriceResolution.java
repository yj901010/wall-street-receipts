package com.wallstreetreceipts.api.domain.outcome.observation;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolvedSessionWindow;

/** A selected endpoint close or an explicit point-in-time unavailability reason. */
public sealed interface EndpointPriceResolution
        permits EndpointPriceResolution.Resolved, EndpointPriceResolution.Unavailable {

    enum UnavailableReason {
        CATALOG_NOT_KNOWN_AS_OF,
        CATALOG_EVIDENCE_MISMATCH,
        BINDING_NOT_KNOWN_AS_OF,
        ENDPOINT_NOT_REACHED_AS_OF,
        OBSERVATION_MISSING_AS_OF,
        ASSET_MISMATCH,
        PRIMARY_VENUE_MISMATCH,
        CURRENCY_MISMATCH,
        SOURCE_MISMATCH,
        CATALOG_MISMATCH,
        SESSION_MISMATCH,
        OBSERVED_AT_MISMATCH,
        PRICE_FIELD_MISMATCH,
        ADJUSTMENT_BASIS_MISMATCH,
        CORPORATE_ACTION_CONTINUITY_UNAVAILABLE,
        OBSERVATION_AMBIGUOUS
    }

    record ResolutionContext(
            EndpointPricePolicyVersion policyVersion,
            String policyDefinitionHash,
            com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Resolved
                    horizonResolution,
            CatalogPointInTimeEvidence catalogEvidence,
            EndpointPriceBinding binding,
            Instant evaluationAsOf) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(horizonResolution,
                    "horizonResolution must not be null");
            Objects.requireNonNull(catalogEvidence, "catalogEvidence must not be null");
            Objects.requireNonNull(binding, "binding must not be null");
            PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
            if (policyVersion
                    != EndpointPricePolicyVersion
                            .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the endpoint-price V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            var horizonContext = horizonResolution.window().context();
            if (horizonContext.policyVersion()
                    != SessionCloseHorizonPolicyVersion
                            .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1) {
                throw new IllegalArgumentException(
                        "horizonResolution must use the required strict-close V1 policy");
            }
        }
    }

    /**
     * Locally consistent selected evidence. This constructor does not attest
     * request-candidate membership or cardinality; only EndpointPriceSelector
     * owns those claims.
     */
    record Resolved(
            ResolutionContext context,
            EndpointPriceObservation observation) implements EndpointPriceResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(observation, "observation must not be null");
            validateResolvedEvidence(context, observation);
        }
    }

    record Unavailable(
            ResolutionContext context,
            UnavailableReason reason) implements EndpointPriceResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    private static void validateResolvedEvidence(
            ResolutionContext context,
            EndpointPriceObservation observation) {
        Instant evaluationAsOf = context.evaluationAsOf();
        ResolvedSessionWindow window = context.horizonResolution().window();
        var endpoint = window.endpointSession();
        EndpointPriceBinding binding = context.binding();
        CatalogPointInTimeEvidence catalog = context.catalogEvidence();

        if (catalog.availableAt().isAfter(evaluationAsOf)
                || catalog.capturedAt().isAfter(evaluationAsOf)
                || binding.availableAt().isAfter(evaluationAsOf)
                || binding.capturedAt().isAfter(evaluationAsOf)
                || endpoint.closesAt().isAfter(evaluationAsOf)
                || observation.availableAt().isAfter(evaluationAsOf)
                || observation.capturedAt().isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException(
                    "resolved evidence must be known and mature by evaluationAsOf");
        }
        if (!catalog.calendarId().equals(window.context().calendarId())
                || !catalog.catalogRevision().equals(window.context().catalogRevision())) {
            throw new IllegalArgumentException(
                    "resolved catalog evidence must match the strict-horizon context");
        }
        if (!observation.assetId().equals(binding.assetId())
                || !observation.venueId().equals(binding.primaryVenueId())
                || !observation.currency().equals(binding.currency())
                || !observation.priceSourceId().equals(binding.priceSourceId())
                || !observation.priceSourceRevision().equals(binding.priceSourceRevision())
                || !observation.calendarId().equals(catalog.calendarId())
                || !observation.catalogRevision().equals(catalog.catalogRevision())
                || !observation.sessionId().equals(endpoint.sessionId())
                || !observation.observedAt().equals(endpoint.closesAt())
                || observation.priceField() != EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE
                || observation.adjustmentBasis()
                        != EndpointPriceAdjustmentBasis
                                .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED
                || observation.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS) {
            throw new IllegalArgumentException(
                    "observation must exactly match the endpoint price policy context");
        }
    }
}
