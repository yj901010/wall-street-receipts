package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;

/** Target-hit input readiness without calculator or cancellation claims. */
public sealed interface TargetEligibilityResolution
        permits TargetEligibilityResolution.ReadyForWindowEvidence,
        TargetEligibilityResolution.Pending,
        TargetEligibilityResolution.NotApplicable,
        TargetEligibilityResolution.Unavailable {

    enum PendingReason {
        HORIZON_NOT_REACHED_AS_OF
    }

    enum NotApplicableReason {
        TARGET_ABSENT,
        NON_DIRECTIONAL,
        TARGET_ABSENT_AND_NON_DIRECTIONAL
    }

    enum UnavailableReason {
        BASIS_TERMS_NOT_KNOWN_AS_OF,
        HORIZON_BASIS_MISMATCH,
        ROUTE_MISSING,
        ROUTE_DIRECTION_MISMATCH,
        TARGET_STATE_CONFLICT,
        TARGET_DATE_SEMANTICS_UNSUPPORTED,
        TARGET_EVIDENCE_NOT_KNOWN_AS_OF,
        TARGET_EVIDENCE_BASIS_MISMATCH,
        TARGET_ASSET_MISMATCH,
        TARGET_CURRENCY_MISMATCH,
        CATALOG_NOT_KNOWN_AS_OF,
        CATALOG_EVIDENCE_MISMATCH,
        FIRST_ELIGIBLE_SESSION_MISSING,
        HORIZON_ENDPOINT_SESSION_MISSING
    }

    /** Stable policy, schedule, and evaluation instant echoed by every result. */
    record ResolutionContext(
            TargetEligibilityPolicyVersion policyVersion,
            String policyDefinitionHash,
            SessionCloseHorizonResolution horizonResolution,
            Instant evaluationAsOf) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(horizonResolution,
                    "horizonResolution must not be null");
            PersistentInstant.requireMicrosecondPrecision(
                    evaluationAsOf, "evaluationAsOf");
            if (policyVersion
                    != TargetEligibilityPolicyVersion
                            .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the target-eligibility V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new TargetEligibilityRequest(
                    policyVersion, horizonResolution, null, null, null, null,
                    evaluationAsOf);
        }
    }

    /** Only point-in-time-visible evidence is retained in this result. */
    record EligibilityEvidence(
            BasisForecastTermsEvidence termsEvidence,
            CalculatorSideRouting.Result sideRouting,
            TargetPriceEvidence targetEvidence,
            CatalogPointInTimeEvidence catalogEvidence) {
    }

    /** All policy inputs are ready for a later full-window evidence selector. */
    record ReadyForWindowEvidence(
            ResolutionContext context,
            EligibilityEvidence evidence) implements TargetEligibilityResolution {

        public ReadyForWindowEvidence {
            Objects.requireNonNull(context, "context must not be null");
            requireCompleteDirectionalEvidence(context, evidence);
            if (!(context.horizonResolution()
                    instanceof SessionCloseHorizonResolution.Resolved resolved)
                    || resolved.window().endpointSession().closesAt()
                            .isAfter(context.evaluationAsOf())) {
                throw new IllegalArgumentException(
                        "ready evidence requires a mature resolved horizon");
            }
        }
    }

    /** Complete target inputs whose exact endpoint close is still in the future. */
    record Pending(
            ResolutionContext context,
            EligibilityEvidence evidence,
            PendingReason reason) implements TargetEligibilityResolution {

        public Pending {
            Objects.requireNonNull(context, "context must not be null");
            requireCompleteDirectionalEvidence(context, evidence);
            Objects.requireNonNull(reason, "reason must not be null");
            if (!(context.horizonResolution()
                    instanceof SessionCloseHorizonResolution.Resolved resolved)
                    || !resolved.window().endpointSession().closesAt()
                            .isAfter(context.evaluationAsOf())) {
                throw new IllegalArgumentException(
                        "pending evidence requires an unreached resolved horizon");
            }
        }
    }

    /** A known absent target or non-directional source is permanently N/A. */
    record NotApplicable(
            ResolutionContext context,
            EligibilityEvidence evidence,
            NotApplicableReason reason) implements TargetEligibilityResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(evidence.termsEvidence(),
                    "termsEvidence must not be null");
            Objects.requireNonNull(evidence.sideRouting(),
                    "sideRouting must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (evidence.targetEvidence() != null || evidence.catalogEvidence() != null) {
                throw new IllegalArgumentException(
                        "not-applicable evidence must not retain unused target or catalog evidence");
            }
            requireVisibleTerms(context, evidence.termsEvidence());
            requireMatchingBasis(context, evidence.termsEvidence());
            requireMatchingRoute(evidence.termsEvidence(), evidence.sideRouting());
            boolean absent = evidence.termsEvidence().targetDisposition()
                    instanceof BasisForecastTermsEvidence.TargetDisposition.Absent;
            boolean nonDirectional = evidence.sideRouting()
                    instanceof NonDirectionalRoute;
            NotApplicableReason expected = absent && nonDirectional
                    ? NotApplicableReason.TARGET_ABSENT_AND_NON_DIRECTIONAL
                    : absent ? NotApplicableReason.TARGET_ABSENT
                    : nonDirectional ? NotApplicableReason.NON_DIRECTIONAL : null;
            if (reason != expected) {
                throw new IllegalArgumentException(
                        "reason must match target presence and routing");
            }
        }
    }

    /** Explicit missing, mismatched, unsupported, or incomplete evidence. */
    record Unavailable(
            ResolutionContext context,
            EligibilityEvidence evidence,
            UnavailableReason reason,
            SessionCloseHorizonResolution.IncompleteReason horizonReason)
            implements TargetEligibilityResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            boolean horizonRelated = reason == UnavailableReason
                    .FIRST_ELIGIBLE_SESSION_MISSING
                    || reason == UnavailableReason
                            .HORIZON_ENDPOINT_SESSION_MISSING;
            if (horizonRelated != (horizonReason != null)) {
                throw new IllegalArgumentException(
                        "horizonReason is required exactly for horizon coverage unavailability");
            }
            if (horizonRelated) {
                if (!(context.horizonResolution()
                        instanceof SessionCloseHorizonResolution.Incomplete incomplete)
                        || incomplete.reason() != horizonReason
                        || !maps(reason, horizonReason)) {
                    throw new IllegalArgumentException(
                            "horizonReason must match the horizon resolution and reason");
                }
            }
            validateUnavailableEvidence(context, evidence, reason);
        }
    }

    private static void requireCompleteDirectionalEvidence(
            ResolutionContext context,
            EligibilityEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(evidence.termsEvidence(),
                "termsEvidence must not be null");
        if (!(evidence.sideRouting() instanceof DirectionalRoute)) {
            throw new IllegalArgumentException(
                    "complete target evidence requires directional routing");
        }
        Objects.requireNonNull(evidence.targetEvidence(),
                "targetEvidence must not be null");
        Objects.requireNonNull(evidence.catalogEvidence(),
                "catalogEvidence must not be null");
        if (!(evidence.termsEvidence().targetDisposition()
                instanceof Present present) || present.targetDate() != null) {
            throw new IllegalArgumentException(
                    "complete target evidence requires a present undated target");
        }
        requireVisibleTerms(context, evidence.termsEvidence());
        requireMatchingBasis(context, evidence.termsEvidence());
        requireMatchingRoute(evidence.termsEvidence(), evidence.sideRouting());
        requireVisibleTarget(context, evidence.targetEvidence());
        if (!evidence.targetEvidence().basis().equals(
                evidence.termsEvidence().basis())
                || !evidence.targetEvidence().currency().equals(
                        present.sourceTargetCurrency())
                || !evidence.targetEvidence().assetId().equals(
                        evidence.termsEvidence().assetId())) {
            throw new IllegalArgumentException(
                    "target evidence must match basis, asset, and source target currency");
        }
        requireVisibleCatalog(context, evidence.catalogEvidence());
        requireMatchingCatalog(context, evidence.catalogEvidence());
    }

    private static void validateUnavailableEvidence(
            ResolutionContext context,
            EligibilityEvidence evidence,
            UnavailableReason reason) {
        if (reason == UnavailableReason.BASIS_TERMS_NOT_KNOWN_AS_OF) {
            if (evidence.termsEvidence() != null || evidence.sideRouting() != null
                    || evidence.targetEvidence() != null
                    || evidence.catalogEvidence() != null) {
                throw new IllegalArgumentException(
                        "unknown terms must not leak supplied evidence");
            }
            return;
        }
        BasisForecastTermsEvidence terms = Objects.requireNonNull(
                evidence.termsEvidence(),
                "known terms are required for this reason");
        requireVisibleTerms(context, terms);
        switch (reason) {
            case BASIS_TERMS_NOT_KNOWN_AS_OF -> throw new AssertionError();
            case HORIZON_BASIS_MISMATCH -> {
                requireClearedAfterTerms(evidence);
                if (terms.basis().equals(TargetEligibilityRequest
                        .horizonContext(context.horizonResolution()).basis())) {
                    throw new IllegalArgumentException(
                            "horizon-basis mismatch reason requires mismatched evidence");
                }
            }
            case ROUTE_MISSING -> {
                requireMatchingBasis(context, terms);
                requireClearedAfterTerms(evidence);
            }
            case ROUTE_DIRECTION_MISMATCH -> {
                requireMatchingBasis(context, terms);
                Objects.requireNonNull(evidence.sideRouting(),
                        "mismatched routing must be preserved");
                if (routeMatches(terms, evidence.sideRouting())) {
                    throw new IllegalArgumentException(
                            "route mismatch reason requires mismatched routing");
                }
                requireNull(evidence.targetEvidence(), "targetEvidence");
                requireNull(evidence.catalogEvidence(), "catalogEvidence");
            }
            case TARGET_STATE_CONFLICT -> {
                requireMatchingBasis(context, terms);
                Objects.requireNonNull(evidence.sideRouting(),
                        "matching routing must be preserved");
                requireMatchingRoute(terms, evidence.sideRouting());
                if (!(terms.targetDisposition()
                        instanceof BasisForecastTermsEvidence.TargetDisposition.Absent)) {
                    throw new IllegalArgumentException(
                            "target-state conflict requires absent source terms");
                }
                TargetPriceEvidence conflictingTarget = Objects.requireNonNull(
                        evidence.targetEvidence(),
                        "visible conflicting target evidence must be preserved");
                requireVisibleTarget(context, conflictingTarget);
                requireNull(evidence.catalogEvidence(), "catalogEvidence");
            }
            case TARGET_DATE_SEMANTICS_UNSUPPORTED -> {
                requireDirectionalPresentUndatedPrefix(context, evidence, false);
                if (((Present) terms.targetDisposition()).targetDate() == null) {
                    throw new IllegalArgumentException(
                            "unsupported target-date reason requires a target date");
                }
                requireNull(evidence.targetEvidence(), "targetEvidence");
                requireNull(evidence.catalogEvidence(), "catalogEvidence");
            }
            case TARGET_EVIDENCE_NOT_KNOWN_AS_OF -> {
                requireDirectionalPresentUndatedPrefix(context, evidence, true);
                requireNull(evidence.targetEvidence(), "targetEvidence");
                requireNull(evidence.catalogEvidence(), "catalogEvidence");
            }
            case TARGET_EVIDENCE_BASIS_MISMATCH,
                    TARGET_ASSET_MISMATCH,
                    TARGET_CURRENCY_MISMATCH -> {
                requireDirectionalPresentUndatedPrefix(context, evidence, true);
                TargetPriceEvidence target = Objects.requireNonNull(
                        evidence.targetEvidence(),
                        "known target evidence must be preserved");
                requireVisibleTarget(context, target);
                boolean basisMatches = target.basis().equals(terms.basis());
                boolean assetMatches = target.assetId().equals(terms.assetId());
                boolean currencyMatches = target.currency().equals(
                        ((Present) terms.targetDisposition())
                                .sourceTargetCurrency());
                boolean exactReason = switch (reason) {
                    case TARGET_EVIDENCE_BASIS_MISMATCH -> !basisMatches;
                    case TARGET_ASSET_MISMATCH -> basisMatches && !assetMatches;
                    case TARGET_CURRENCY_MISMATCH ->
                            basisMatches && assetMatches && !currencyMatches;
                    default -> false;
                };
                if (!exactReason) {
                    throw new IllegalArgumentException(
                            "target mismatch reason must follow exact precedence");
                }
                requireNull(evidence.catalogEvidence(), "catalogEvidence");
            }
            case CATALOG_NOT_KNOWN_AS_OF,
                    CATALOG_EVIDENCE_MISMATCH,
                    FIRST_ELIGIBLE_SESSION_MISSING,
                    HORIZON_ENDPOINT_SESSION_MISSING -> {
                requireCompleteBeforeCatalog(context, evidence);
                if (reason == UnavailableReason.CATALOG_NOT_KNOWN_AS_OF) {
                    requireNull(evidence.catalogEvidence(), "catalogEvidence");
                } else {
                    CatalogPointInTimeEvidence catalog = Objects.requireNonNull(
                            evidence.catalogEvidence(),
                            "known catalog evidence must be preserved");
                    requireVisibleCatalog(context, catalog);
                    boolean matches = catalogMatches(context, catalog);
                    if (reason == UnavailableReason.CATALOG_EVIDENCE_MISMATCH
                            ? matches : !matches) {
                        throw new IllegalArgumentException(
                                "catalog evidence must match the exact reason");
                    }
                }
            }
        }
    }

    private static void requireClearedAfterTerms(EligibilityEvidence evidence) {
        requireNull(evidence.sideRouting(), "sideRouting");
        requireNull(evidence.targetEvidence(), "targetEvidence");
        requireNull(evidence.catalogEvidence(), "catalogEvidence");
    }

    private static void requireDirectionalPresentUndatedPrefix(
            ResolutionContext context,
            EligibilityEvidence evidence,
            boolean requireUndated) {
        BasisForecastTermsEvidence terms = evidence.termsEvidence();
        requireMatchingBasis(context, terms);
        if (!(evidence.sideRouting() instanceof DirectionalRoute)) {
            throw new IllegalArgumentException(
                    "this reason requires directional routing");
        }
        requireMatchingRoute(terms, evidence.sideRouting());
        if (!(terms.targetDisposition() instanceof Present present)
                || requireUndated && present.targetDate() != null) {
            throw new IllegalArgumentException(
                    "this reason requires a present source target");
        }
    }

    private static void requireCompleteBeforeCatalog(
            ResolutionContext context,
            EligibilityEvidence evidence) {
        requireDirectionalPresentUndatedPrefix(context, evidence, true);
        TargetPriceEvidence target = Objects.requireNonNull(
                evidence.targetEvidence(),
                "known target evidence must be preserved");
        requireVisibleTarget(context, target);
        if (!target.basis().equals(evidence.termsEvidence().basis())
                || !target.assetId().equals(evidence.termsEvidence().assetId())
                || !target.currency().equals(
                        ((Present) evidence.termsEvidence().targetDisposition())
                                .sourceTargetCurrency())) {
            throw new IllegalArgumentException(
                    "target evidence must pass all identity gates");
        }
    }

    private static void requireNull(Object value, String field) {
        if (value != null) {
            throw new IllegalArgumentException(
                    field + " must be cleared by reason precedence");
        }
    }

    private static void requireVisibleTerms(
            ResolutionContext context,
            BasisForecastTermsEvidence terms) {
        if (terms.availableAt().isAfter(context.evaluationAsOf())
                || terms.capturedAt().isAfter(context.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "terms evidence must be known by evaluationAsOf");
        }
    }

    private static void requireMatchingBasis(
            ResolutionContext context,
            BasisForecastTermsEvidence terms) {
        if (!terms.basis().equals(TargetEligibilityRequest
                .horizonContext(context.horizonResolution()).basis())) {
            throw new IllegalArgumentException(
                    "terms basis must match the horizon basis");
        }
    }

    private static void requireMatchingRoute(
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result routing) {
        CallDirection direction = switch (routing) {
            case DirectionalRoute directional ->
                    directional.source().context().direction();
            case NonDirectionalRoute nonDirectional ->
                    nonDirectional.source().context().direction();
        };
        var directionContext = switch (routing) {
            case DirectionalRoute directional -> directional.source().context();
            case NonDirectionalRoute nonDirectional -> nonDirectional.source().context();
        };
        if (directionContext.policyVersion()
                != CallDirectionPolarityPolicyVersion
                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1
                || !"d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50"
                        .equals(
                        directionContext.policyDefinitionHash())
                || direction != terms.direction()) {
            throw new IllegalArgumentException(
                    "routing must use the required policy and source direction");
        }
    }

    private static void requireVisibleTarget(
            ResolutionContext context,
            TargetPriceEvidence target) {
        if (target.availableAt().isAfter(context.evaluationAsOf())
                || target.capturedAt().isAfter(context.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "target evidence must be known by evaluationAsOf");
        }
    }

    private static void requireVisibleCatalog(
            ResolutionContext context,
            CatalogPointInTimeEvidence catalog) {
        if (catalog.availableAt().isAfter(context.evaluationAsOf())
                || catalog.capturedAt().isAfter(context.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "catalog evidence must be known by evaluationAsOf");
        }
    }

    private static void requireMatchingCatalog(
            ResolutionContext context,
            CatalogPointInTimeEvidence catalog) {
        if (!catalogMatches(context, catalog)) {
            throw new IllegalArgumentException(
                    "catalog evidence must match the horizon catalog identity");
        }
    }

    private static boolean catalogMatches(
            ResolutionContext context,
            CatalogPointInTimeEvidence catalog) {
        var horizon = TargetEligibilityRequest.horizonContext(
                context.horizonResolution());
        return catalog.calendarId().equals(horizon.calendarId())
                && catalog.catalogRevision().equals(horizon.catalogRevision());
    }

    private static boolean routeMatches(
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result routing) {
        CallDirection direction = switch (routing) {
            case DirectionalRoute directional ->
                    directional.source().context().direction();
            case NonDirectionalRoute nonDirectional ->
                    nonDirectional.source().context().direction();
        };
        var directionContext = switch (routing) {
            case DirectionalRoute directional -> directional.source().context();
            case NonDirectionalRoute nonDirectional -> nonDirectional.source().context();
        };
        return directionContext.policyVersion()
                == CallDirectionPolarityPolicyVersion
                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1
                && "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50"
                        .equals(directionContext.policyDefinitionHash())
                && direction == terms.direction();
    }

    private static boolean maps(
            UnavailableReason reason,
            SessionCloseHorizonResolution.IncompleteReason horizonReason) {
        return switch (horizonReason) {
            case FIRST_ELIGIBLE_SESSION_MISSING ->
                    reason == UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING;
            case HORIZON_ENDPOINT_SESSION_MISSING ->
                    reason == UnavailableReason.HORIZON_ENDPOINT_SESSION_MISSING;
        };
    }
}
