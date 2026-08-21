package com.wallstreetreceipts.api.domain.outcome.direction;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.call.CallDirection;

/**
 * Closed polarity reduction result for one exact canonical direction.
 *
 * <p>The result preserves the policy digest and supplied direction only. It
 * does not attest which original or correction supplied that direction, its
 * point-in-time eligibility, or the eligibility of any calculated metric.</p>
 */
public sealed interface CallDirectionPolarityResolution
        permits CallDirectionPolarityResolution.Directional,
        CallDirectionPolarityResolution.NonDirectional {

    /** The two forecast polarities accepted by later scoring primitives. */
    enum DirectionalSide {
        BULLISH,
        BEARISH
    }

    /** Exact reason why a canonical direction has no scoring polarity. */
    enum NonDirectionalReason {
        NEUTRAL_DIRECTION
    }

    /** Exact policy identity and caller-supplied direction used for replay. */
    record ResolutionContext(
            CallDirectionPolarityPolicyVersion policyVersion,
            String policyDefinitionHash,
            CallDirection direction) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(
                    policyDefinitionHash, "policyDefinitionHash must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** A non-neutral direction reduced to its exact bullish/bearish polarity. */
    record Directional(
            ResolutionContext context,
            DirectionalSide side) implements CallDirectionPolarityResolution {

        public Directional {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(side, "side must not be null");
            DirectionalSide expectedSide = expectedDirectionalSide(context.direction());
            if (expectedSide == null) {
                throw new IllegalArgumentException(
                        "a neutral direction must be non-directional");
            }
            if (side != expectedSide) {
                throw new IllegalArgumentException(
                        "side must match the canonical direction polarity");
            }
        }
    }

    /** Neutral direction preserved as explicit non-directional evidence. */
    record NonDirectional(
            ResolutionContext context,
            NonDirectionalReason reason) implements CallDirectionPolarityResolution {

        public NonDirectional {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (context.direction() != CallDirection.NEUTRAL) {
                throw new IllegalArgumentException(
                        "only a neutral direction may be non-directional");
            }
        }
    }

    private static DirectionalSide expectedDirectionalSide(CallDirection direction) {
        return switch (direction) {
            case STRONG_BULLISH, BULLISH -> DirectionalSide.BULLISH;
            case NEUTRAL -> null;
            case BEARISH, STRONG_BEARISH -> DirectionalSide.BEARISH;
        };
    }
}
