package com.example.notificationdemo.filter;

/** Android-independent attention arithmetic and the explicitly allowed feedback mapping. */
public final class AttentionMath {
    public static final int REASON_CLICK = 1;
    public static final int REASON_CANCEL = 2;
    public static final int REASON_IGNORE = -1000;
    public static final long FAST_CLICK_MS = 60_000;
    public static final long IGNORE_AFTER_MS = 30 * 60_000L;

    private AttentionMath() { }

    public enum Kind { FAST_CLICK, SLOW_CLICK, DISMISS, IGNORE }

    public static final class Observation {
        public final Kind kind;
        public final double y;
        public final int reason;
        private Observation(Kind kind, double y, int reason) {
            this.kind = kind; this.y = y; this.reason = reason;
        }
    }

    /** All other Android reasons, including cancel-all and listener cancellation, are ignored. */
    public static Observation observationForRemoval(int reason, long elapsedMs) {
        if (reason == REASON_CLICK) {
            if (elapsedMs < 0) return null;
            return elapsedMs <= FAST_CLICK_MS
                    ? new Observation(Kind.FAST_CLICK, 1.0, reason)
                    : new Observation(Kind.SLOW_CLICK, 0.7, reason);
        }
        if (reason == REASON_CANCEL) return new Observation(Kind.DISMISS, 0.05, reason);
        return null;
    }

    /** Caller must establish that this particular notification remained active for 30 minutes. */
    public static Observation ignoredObservation() { return new Observation(Kind.IGNORE, 0.2, REASON_IGNORE); }

    public static double halfLifeMillis(double halfLifeMinutes) {
        if (!Double.isFinite(halfLifeMinutes) || halfLifeMinutes <= 0 || halfLifeMinutes > 525_600_000) {
            throw new IllegalArgumentException("halfLifeMinutes must be finite and positive");
        }
        return halfLifeMinutes * 60_000.0;
    }

    public static double decayFactor(long elapsedMs, double halfLifeMinutes) {
        return Math.pow(0.5, Math.max(0, elapsedMs) / halfLifeMillis(halfLifeMinutes));
    }

    public static double rawN(double alpha, double beta) {
        validatePosterior(alpha, beta);
        return alpha + beta - 2.0;
    }

    public static double effectiveN(double alpha, double beta) { return Math.max(0.0, rawN(alpha, beta)); }

    public static double shortProbability(double alpha, double beta) {
        validatePosterior(alpha, beta);
        double total = alpha + beta;
        return total == 0.0 ? 0.5 : alpha / total;
    }

    public static double logit(double probability) {
        requireProbability(probability);
        double bounded = Math.max(0.01, Math.min(0.99, probability));
        return Math.log(bounded / (1.0 - bounded));
    }

    public static double sigmoid(double value) {
        if (Double.isNaN(value)) throw new IllegalArgumentException("NaN logit");
        if (value >= 0) return 1.0 / (1.0 + Math.exp(-value));
        double exponential = Math.exp(value);
        return exponential / (1.0 + exponential);
    }

    /** Both priors decay. Negative raw n is preserved for inspection, but never becomes a weight. */
    public static double fuse(double pJev, double alpha, double beta, double weight) {
        requireProbability(pJev);
        if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("weight must be finite and nonnegative");
        double n = effectiveN(alpha, beta);
        // Deliberately before clamping/logit: a no-evidence result is bit-for-bit unchanged.
        if (n <= 0.0 || weight == 0.0) return pJev;
        return sigmoid(logit(pJev) + weight * (n / (n + 3.0)) * logit(shortProbability(alpha, beta)));
    }

    private static void requireProbability(double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) throw new IllegalArgumentException("invalid probability");
    }

    private static void validatePosterior(double alpha, double beta) {
        if (!Double.isFinite(alpha) || !Double.isFinite(beta) || alpha < 0 || beta < 0 || !Double.isFinite(alpha + beta)) {
            throw new IllegalArgumentException("invalid posterior");
        }
    }
}
