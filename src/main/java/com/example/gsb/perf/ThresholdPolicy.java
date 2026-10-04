package com.example.gsb.perf;

/**
 * Practical significance gate: a slowdown only counts as a regression when it
 * also exceeds the configured threshold(s).
 *
 * <ul>
 *   <li>absolute: median slowdown in nanoseconds must exceed
 *       {@code absoluteThresholdNanos}</li>
 *   <li>relative: change ratio (cur-base)/base must exceed
 *       {@code relativeThreshold} (e.g. 0.05 = 5%)</li>
 * </ul>
 *
 * When both are configured, {@link Combination#ANY} (default) requires one of
 * them to be exceeded, {@link Combination#ALL} requires both. When nothing is
 * configured the policy never blocks a verdict and statistical significance
 * alone decides.
 */
public final class ThresholdPolicy {

    public enum Combination { ANY, ALL }

    private final long absoluteThresholdNanos;
    private final double relativeThreshold;
    private final Combination combination;

    private ThresholdPolicy(long absoluteThresholdNanos, double relativeThreshold,
                            Combination combination) {
        this.absoluteThresholdNanos = absoluteThresholdNanos;
        this.relativeThreshold = relativeThreshold;
        this.combination = combination;
    }

    public static ThresholdPolicy none() {
        return new ThresholdPolicy(-1, -1, Combination.ANY);
    }

    public static ThresholdPolicy absoluteNanos(long nanos) {
        return new ThresholdPolicy(nanos, -1, Combination.ANY);
    }

    public static ThresholdPolicy relative(double ratio) {
        return new ThresholdPolicy(-1, ratio, Combination.ANY);
    }

    public static ThresholdPolicy of(long absoluteThresholdNanos, double relativeThreshold,
                                     Combination combination) {
        return new ThresholdPolicy(absoluteThresholdNanos, relativeThreshold, combination);
    }

    public boolean hasAbsolute() {
        return absoluteThresholdNanos >= 0;
    }

    public boolean hasRelative() {
        return relativeThreshold >= 0;
    }

    public boolean isConfigured() {
        return hasAbsolute() || hasRelative();
    }

    public long absoluteThresholdNanos() {
        return absoluteThresholdNanos;
    }

    public double relativeThreshold() {
        return relativeThreshold;
    }

    public Combination combination() {
        return combination;
    }

    /** True when the observed slowdown breaches the policy. */
    public boolean breachedBy(double deltaNanos, double changeRatio) {
        if (!isConfigured()) {
            return true;
        }
        boolean absoluteHit = hasAbsolute() && deltaNanos > absoluteThresholdNanos;
        boolean relativeHit = hasRelative() && changeRatio > relativeThreshold;
        if (hasAbsolute() && hasRelative()) {
            return combination == Combination.ALL ? absoluteHit && relativeHit
                    : absoluteHit || relativeHit;
        }
        return hasAbsolute() ? absoluteHit : relativeHit;
    }

    public String describe() {
        if (!isConfigured()) {
            return "no threshold (significance only)";
        }
        StringBuilder sb = new StringBuilder();
        if (hasAbsolute()) {
            sb.append("abs>").append(absoluteThresholdNanos).append("ns");
        }
        if (hasRelative()) {
            if (sb.length() > 0) {
                sb.append(combination == Combination.ALL ? " AND " : " OR ");
            }
            sb.append("rel>").append(String.format("%.1f%%", relativeThreshold * 100));
        }
        return sb.toString();
    }
}
