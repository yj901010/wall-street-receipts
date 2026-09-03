package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Absent;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.NotApplicable;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.Pending;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.PendingReason;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.ReadyForWindowEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

class TargetEligibilityResolverGoldenTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-10T14:00:00Z");
    private static final Instant TERMS_AVAILABLE = Instant.parse("2026-08-10T14:01:00Z");
    private static final Instant ENDPOINT_CLOSE = Instant.parse("2026-08-11T20:00:00Z");
    private static final Instant READY_AS_OF = Instant.parse("2026-08-11T20:01:00Z");
    private static final Instant PENDING_AS_OF = Instant.parse("2026-08-11T19:59:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-1", BASIS_TIME);

    @Test
    void canonicalDefinitionHasFixedBytesLengthAndIndependentHash() throws Exception {
        var policy = policy();
        byte[] bytes = policy.canonicalDefinitionUtf8();
        assertThat(bytes).hasSize(3862);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo("a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465")
                .isEqualTo(policy.definitionHash());
        bytes[0] = 0;
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactClosedEnumOrdersAreStable() {
        assertThat(TargetEligibilityPolicyVersion.values())
                .containsExactly(policy());
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.BASIS_TERMS_NOT_KNOWN_AS_OF,
                UnavailableReason.HORIZON_BASIS_MISMATCH,
                UnavailableReason.ROUTE_MISSING,
                UnavailableReason.ROUTE_DIRECTION_MISMATCH,
                UnavailableReason.TARGET_STATE_CONFLICT,
                UnavailableReason.TARGET_DATE_SEMANTICS_UNSUPPORTED,
                UnavailableReason.TARGET_EVIDENCE_NOT_KNOWN_AS_OF,
                UnavailableReason.TARGET_EVIDENCE_BASIS_MISMATCH,
                UnavailableReason.TARGET_ASSET_MISMATCH,
                UnavailableReason.TARGET_CURRENCY_MISMATCH,
                UnavailableReason.CATALOG_NOT_KNOWN_AS_OF,
                UnavailableReason.CATALOG_EVIDENCE_MISMATCH,
                UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING,
                UnavailableReason.HORIZON_ENDPOINT_SESSION_MISSING);
    }

    @Test
    void exactProductionFileRecordAndSealedSurfacesAreStable() throws Exception {
        Path packagePath = Path.of("src/main/java/com/wallstreetreceipts/api/domain/outcome/targeteligibility");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "BasisForecastTermsEvidence.java",
                            "TargetEligibilityPolicyVersion.java",
                            "TargetEligibilityRequest.java",
                            "TargetEligibilityResolution.java",
                            "TargetEligibilityResolver.java");
        }

        assertRecordComponents(BasisForecastTermsEvidence.class,
                "termsEvidenceId:String", "basis:OutcomeBasis", "assetId:String",
                "direction:CallDirection", "targetDisposition:TargetDisposition",
                "provider:String", "providerEventId:String", "availableAt:Instant",
                "capturedAt:Instant", "provenanceId:String");
        assertThat(BasisForecastTermsEvidence.TargetDisposition.class.isSealed()).isTrue();
        assertThat(permittedSimpleNames(BasisForecastTermsEvidence.TargetDisposition.class))
                .containsExactlyInAnyOrder("Present", "Absent");
        assertRecordComponents(Present.class, "sourceTarget:BigDecimal",
                "sourceTargetCurrency:Currency", "targetDate:LocalDate");
        assertRecordComponents(Absent.class);
        assertRecordComponents(TargetEligibilityRequest.class,
                "policyVersion:TargetEligibilityPolicyVersion",
                "horizonResolution:SessionCloseHorizonResolution",
                "termsEvidence:BasisForecastTermsEvidence",
                "sideRouting:Result", "targetEvidence:TargetPriceEvidence",
                "catalogEvidence:CatalogPointInTimeEvidence", "evaluationAsOf:Instant");

        assertThat(TargetEligibilityResolution.class.isSealed()).isTrue();
        assertThat(permittedSimpleNames(TargetEligibilityResolution.class))
                .containsExactlyInAnyOrder("ReadyForWindowEvidence", "Pending",
                        "NotApplicable", "Unavailable");
        assertRecordComponents(TargetEligibilityResolution.ResolutionContext.class,
                "policyVersion:TargetEligibilityPolicyVersion",
                "policyDefinitionHash:String",
                "horizonResolution:SessionCloseHorizonResolution",
                "evaluationAsOf:Instant");
        assertRecordComponents(TargetEligibilityResolution.EligibilityEvidence.class,
                "termsEvidence:BasisForecastTermsEvidence", "sideRouting:Result",
                "targetEvidence:TargetPriceEvidence",
                "catalogEvidence:CatalogPointInTimeEvidence");
        assertRecordComponents(ReadyForWindowEvidence.class,
                "context:ResolutionContext", "evidence:EligibilityEvidence");
        assertRecordComponents(Pending.class, "context:ResolutionContext",
                "evidence:EligibilityEvidence", "reason:PendingReason");
        assertRecordComponents(NotApplicable.class, "context:ResolutionContext",
                "evidence:EligibilityEvidence", "reason:NotApplicableReason");
        assertRecordComponents(Unavailable.class, "context:ResolutionContext",
                "evidence:EligibilityEvidence", "reason:UnavailableReason",
                "horizonReason:IncompleteReason");
        assertThat(Modifier.isFinal(TargetEligibilityResolver.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(TargetEligibilityResolver.class
                .getDeclaredConstructor().getModifiers())).isTrue();
        assertThat(Arrays.stream(TargetEligibilityResolver.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName() + ":" + method.getParameterCount())
                .toList()).containsExactly("resolve:1");
    }

    @ParameterizedTest
    @EnumSource(CallDirection.class)
    void everyCanonicalDirectionUsesItsExactClosedRoute(CallDirection direction) {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, direction, present(null), TERMS_AVAILABLE),
                route(direction), target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        if (direction == CallDirection.NEUTRAL) {
            assertThat(result).isInstanceOfSatisfying(NotApplicable.class,
                    value -> assertThat(value.reason())
                            .isEqualTo(NotApplicableReason.NON_DIRECTIONAL));
        } else {
            assertThat(result).isInstanceOfSatisfying(ReadyForWindowEvidence.class,
                    value -> assertThat(routeDirection(value.evidence().sideRouting()))
                            .isEqualTo(direction));
        }
    }

    @Test
    void exactEndpointEqualityIsReadyForWindowEvidenceOnly() {
        var request = request(ENDPOINT_CLOSE, terms(ORIGINAL, CallDirection.BULLISH,
                present(null), TERMS_AVAILABLE), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE), catalog(TERMS_AVAILABLE),
                resolved(ORIGINAL));
        var result = TargetEligibilityResolver.resolve(request);
        assertThat(result).isInstanceOf(ReadyForWindowEvidence.class);
        var ready = (ReadyForWindowEvidence) result;
        assertThat(ready.evidence().termsEvidence()).isSameAs(request.termsEvidence());
        assertThat(ready.evidence().targetEvidence()).isSameAs(request.targetEvidence());
        assertThat(ready.evidence().catalogEvidence()).isSameAs(request.catalogEvidence());
    }

    @Test
    void endpointAfterEvaluationIsPending() {
        var result = TargetEligibilityResolver.resolve(completeRequest(PENDING_AS_OF));
        assertThat(result).isEqualTo(new Pending(
                ((Pending) result).context(),
                ((Pending) result).evidence(),
                PendingReason.HORIZON_NOT_REACHED_AS_OF));
    }

    @Test
    void absentTargetIsKnownNotApplicableNotMissing() {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.BULLISH, new Absent(), TERMS_AVAILABLE),
                route(CallDirection.BULLISH), null, null, resolved(ORIGINAL)));
        assertThat(result).isInstanceOfSatisfying(NotApplicable.class, value -> {
            assertThat(value.reason()).isEqualTo(NotApplicableReason.TARGET_ABSENT);
            assertThat(value.evidence().targetEvidence()).isNull();
            assertThat(value.evidence().catalogEvidence()).isNull();
        });
    }

    @Test
    void visibleExactTargetConflictsWithAbsentDirectionalSourceTerms() {
        var target = target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE);
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.BULLISH, new Absent(), TERMS_AVAILABLE),
                route(CallDirection.BULLISH), target, catalog(TERMS_AVAILABLE),
                resolved(ORIGINAL)));
        assertThat(result).isInstanceOfSatisfying(Unavailable.class, value -> {
            assertThat(value.reason()).isEqualTo(
                    UnavailableReason.TARGET_STATE_CONFLICT);
            assertThat(value.evidence().targetEvidence()).isSameAs(target);
            assertThat(value.evidence().catalogEvidence()).isNull();
        });
    }

    @Test
    void visibleWrongTargetConflictsWithAbsentNeutralBeforeCombinedNotApplicable() {
        var wrongTarget = target(
                new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                "wrong-asset", Currency.getInstance("EUR"), TERMS_AVAILABLE);
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.NEUTRAL, new Absent(), TERMS_AVAILABLE),
                route(CallDirection.NEUTRAL), wrongTarget, catalog(TERMS_AVAILABLE),
                resolved(ORIGINAL)));
        assertThat(result).isInstanceOfSatisfying(Unavailable.class, value -> {
            assertThat(value.reason()).isEqualTo(
                    UnavailableReason.TARGET_STATE_CONFLICT);
            assertThat(value.evidence().targetEvidence()).isSameAs(wrongTarget);
        });
    }

    @ParameterizedTest
    @EnumSource(value = CallDirection.class, names = {"BULLISH", "NEUTRAL"})
    void futureTargetRemainsInvisibleToAbsentTermsAndEqualsNullNotApplicable(
            CallDirection direction) {
        var absentTerms = terms(
                ORIGINAL, direction, new Absent(), TERMS_AVAILABLE);
        var nullResult = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, absentTerms, route(direction), null,
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        var futureWrong = target(
                new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                "wrong-asset", Currency.getInstance("EUR"),
                READY_AS_OF.plusNanos(1_000));
        var futureResult = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, absentTerms, route(direction), futureWrong,
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        assertThat(futureResult).isEqualTo(nullResult);
        assertThat(((NotApplicable) futureResult).reason()).isEqualTo(
                direction == CallDirection.NEUTRAL
                        ? NotApplicableReason.TARGET_ABSENT_AND_NON_DIRECTIONAL
                        : NotApplicableReason.TARGET_ABSENT);
    }

    @Test
    void neutralIsKnownNotApplicableEvenWithPresentTarget() {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.NEUTRAL, present(null), TERMS_AVAILABLE),
                route(CallDirection.NEUTRAL), null, null, resolved(ORIGINAL)));
        assertThat(result).isInstanceOfSatisfying(NotApplicable.class,
                value -> assertThat(value.reason())
                        .isEqualTo(NotApplicableReason.NON_DIRECTIONAL));
    }

    @Test
    void absentNeutralPreservesCombinedReason() {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.NEUTRAL, new Absent(), TERMS_AVAILABLE),
                route(CallDirection.NEUTRAL), null, null, resolved(ORIGINAL)));
        assertThat(((NotApplicable) result).reason())
                .isEqualTo(NotApplicableReason.TARGET_ABSENT_AND_NON_DIRECTIONAL);
    }

    @Test
    void nullAndFutureTermsAreWholeResultEqualAndLeakNothing() {
        var nullResult = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, null, route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        var futureResult = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                        CallDirection.BEARISH, present(null), READY_AS_OF.plusSeconds(1)),
                route(CallDirection.BEARISH),
                target(new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                        "wrong-asset", Currency.getInstance("EUR"),
                        READY_AS_OF.plusSeconds(1)),
                catalog(READY_AS_OF.plusSeconds(1)), resolved(ORIGINAL)));
        assertThat(futureResult).isEqualTo(nullResult);
        assertThat(((Unavailable) futureResult).evidence())
                .isEqualTo(new TargetEligibilityResolution.EligibilityEvidence(
                        null, null, null, null));
    }

    @Test
    void horizonBasisMismatchPrecedesRoutingAndClearsLaterEvidence() {
        var wrong = new OutcomeBasis.Original("wrong-call", BASIS_TIME);
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, terms(wrong, CallDirection.BULLISH, present(null), TERMS_AVAILABLE),
                route(CallDirection.BEARISH), target(wrong, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        assertUnavailable(result, UnavailableReason.HORIZON_BASIS_MISMATCH);
        var evidence = ((Unavailable) result).evidence();
        assertThat(evidence.sideRouting()).isNull();
        assertThat(evidence.targetEvidence()).isNull();
        assertThat(evidence.catalogEvidence()).isNull();
    }

    @Test
    void missingAndMismatchedRoutesAreExplicit() {
        var base = terms(ORIGINAL, CallDirection.BULLISH, present(null), TERMS_AVAILABLE);
        assertUnavailable(TargetEligibilityResolver.resolve(request(
                READY_AS_OF, base, null, null, null, resolved(ORIGINAL))),
                UnavailableReason.ROUTE_MISSING);
        assertUnavailable(TargetEligibilityResolver.resolve(request(
                READY_AS_OF, base, route(CallDirection.BEARISH), null, null,
                resolved(ORIGINAL))), UnavailableReason.ROUTE_DIRECTION_MISMATCH);
    }

    @Test
    void datedDirectionalTargetFailsClosedBeforeTargetOrCatalogEvidence() {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.BULLISH,
                        present(LocalDate.of(2027, 1, 1)), TERMS_AVAILABLE),
                route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        assertUnavailable(result, UnavailableReason.TARGET_DATE_SEMANTICS_UNSUPPORTED);
        assertThat(((Unavailable) result).evidence().targetEvidence()).isNull();
    }

    @Test
    void nullAndFutureTargetEvidenceAreWholeResultEqual() {
        var terms = terms(ORIGINAL, CallDirection.BULLISH, present(null), TERMS_AVAILABLE);
        var noTarget = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, terms, route(CallDirection.BULLISH), null,
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        var futureWrong = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, terms, route(CallDirection.BULLISH),
                target(new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                        "wrong-asset", Currency.getInstance("EUR"),
                        READY_AS_OF.plusSeconds(1)),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        assertThat(futureWrong).isEqualTo(noTarget);
        assertUnavailable(futureWrong,
                UnavailableReason.TARGET_EVIDENCE_NOT_KNOWN_AS_OF);
    }

    @Test
    void targetMismatchPrecedenceIsBasisThenAssetThenCurrency() {
        var terms = terms(ORIGINAL, CallDirection.BULLISH, present(null), TERMS_AVAILABLE);
        assertUnavailable(resolveWithTarget(terms,
                target(new OutcomeBasis.Original("wrong-call", BASIS_TIME),
                        "wrong-asset", Currency.getInstance("EUR"), TERMS_AVAILABLE)),
                UnavailableReason.TARGET_EVIDENCE_BASIS_MISMATCH);
        assertUnavailable(resolveWithTarget(terms,
                target(ORIGINAL, "wrong-asset", Currency.getInstance("EUR"), TERMS_AVAILABLE)),
                UnavailableReason.TARGET_ASSET_MISMATCH);
        assertUnavailable(resolveWithTarget(terms,
                target(ORIGINAL, "asset-1", Currency.getInstance("EUR"), TERMS_AVAILABLE)),
                UnavailableReason.TARGET_CURRENCY_MISMATCH);
    }

    @Test
    void nullAndFutureCatalogAreWholeResultEqual() {
        var noCatalog = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, presentTerms(), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE), null,
                resolved(ORIGINAL)));
        var future = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, presentTerms(), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                new CatalogPointInTimeEvidence("wrong", "wrong", "source", "revision",
                        READY_AS_OF.plusSeconds(1), READY_AS_OF.plusSeconds(1), "prov"),
                resolved(ORIGINAL)));
        assertThat(future).isEqualTo(noCatalog);
        assertUnavailable(future, UnavailableReason.CATALOG_NOT_KNOWN_AS_OF);
    }

    @Test
    void knownWrongCatalogIsExplicitMismatch() {
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF, presentTerms(), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                new CatalogPointInTimeEvidence("wrong", "wrong", "source", "revision",
                        TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"), resolved(ORIGINAL)));
        assertUnavailable(result, UnavailableReason.CATALOG_EVIDENCE_MISMATCH);
    }

    @Test
    void bothHorizonCoverageReasonsArePreservedExactly() {
        for (var nested : SessionCloseHorizonResolution.IncompleteReason.values()) {
            var result = TargetEligibilityResolver.resolve(request(
                    READY_AS_OF, presentTerms(), route(CallDirection.BULLISH),
                    target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                    catalog(TERMS_AVAILABLE), incomplete(ORIGINAL, nested)));
            var unavailable = (Unavailable) result;
            assertThat(unavailable.horizonReason()).isEqualTo(nested);
            assertThat(unavailable.reason()).isEqualTo(switch (nested) {
                case FIRST_ELIGIBLE_SESSION_MISSING ->
                        UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING;
                case HORIZON_ENDPOINT_SESSION_MISSING ->
                        UnavailableReason.HORIZON_ENDPOINT_SESSION_MISSING;
            });
        }
    }

    @Test
    void correctionUsesItsOwnExactBasisAndEventClock() {
        var correction = new OutcomeBasis.Correction(
                "call-1", "revision-2", BASIS_TIME.plusSeconds(60));
        var result = TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(correction, CallDirection.BEARISH, present(null), TERMS_AVAILABLE),
                route(CallDirection.BEARISH),
                target(correction, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(correction)));
        assertThat(result).isInstanceOf(ReadyForWindowEvidence.class);
        assertThat(((ReadyForWindowEvidence) result).evidence()
                .termsEvidence().basis()).isEqualTo(correction);
    }

    @Test
    void evidenceRecordMakesPresentAndAbsentStructurallyDistinct() {
        assertThatThrownBy(() -> new Present(BigDecimal.ZERO, USD, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Absent()).isNotEqualTo(new Present(BigDecimal.ONE, USD, null));
        assertThatThrownBy(() -> terms(ORIGINAL, CallDirection.BULLISH, null,
                TERMS_AVAILABLE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void directUnavailableCannotLeakFutureTargetEvidence() {
        var resolved = (Unavailable) TargetEligibilityResolver.resolve(request(
                READY_AS_OF, presentTerms(), route(CallDirection.BULLISH), null,
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        var leaked = new TargetEligibilityResolution.EligibilityEvidence(
                resolved.evidence().termsEvidence(), resolved.evidence().sideRouting(),
                target(ORIGINAL, "asset-1", USD, READY_AS_OF.plusSeconds(1)), null);
        assertThatThrownBy(() -> new Unavailable(
                resolved.context(), leaked,
                UnavailableReason.TARGET_EVIDENCE_NOT_KNOWN_AS_OF, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void termsAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries() {
        Instant edge = READY_AS_OF;
        Instant future = edge.plusNanos(1_000);
        var atAvailableEdge = termsAt(ORIGINAL, CallDirection.BULLISH, present(null),
                edge, edge);
        assertThat(resolveWithAll(atAvailableEdge,
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), edge))
                .isInstanceOf(ReadyForWindowEvidence.class);

        var nullTerms = TargetEligibilityResolver.resolve(request(edge, null,
                route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        var futureAvailable = termsAt(ORIGINAL, CallDirection.BULLISH, present(null),
                future, future);
        var futureCaptured = termsAt(ORIGINAL, CallDirection.BULLISH, present(null),
                TERMS_AVAILABLE, future);
        assertThat(resolveWithAll(futureAvailable,
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), edge)).isEqualTo(nullTerms);
        assertThat(resolveWithAll(futureCaptured,
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), edge)).isEqualTo(nullTerms);
    }

    @Test
    void targetAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries() {
        Instant edge = READY_AS_OF;
        Instant future = edge.plusNanos(1_000);
        var atEdge = targetAt(ORIGINAL, "asset-1", USD, "117.50", edge, edge);
        assertThat(resolveWithAll(presentTerms(), atEdge, catalog(TERMS_AVAILABLE), edge))
                .isInstanceOf(ReadyForWindowEvidence.class);

        var nullTarget = resolveWithAll(presentTerms(), null,
                catalog(TERMS_AVAILABLE), edge);
        var futureAvailable = targetAt(ORIGINAL, "wrong-asset",
                Currency.getInstance("EUR"), "999", future, future);
        var futureCaptured = targetAt(ORIGINAL, "wrong-asset",
                Currency.getInstance("EUR"), "999", TERMS_AVAILABLE, future);
        assertThat(resolveWithAll(presentTerms(), futureAvailable,
                catalog(TERMS_AVAILABLE), edge)).isEqualTo(nullTarget);
        assertThat(resolveWithAll(presentTerms(), futureCaptured,
                catalog(TERMS_AVAILABLE), edge)).isEqualTo(nullTarget);
    }

    @Test
    void catalogAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries() {
        Instant edge = READY_AS_OF;
        Instant future = edge.plusNanos(1_000);
        var atEdge = catalogAt("calendar-1", "revision-1", edge, edge);
        assertThat(resolveWithAll(presentTerms(),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE), atEdge, edge))
                .isInstanceOf(ReadyForWindowEvidence.class);

        var nullCatalog = resolveWithAll(presentTerms(),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE), null, edge);
        var futureAvailable = catalogAt("wrong", "wrong", future, future);
        var futureCaptured = catalogAt("wrong", "wrong", TERMS_AVAILABLE, future);
        assertThat(resolveWithAll(presentTerms(),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                futureAvailable, edge)).isEqualTo(nullCatalog);
        assertThat(resolveWithAll(presentTerms(),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                futureCaptured, edge)).isEqualTo(nullCatalog);
    }

    @Test
    void sourceAndNormalizedTargetValuesRemainSeparateWithoutEqualityInference() {
        var terms = termsAt(ORIGINAL, CallDirection.BULLISH,
                new Present(new BigDecimal("235.00"), USD, null),
                TERMS_AVAILABLE, TERMS_AVAILABLE);
        var normalized = targetAt(ORIGINAL, "asset-1", USD, "117.50",
                TERMS_AVAILABLE, TERMS_AVAILABLE);
        var result = resolveWithAll(terms, normalized, catalog(TERMS_AVAILABLE),
                READY_AS_OF);
        assertThat(result).isInstanceOfSatisfying(ReadyForWindowEvidence.class,
                value -> {
                    var source = (Present) value.evidence().termsEvidence()
                            .targetDisposition();
                    assertThat(source.sourceTarget()).isEqualByComparingTo("235.00");
                    assertThat(value.evidence().targetEvidence().target())
                            .isEqualByComparingTo("117.50");
                });
    }

    @Test
    void sourceTermsRejectNullBlankUntrimmedAndInvalidTimes() {
        var valid = present(null);
        assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                null, ORIGINAL, "asset-1", CallDirection.BULLISH, valid,
                "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                .isInstanceOf(NullPointerException.class);
        for (String bad : List.of("", " ", " value ")) {
            assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                    bad, ORIGINAL, "asset-1", CallDirection.BULLISH, valid,
                    "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                    "terms", ORIGINAL, bad, CallDirection.BULLISH, valid,
                    "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                    "terms", ORIGINAL, "asset-1", CallDirection.BULLISH, valid,
                    bad, "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                    "terms", ORIGINAL, "asset-1", CallDirection.BULLISH, valid,
                    "provider", bad, TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                    "terms", ORIGINAL, "asset-1", CallDirection.BULLISH, valid,
                    "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> termsAt(ORIGINAL, CallDirection.BULLISH, valid,
                BASIS_TIME.minusNanos(1_000), TERMS_AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> termsAt(ORIGINAL, CallDirection.BULLISH, valid,
                TERMS_AVAILABLE, TERMS_AVAILABLE.minusNanos(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> termsAt(ORIGINAL, CallDirection.BULLISH, valid,
                TERMS_AVAILABLE.plusNanos(1), TERMS_AVAILABLE.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                "terms", null, "asset-1", CallDirection.BULLISH, valid,
                "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                "terms", ORIGINAL, "asset-1", null, valid,
                "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BasisForecastTermsEvidence(
                "terms", ORIGINAL, "asset-1", CallDirection.BULLISH, null,
                "provider", "event", TERMS_AVAILABLE, TERMS_AVAILABLE, "prov"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void presentSourceTargetEnforcesExactNumeric38Scale12Boundary() {
        assertThat(new Present(new BigDecimal("99999999999999999999999999.999999999999"),
                USD, null).sourceTarget()).isNotNull();
        for (BigDecimal invalid : List.of(
                BigDecimal.ZERO,
                BigDecimal.ONE.negate(),
                new BigDecimal("1.0000000000001"),
                new BigDecimal("100000000000000000000000000.000000000000"))) {
            assertThatThrownBy(() -> new Present(invalid, USD, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Present(null, USD, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Present(BigDecimal.ONE, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requestAndContextRejectNullsFinerInstantsAndWrongHash() {
        assertThatThrownBy(() -> new TargetEligibilityRequest(
                null, resolved(ORIGINAL), presentTerms(), route(CallDirection.BULLISH),
                null, null, READY_AS_OF)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetEligibilityRequest(
                policy(), null, presentTerms(), route(CallDirection.BULLISH),
                null, null, READY_AS_OF)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetEligibilityRequest(
                policy(), resolved(ORIGINAL), presentTerms(), route(CallDirection.BULLISH),
                null, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetEligibilityRequest(
                policy(), resolved(ORIGINAL), presentTerms(), route(CallDirection.BULLISH),
                null, null, READY_AS_OF.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetEligibilityResolution.ResolutionContext(
                policy(), "0".repeat(64), resolved(ORIGINAL), READY_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directBranchConstructorsRejectMissingContradictoryAndFutureComponents() {
        var ready = (ReadyForWindowEvidence) TargetEligibilityResolver.resolve(
                completeRequest(READY_AS_OF));
        var context = ready.context();
        var full = ready.evidence();
        for (var incompleteEvidence : List.of(
                new TargetEligibilityResolution.EligibilityEvidence(
                        null, full.sideRouting(), full.targetEvidence(), full.catalogEvidence()),
                new TargetEligibilityResolution.EligibilityEvidence(
                        full.termsEvidence(), null, full.targetEvidence(), full.catalogEvidence()),
                new TargetEligibilityResolution.EligibilityEvidence(
                        full.termsEvidence(), full.sideRouting(), null, full.catalogEvidence()),
                new TargetEligibilityResolution.EligibilityEvidence(
                        full.termsEvidence(), full.sideRouting(), full.targetEvidence(), null))) {
            assertThatThrownBy(() -> new ReadyForWindowEvidence(context, incompleteEvidence))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Pending(context, full,
                PendingReason.HORIZON_NOT_REACHED_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotApplicable(context,
                new TargetEligibilityResolution.EligibilityEvidence(
                        full.termsEvidence(), full.sideRouting(), full.targetEvidence(), null),
                NotApplicableReason.TARGET_ABSENT))
                .isInstanceOf(IllegalArgumentException.class);

        var horizonMissing = (Unavailable) TargetEligibilityResolver.resolve(request(
                READY_AS_OF, presentTerms(), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), incomplete(ORIGINAL,
                        SessionCloseHorizonResolution.IncompleteReason
                                .FIRST_ELIGIBLE_SESSION_MISSING)));
        assertThatThrownBy(() -> new Unavailable(
                horizonMissing.context(), horizonMissing.evidence(),
                UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING,
                SessionCloseHorizonResolution.IncompleteReason
                        .HORIZON_ENDPOINT_SESSION_MISSING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Unavailable(
                horizonMissing.context(), horizonMissing.evidence(),
                UnavailableReason.CATALOG_NOT_KNOWN_AS_OF,
                horizonMissing.horizonReason()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directTargetStateConflictRequiresAbsentTermsVisibleTargetAndClearedCatalog() {
        var conflict = (Unavailable) TargetEligibilityResolver.resolve(request(
                READY_AS_OF,
                terms(ORIGINAL, CallDirection.BULLISH, new Absent(), TERMS_AVAILABLE),
                route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
        assertThat(conflict.reason()).isEqualTo(UnavailableReason.TARGET_STATE_CONFLICT);

        var missingTarget = new TargetEligibilityResolution.EligibilityEvidence(
                conflict.evidence().termsEvidence(), conflict.evidence().sideRouting(),
                null, null);
        assertThatThrownBy(() -> new Unavailable(
                conflict.context(), missingTarget,
                UnavailableReason.TARGET_STATE_CONFLICT, null))
                .isInstanceOf(NullPointerException.class);

        var futureTarget = new TargetEligibilityResolution.EligibilityEvidence(
                conflict.evidence().termsEvidence(), conflict.evidence().sideRouting(),
                target(ORIGINAL, "asset-1", USD, READY_AS_OF.plusNanos(1_000)), null);
        assertThatThrownBy(() -> new Unavailable(
                conflict.context(), futureTarget,
                UnavailableReason.TARGET_STATE_CONFLICT, null))
                .isInstanceOf(IllegalArgumentException.class);

        var presentTerms = new TargetEligibilityResolution.EligibilityEvidence(
                presentTerms(), conflict.evidence().sideRouting(),
                conflict.evidence().targetEvidence(), null);
        assertThatThrownBy(() -> new Unavailable(
                conflict.context(), presentTerms,
                UnavailableReason.TARGET_STATE_CONFLICT, null))
                .isInstanceOf(IllegalArgumentException.class);

        var leakedCatalog = new TargetEligibilityResolution.EligibilityEvidence(
                conflict.evidence().termsEvidence(), conflict.evidence().sideRouting(),
                conflict.evidence().targetEvidence(), catalog(TERMS_AVAILABLE));
        assertThatThrownBy(() -> new Unavailable(
                conflict.context(), leakedCatalog,
                UnavailableReason.TARGET_STATE_CONFLICT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localeAndDefaultTimezoneCannotChangeReplay() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            var expected = TargetEligibilityResolver.resolve(completeRequest(READY_AS_OF));
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Apia"));
            assertThat(TargetEligibilityResolver.resolve(completeRequest(READY_AS_OF)))
                    .isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private static TargetEligibilityRequest completeRequest(Instant asOf) {
        return request(asOf, presentTerms(), route(CallDirection.BULLISH),
                target(ORIGINAL, "asset-1", USD, TERMS_AVAILABLE),
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL));
    }

    private static TargetEligibilityResolution resolveWithAll(
            BasisForecastTermsEvidence terms,
            TargetPriceEvidence target,
            CatalogPointInTimeEvidence catalog,
            Instant asOf) {
        return TargetEligibilityResolver.resolve(request(
                asOf, terms, route(CallDirection.BULLISH), target, catalog,
                resolved(ORIGINAL)));
    }

    private static TargetEligibilityResolution resolveWithTarget(
            BasisForecastTermsEvidence terms,
            TargetPriceEvidence target) {
        return TargetEligibilityResolver.resolve(request(
                READY_AS_OF, terms, route(CallDirection.BULLISH), target,
                catalog(TERMS_AVAILABLE), resolved(ORIGINAL)));
    }

    private static void assertUnavailable(
            TargetEligibilityResolution result,
            UnavailableReason reason) {
        assertThat(result).isInstanceOfSatisfying(Unavailable.class,
                value -> assertThat(value.reason()).isEqualTo(reason));
    }

    private static TargetEligibilityRequest request(
            Instant asOf,
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result routing,
            TargetPriceEvidence target,
            CatalogPointInTimeEvidence catalog,
            SessionCloseHorizonResolution horizon) {
        return new TargetEligibilityRequest(
                policy(), horizon, terms, routing, target, catalog, asOf);
    }

    private static TargetEligibilityPolicyVersion policy() {
        return TargetEligibilityPolicyVersion
                .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1;
    }

    private static BasisForecastTermsEvidence presentTerms() {
        return terms(ORIGINAL, CallDirection.BULLISH, present(null), TERMS_AVAILABLE);
    }

    private static Present present(LocalDate targetDate) {
        return new Present(new BigDecimal("235.00"), USD, targetDate);
    }

    private static BasisForecastTermsEvidence terms(
            OutcomeBasis basis,
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition,
            Instant availableAt) {
        return termsAt(basis, direction, disposition, availableAt, availableAt);
    }

    private static BasisForecastTermsEvidence termsAt(
            OutcomeBasis basis,
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition,
            Instant availableAt,
            Instant capturedAt) {
        return new BasisForecastTermsEvidence(
                "terms-1", basis, "asset-1", direction, disposition,
                "provider", "provider-event-1", availableAt, capturedAt,
                "terms-provenance");
    }

    private static CalculatorSideRouting.Result route(CallDirection direction) {
        return CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(
                        CallDirectionPolarityPolicyVersion
                                .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                        direction)));
    }

    private static TargetPriceEvidence target(
            OutcomeBasis basis,
            String assetId,
            Currency currency,
            Instant availableAt) {
        return targetAt(basis, assetId, currency, "235.00", availableAt, availableAt);
    }

    private static TargetPriceEvidence targetAt(
            OutcomeBasis basis,
            String assetId,
            Currency currency,
            String value,
            Instant availableAt,
            Instant capturedAt) {
        return new TargetPriceEvidence(
                "target-1", basis, assetId, "venue-1", currency,
                EndpointPriceAdjustmentBasis
                        .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED,
                new BigDecimal(value), availableAt, capturedAt,
                "target-provenance");
    }

    private static CatalogPointInTimeEvidence catalog(Instant availableAt) {
        return catalogAt("calendar-1", "revision-1", availableAt, availableAt);
    }

    private static CatalogPointInTimeEvidence catalogAt(
            String calendarId,
            String revision,
            Instant availableAt,
            Instant capturedAt) {
        return new CatalogPointInTimeEvidence(
                calendarId, revision, "calendar-source", "source-revision-1",
                availableAt, capturedAt, "catalog-provenance");
    }

    private static SessionCloseHorizonResolution resolved(OutcomeBasis basis) {
        var context = horizonContext(basis);
        var session = new TradingSession(
                "session-1", ENDPOINT_CLOSE.minusSeconds(6 * 60 * 60), ENDPOINT_CLOSE);
        return new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        context, List.of(session), session));
    }

    private static SessionCloseHorizonResolution incomplete(
            OutcomeBasis basis,
            SessionCloseHorizonResolution.IncompleteReason reason) {
        return new SessionCloseHorizonResolution.Incomplete(horizonContext(basis), reason);
    }

    private static SessionCloseHorizonResolution.ResolutionContext horizonContext(
            OutcomeBasis basis) {
        var version = SessionCloseHorizonPolicyVersion
                .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1;
        return new SessionCloseHorizonResolution.ResolutionContext(
                version, version.definitionHash(), basis, OutcomeHorizon.D1,
                version.sessionCount(OutcomeHorizon.D1), "calendar-1", "revision-1");
    }

    private static CallDirection routeDirection(CalculatorSideRouting.Result route) {
        return switch (route) {
            case CalculatorSideRouting.DirectionalRoute directional ->
                    directional.source().context().direction();
            case CalculatorSideRouting.NonDirectionalRoute nonDirectional ->
                    nonDirectional.source().context().direction();
        };
    }

    private static Set<String> permittedSimpleNames(Class<?> type) {
        return Arrays.stream(type.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(type.isRecord()).as(type.getSimpleName()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName())
                .toList()).containsExactly(expected);
    }
}
