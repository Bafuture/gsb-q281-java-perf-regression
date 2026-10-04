package com.example.gsb.perf;

public enum Verdict {
    /** Statistically significant slowdown that also breaches the threshold policy. */
    REGRESSION,
    /** No significant slowdown, or slowdown within the allowed threshold. */
    NO_REGRESSION,
    /** Statistically significant speedup. */
    IMPROVEMENT,
    /** Not enough samples to draw a conclusion. */
    INSUFFICIENT_DATA
}
