package com.example.gsb.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegressionComparatorTest {

    private static final EnvironmentFingerprint ENV =
            new EnvironmentFingerprint("test", "Linux", "amd64", "17", "vm", "");

    /** 21 samples centered on {@code center} with MAD = 5. */
    private static Samples spread(double center) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            values.add(center - 5);
        }
        values.add(center);
        for (int i = 0; i < 10; i++) {
            values.add(center + 5);
        }
        return Samples.of(values);
    }

    private static Baseline baselineOf(Samples samples) {
        return Baseline.fromSamples("bench", ENV, samples, 2);
    }

    @Test
    void jitterWithinNoiseBandIsNotARegression() {
        RegressionComparator comparator = new RegressionComparator(ThresholdPolicy.none());
        Baseline baseline = baselineOf(spread(1000));
        // same distribution shape, median shifted by only 5 ns (noise)
        ComparisonResult result = comparator.compare(baseline, spread(1005));
        assertThat(result.verdict()).isEqualTo(Verdict.NO_REGRESSION);
        assertThat(result.significant()).isFalse();
        assertThat(result.confidenceNote()).contains("noise band").contains("jitter");
    }

    @Test
    void realSlowdownBeyondNoiseBandIsSignificant() {
        RegressionComparator comparator = new RegressionComparator(ThresholdPolicy.none());
        Baseline baseline = baselineOf(spread(1000));
        ComparisonResult result = comparator.compare(baseline, spread(1020));
        assertThat(result.significant()).isTrue();
        assertThat(result.verdict()).isEqualTo(Verdict.REGRESSION);
        assertThat(result.changeRatio()).isEqualTo(0.02);
        assertThat(result.confidenceNote()).contains("95%");
    }

    @Test
    void significantSpeedupIsReportedAsImprovement() {
        RegressionComparator comparator = new RegressionComparator(ThresholdPolicy.none());
        Baseline baseline = baselineOf(spread(1000));
        ComparisonResult result = comparator.compare(baseline, spread(970));
        assertThat(result.verdict()).isEqualTo(Verdict.IMPROVEMENT);
        assertThat(result.significant()).isTrue();
    }

    @Test
    void relativeThresholdSuppressesSmallButSignificantSlowdown() {
        RegressionComparator comparator =
                new RegressionComparator(ThresholdPolicy.relative(0.05));
        Baseline baseline = baselineOf(spread(1000));
        // +2% is significant but below the 5% relative threshold
        ComparisonResult result = comparator.compare(baseline, spread(1020));
        assertThat(result.significant()).isTrue();
        assertThat(result.thresholdBreached()).isFalse();
        assertThat(result.verdict()).isEqualTo(Verdict.NO_REGRESSION);
        assertThat(result.confidenceNote()).contains("within allowed threshold");
    }

    @Test
    void absoluteThresholdTriggersOnLargeEnoughSlowdown() {
        RegressionComparator comparator =
                new RegressionComparator(ThresholdPolicy.absoluteNanos(15));
        Baseline baseline = baselineOf(spread(1000));
        ComparisonResult hit = comparator.compare(baseline, spread(1020));
        assertThat(hit.thresholdBreached()).isTrue();
        assertThat(hit.verdict()).isEqualTo(Verdict.REGRESSION);

        ComparisonResult miss = comparator.compare(baseline, spread(1010));
        assertThat(miss.thresholdBreached()).isFalse();
        assertThat(miss.verdict()).isEqualTo(Verdict.NO_REGRESSION);
    }

    @Test
    void combinedThresholdsWithAnyAndAll() {
        Baseline baseline = baselineOf(spread(1000));
        Samples current = spread(1012); // +12 ns, +1.2%

        RegressionComparator any = new RegressionComparator(
                ThresholdPolicy.of(10, 0.05, ThresholdPolicy.Combination.ANY));
        ComparisonResult anyResult = any.compare(baseline, current);
        assertThat(anyResult.thresholdBreached()).isTrue(); // absolute hit is enough
        assertThat(anyResult.verdict()).isEqualTo(Verdict.REGRESSION);

        RegressionComparator all = new RegressionComparator(
                ThresholdPolicy.of(10, 0.05, ThresholdPolicy.Combination.ALL));
        ComparisonResult allResult = all.compare(baseline, current);
        assertThat(allResult.thresholdBreached()).isFalse(); // relative leg misses
        assertThat(allResult.verdict()).isEqualTo(Verdict.NO_REGRESSION);
    }

    @Test
    void insufficientSamplesYieldInsufficientData() {
        RegressionComparator comparator = new RegressionComparator(ThresholdPolicy.none());
        Baseline baseline = baselineOf(spread(1000));
        Samples tiny = Samples.of(List.of(2000.0, 2000.0));
        ComparisonResult result = comparator.compare(baseline, tiny);
        assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_DATA);
        assertThat(result.confidenceNote()).contains("insufficient data");
    }

    @Test
    void degenerateDistributionsStillGetNonZeroNoiseBand() {
        RegressionComparator comparator = new RegressionComparator(ThresholdPolicy.none());
        Samples constant = Samples.of(List.of(100.0, 100.0, 100.0, 100.0, 100.0));
        Baseline baseline = baselineOf(constant);
        assertThat(comparator.noiseBandNanos(baseline, constant)).isGreaterThan(0);
        // identical constant distributions: no regression
        assertThat(comparator.compare(baseline, constant).verdict())
                .isEqualTo(Verdict.NO_REGRESSION);
    }
}
