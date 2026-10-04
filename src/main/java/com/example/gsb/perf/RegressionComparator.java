package com.example.gsb.perf;

import java.util.Locale;

/**
 * Decides whether a new sample set regressed against a baseline.
 *
 * <h2>Significance method</h2>
 * Spread is measured with the median absolute deviation (MAD), a robust
 * estimator that ignores outliers and skewed tails. MAD is scaled by 1.4826
 * to be consistent with a standard deviation on normal data. The standard
 * error of each median is approximated as
 * {@code SE = 1.2533 * sigma / sqrt(n)} (the asymptotic SE of the median),
 * and the two distributions are compared with a two-sided z test:
 * <pre>
 * significant iff |median_current - median_baseline|
 *                 &gt; z * sqrt(SE_baseline^2 + SE_current^2)
 * </pre>
 * z defaults to 1.96 (95% confidence; 2.576 for 99%). When a distribution is
 * effectively degenerate (MAD == 0, typical for very fast/constant code), the
 * sigma falls back to 1% of its median so the noise band can never collapse
 * to zero. A slowdown is a {@link Verdict#REGRESSION} only when the shift is
 * statistically significant <em>and</em> breaches the {@link ThresholdPolicy};
 * changes inside the noise band are always reported as NO_REGRESSION, which
 * prevents ordinary benchmark jitter from being treated as a regression.
 */
public final class RegressionComparator {

    public static final double CONFIDENCE_95 = 1.96;
    public static final double CONFIDENCE_99 = 2.576;

    private final double zScore;
    private final int minSamples;
    private final ThresholdPolicy thresholdPolicy;

    public RegressionComparator(ThresholdPolicy thresholdPolicy) {
        this(thresholdPolicy, CONFIDENCE_95, 3);
    }

    public RegressionComparator(ThresholdPolicy thresholdPolicy, double zScore, int minSamples) {
        if (minSamples < 1) {
            throw new IllegalArgumentException("minSamples must be >= 1");
        }
        this.thresholdPolicy = thresholdPolicy;
        this.zScore = zScore;
        this.minSamples = minSamples;
    }

    public ThresholdPolicy thresholdPolicy() {
        return thresholdPolicy;
    }

    public double zScore() {
        return zScore;
    }

    /** Half width of the noise band (nanoseconds) for the two distributions. */
    public double noiseBandNanos(Baseline baseline, Samples current) {
        double seBaseline = standardErrorMedian(baseline.robustSigma(), baseline.sampleCount());
        double seCurrent = standardErrorMedian(current.robustSigma(), current.size());
        return zScore * Math.sqrt(seBaseline * seBaseline + seCurrent * seCurrent);
    }

    public ComparisonResult compare(Baseline baseline, Samples current) {
        double baselineMedian = baseline.medianNanos();
        double currentMedian = current.median();
        double delta = currentMedian - baselineMedian;
        double changeRatio = baselineMedian == 0 ? 0 : delta / baselineMedian;

        if (baseline.sampleCount() < minSamples || current.size() < minSamples) {
            String note = String.format(Locale.ROOT,
                    "insufficient data: baseline n=%d, current n=%d, need n>=%d",
                    baseline.sampleCount(), current.size(), minSamples);
            return new ComparisonResult(baseline.benchmarkName(), baselineMedian, currentMedian,
                    delta, changeRatio, false, false, Verdict.INSUFFICIENT_DATA, note, false);
        }

        double noiseBand = noiseBandNanos(baseline, current);
        boolean significant = Math.abs(delta) > noiseBand;
        boolean thresholdBreached = thresholdPolicy.breachedBy(delta, changeRatio);
        double noiseBandRatio = baselineMedian == 0 ? 0 : noiseBand / baselineMedian;

        Verdict verdict;
        String note;
        if (!significant) {
            verdict = Verdict.NO_REGRESSION;
            note = String.format(Locale.ROOT,
                    "shift %,.0f ns (%s) is within the %.0f%%-confidence noise band ±%,.0f ns (±%.2f%%); treated as jitter",
                    delta, formatPercent(changeRatio), confidencePercent(), noiseBand,
                    noiseBandRatio * 100);
        } else if (delta > 0) {
            if (thresholdBreached) {
                verdict = Verdict.REGRESSION;
                note = String.format(Locale.ROOT,
                        "slowdown %,.0f ns (%s) exceeds the %.0f%%-confidence noise band ±%,.0f ns (±%.2f%%) and breaches threshold [%s]",
                        delta, formatPercent(changeRatio), confidencePercent(), noiseBand,
                        noiseBandRatio * 100, thresholdPolicy.describe());
            } else {
                verdict = Verdict.NO_REGRESSION;
                note = String.format(Locale.ROOT,
                        "slowdown %,.0f ns (%s) is statistically significant (noise band ±%,.0f ns, ±%.2f%%) but within allowed threshold [%s]",
                        delta, formatPercent(changeRatio), noiseBand, noiseBandRatio * 100,
                        thresholdPolicy.describe());
            }
        } else {
            verdict = Verdict.IMPROVEMENT;
            note = String.format(Locale.ROOT,
                    "speedup %,.0f ns (%s) exceeds the %.0f%%-confidence noise band ±%,.0f ns (±%.2f%%)",
                    delta, formatPercent(changeRatio), confidencePercent(), noiseBand,
                    noiseBandRatio * 100);
        }
        return new ComparisonResult(baseline.benchmarkName(), baselineMedian, currentMedian,
                delta, changeRatio, significant, thresholdBreached, verdict, note, false);
    }

    private double confidencePercent() {
        if (zScore >= CONFIDENCE_99 - 1e-9) {
            return 99;
        }
        if (zScore >= CONFIDENCE_95 - 1e-9) {
            return 95;
        }
        return 90;
    }

    private static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%+.2f%%", ratio * 100);
    }

    private static double standardErrorMedian(double sigma, int n) {
        return 1.2533 * sigma / Math.sqrt(n);
    }
}
