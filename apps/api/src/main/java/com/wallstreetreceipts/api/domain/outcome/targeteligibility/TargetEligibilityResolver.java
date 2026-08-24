package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Absent;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.EligibilityEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.PendingReason;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.UnavailableReason;

/** Applies the closed PIT target-hit input-readiness policy. */
public final class TargetEligibilityResolver {

    private TargetEligibilityResolver() {
    }

    public static TargetEligibilityResolution resolve(
            TargetEligibilityRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.horizonResolution(),
                request.evaluationAsOf());

        BasisForecastTermsEvidence terms = visibleTerms(request);
        if (terms == null) {
            return unavailable(context, UnavailableReason
                    .BASIS_TERMS_NOT_KNOWN_AS_OF);
        }
        if (!terms.basis().equals(TargetEligibilityRequest
                .horizonContext(request.horizonResolution()).basis())) {
            return unavailable(context, terms, null, null, null,
                    UnavailableReason.HORIZON_BASIS_MISMATCH, null);
        }
        if (request.sideRouting() == null) {
            return unavailable(context, terms, null, null, null,
                    UnavailableReason.ROUTE_MISSING, null);
        }
        if (!routeMatches(terms, request.sideRouting())) {
            return unavailable(context, terms, request.sideRouting(), null, null,
                    UnavailableReason.ROUTE_DIRECTION_MISMATCH, null);
        }

        boolean absent = terms.targetDisposition() instanceof Absent;
        boolean nonDirectional = request.sideRouting()
                instanceof NonDirectionalRoute;
        var visibleTarget = visibleTarget(request);
        if (absent && visibleTarget != null) {
            return unavailable(context, terms, request.sideRouting(), visibleTarget, null,
                    UnavailableReason.TARGET_STATE_CONFLICT, null);
        }
        if (absent || nonDirectional) {
            NotApplicableReason reason = absent && nonDirectional
                    ? NotApplicableReason.TARGET_ABSENT_AND_NON_DIRECTIONAL
                    : absent ? NotApplicableReason.TARGET_ABSENT
                    : NotApplicableReason.NON_DIRECTIONAL;
            return new TargetEligibilityResolution.NotApplicable(
                    context,
                    new EligibilityEvidence(terms, request.sideRouting(), null, null),
                    reason);
        }
        Present present = (Present) terms.targetDisposition();
        if (present.targetDate() != null) {
            return unavailable(context, terms, request.sideRouting(), null, null,
                    UnavailableReason.TARGET_DATE_SEMANTICS_UNSUPPORTED, null);
        }

        var target = visibleTarget;
        if (target == null) {
            return unavailable(context, terms, request.sideRouting(), null, null,
                    UnavailableReason.TARGET_EVIDENCE_NOT_KNOWN_AS_OF, null);
        }
        if (!target.basis().equals(terms.basis())) {
            return unavailable(context, terms, request.sideRouting(), target, null,
                    UnavailableReason.TARGET_EVIDENCE_BASIS_MISMATCH, null);
        }
        if (!target.assetId().equals(terms.assetId())) {
            return unavailable(context, terms, request.sideRouting(), target, null,
                    UnavailableReason.TARGET_ASSET_MISMATCH, null);
        }
        if (!target.currency().equals(present.sourceTargetCurrency())) {
            return unavailable(context, terms, request.sideRouting(), target, null,
                    UnavailableReason.TARGET_CURRENCY_MISMATCH, null);
        }

        var catalog = visibleCatalog(request);
        if (catalog == null) {
            return unavailable(context, terms, request.sideRouting(), target, null,
                    UnavailableReason.CATALOG_NOT_KNOWN_AS_OF, null);
        }
        var horizonContext = TargetEligibilityRequest.horizonContext(
                request.horizonResolution());
        if (!catalog.calendarId().equals(horizonContext.calendarId())
                || !catalog.catalogRevision().equals(
                        horizonContext.catalogRevision())) {
            return unavailable(context, terms, request.sideRouting(), target, catalog,
                    UnavailableReason.CATALOG_EVIDENCE_MISMATCH, null);
        }

        if (request.horizonResolution()
                instanceof SessionCloseHorizonResolution.Incomplete incomplete) {
            UnavailableReason reason = switch (incomplete.reason()) {
                case FIRST_ELIGIBLE_SESSION_MISSING ->
                        UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING;
                case HORIZON_ENDPOINT_SESSION_MISSING ->
                        UnavailableReason.HORIZON_ENDPOINT_SESSION_MISSING;
            };
            return unavailable(context, terms, request.sideRouting(), target, catalog,
                    reason, incomplete.reason());
        }

        var evidence = new EligibilityEvidence(
                terms, request.sideRouting(), target, catalog);
        var resolved = (SessionCloseHorizonResolution.Resolved)
                request.horizonResolution();
        if (resolved.window().endpointSession().closesAt()
                .isAfter(request.evaluationAsOf())) {
            return new TargetEligibilityResolution.Pending(
                    context, evidence, PendingReason.HORIZON_NOT_REACHED_AS_OF);
        }
        return new TargetEligibilityResolution.ReadyForWindowEvidence(
                context, evidence);
    }

    private static BasisForecastTermsEvidence visibleTerms(
            TargetEligibilityRequest request) {
        var terms = request.termsEvidence();
        return terms != null
                && !terms.availableAt().isAfter(request.evaluationAsOf())
                && !terms.capturedAt().isAfter(request.evaluationAsOf())
                        ? terms : null;
    }

    private static com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence
            visibleTarget(TargetEligibilityRequest request) {
        var target = request.targetEvidence();
        return target != null
                && !target.availableAt().isAfter(request.evaluationAsOf())
                && !target.capturedAt().isAfter(request.evaluationAsOf())
                        ? target : null;
    }

    private static com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence
            visibleCatalog(TargetEligibilityRequest request) {
        var catalog = request.catalogEvidence();
        return catalog != null
                && !catalog.availableAt().isAfter(request.evaluationAsOf())
                && !catalog.capturedAt().isAfter(request.evaluationAsOf())
                        ? catalog : null;
    }

    private static boolean routeMatches(
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result routing) {
        var context = switch (routing) {
            case DirectionalRoute directional -> directional.source().context();
            case NonDirectionalRoute nonDirectional ->
                    nonDirectional.source().context();
        };
        CallDirection direction = context.direction();
        return context.policyVersion()
                == com.wallstreetreceipts.api.domain.outcome.direction
                        .CallDirectionPolarityPolicyVersion
                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1
                && "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50"
                        .equals(context.policyDefinitionHash())
                && direction == terms.direction();
    }

    private static TargetEligibilityResolution.Unavailable unavailable(
            ResolutionContext context,
            UnavailableReason reason) {
        return unavailable(context, null, null, null, null, reason, null);
    }

    private static TargetEligibilityResolution.Unavailable unavailable(
            ResolutionContext context,
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result routing,
            com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence target,
            com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence catalog,
            UnavailableReason reason,
            SessionCloseHorizonResolution.IncompleteReason horizonReason) {
        return new TargetEligibilityResolution.Unavailable(
                context,
                new EligibilityEvidence(terms, routing, target, catalog),
                reason,
                horizonReason);
    }
}
