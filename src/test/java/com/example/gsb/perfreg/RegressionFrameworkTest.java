package com.example.gsb.perfreg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionFrameworkTest {

    @TempDir
    Path baselineDir;

    private static LongSupplier cycling(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.floorMod(index.getAndIncrement(), values.length)];
    }

    private static long[] series(long start, int count) {
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            values[i] = start + i;
        }
        return values;
    }

    private RegressionFramework framework(String tag, ThresholdPolicy policy) {
        return RegressionFramework.builder(baselineDir)
                .environmentTag(tag)
                .sampling(SamplingConfig.of(3, 15))
                .thresholds(policy)
                .build();
    }

    @Test
    void createsBaselineOnFirstRunAndPersistsIt() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));

        BenchmarkReport report = framework.check("sort", cycling(series(100, 15)));

        assertThat(report.verdict()).isEqualTo(Verdict.BASELINE_CREATED);
        assertThat(report.currentMedianNanos()).isEqualTo(107.0);
        assertThat(report.currentSampleCount()).isEqualTo(15);
        assertThat(report.warmupDiscarded()).isEqualTo(3);
        assertThat(report.confidenceNote()).contains("基线");
        assertThat(directoryCount(baselineDir)).isEqualTo(1);
        assertThat(propertiesFileCount(baselineDir)).isEqualTo(1);
    }

    @Test
    void noRegressionWhenDistributionMatchesBaseline() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));
        framework.check("sort", cycling(series(100, 15)));

        BenchmarkReport report = framework.check("sort", cycling(series(100, 15)));

        assertThat(report.verdict()).isEqualTo(Verdict.NO_REGRESSION);
        assertThat(report.baselineMedianNanos()).isEqualTo(107.0);
        assertThat(report.currentMedianNanos()).isEqualTo(107.0);
        assertThat(report.changeRatio()).isCloseTo(0.0, within(1e-9));
        assertThat(report.confidenceNote()).contains("Mann-Whitney").contains("噪声");
    }

    @Test
    void detectsRegressionWhenSignificantlySlowerAndOverThreshold() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));
        framework.check("sort", cycling(series(100, 15)));

        BenchmarkReport report = framework.check("sort", cycling(series(130, 15)));

        assertThat(report.verdict()).isEqualTo(Verdict.REGRESSION);
        assertThat(report.isRegression()).isTrue();
        assertThat(report.benchmarkName()).isEqualTo("sort");
        assertThat(report.changeRatio()).isCloseTo(30.0 / 107.0, within(1e-3));
        assertThat(report.confidenceNote()).contains("超出样本波动范围").contains("被越过");
    }

    @Test
    void significantButTinySlowdownIsFilteredByThreshold() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));
        framework.check("sort", cycling(1000, 1000, 1000, 1000, 1000));

        BenchmarkReport report = framework.check("sort", cycling(1005, 1005, 1005, 1005, 1005));

        assertThat(report.verdict()).isEqualTo(Verdict.NO_REGRESSION);
        assertThat(report.confidenceNote()).contains("未越过");
    }

    @Test
    void andCombinedThresholdsRequireBothConditions() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.combine(
                ThresholdPolicy.Mode.AND,
                ThresholdPolicy.relative(0.10),
                ThresholdPolicy.absolute(java.time.Duration.ofNanos(50))));
        framework.check("sort", cycling(series(1000, 15)));

        // 相对退化 30% 但绝对差只有 30ns < 50ns：AND 不通过
        BenchmarkReport report = framework.check("sort", cycling(series(1030, 15)));

        assertThat(report.verdict()).isEqualTo(Verdict.NO_REGRESSION);
    }

    @Test
    void baselinesAreIsolatedPerEnvironmentTag() {
        framework("machine-a", ThresholdPolicy.relative(0.10))
                .check("sort", cycling(series(100, 15)));

        BenchmarkReport otherEnv = framework("machine-b", ThresholdPolicy.relative(0.10))
                .check("sort", cycling(series(500, 15)));

        assertThat(otherEnv.verdict()).isEqualTo(Verdict.BASELINE_CREATED);
        assertThat(directoryCount(baselineDir)).isEqualTo(2);
    }

    @Test
    void summaryAggregatesStatsAndRegressionCount() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));
        framework.check("fast", cycling(series(100, 15)));
        framework.check("slow", cycling(series(100, 15)));
        framework.check("fast", cycling(series(100, 15)));
        framework.check("slow", cycling(series(200, 15)));

        RunSummary summary = framework.summary();

        assertThat(summary.reports()).hasSize(4);
        assertThat(summary.regressionCount()).isEqualTo(1);
        assertThat(summary.totalSamples()).isEqualTo(60);
        assertThat(summary.totalWarmupDiscarded()).isEqualTo(12);
        assertThat(summary.render()).contains("回归项=1").contains("总样本数=60");
    }

    @Test
    void reportContainsAllRequiredFields() {
        RegressionFramework framework = framework("ci", ThresholdPolicy.relative(0.10));
        framework.check("json-parse", cycling(series(200, 15)));
        BenchmarkReport report = framework.check("json-parse", cycling(series(260, 15)));

        assertThat(report.benchmarkName()).isEqualTo("json-parse");
        assertThat(report.environmentKey()).isNotBlank();
        assertThat(report.baselineMedianNanos()).isEqualTo(207.0);
        assertThat(report.currentMedianNanos()).isEqualTo(267.0);
        assertThat(report.changeRatio()).isGreaterThan(0.25);
        assertThat(report.verdict()).isEqualTo(Verdict.REGRESSION);
        assertThat(report.confidenceNote()).contains("p=").contains("alpha=");
        assertThat(report.baselineSampleCount()).isEqualTo(15);
        assertThat(report.render()).contains("json-parse").contains("REGRESSION");
    }

    private static long propertiesFileCount(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".properties")).count();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long directoryCount(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).count();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
