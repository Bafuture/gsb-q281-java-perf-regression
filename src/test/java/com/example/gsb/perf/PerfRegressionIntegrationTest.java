package com.example.gsb.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerfRegressionIntegrationTest {

    @TempDir
    Path dir;

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

    private PerfRegression newFramework(String tag) {
        return PerfRegression.builder()
                .baselineDirectory(dir)
                .environmentTag(tag)
                .warmupIterations(3)
                .measuredIterations(21)
                .thresholds(ThresholdPolicy.relative(0.01))
                .build();
    }

    @Test
    void firstRunEstablishesBaselineSecondRunCompares() {
        PerfRegression framework = newFramework("it");
        ComparisonResult first = framework.compare("json-parse", spread(1000));
        assertThat(first.baselineEstablished()).isTrue();
        assertThat(first.verdict()).isEqualTo(Verdict.NO_REGRESSION);
        assertThat(framework.baselineStore()
                .find("json-parse", framework.environment())).isPresent();

        ComparisonResult second = framework.compare("json-parse", spread(1050));
        assertThat(second.baselineEstablished()).isFalse();
        assertThat(second.verdict()).isEqualTo(Verdict.REGRESSION);
        assertThat(second.baselineMedianNanos()).isEqualTo(1000.0);
        assertThat(second.currentMedianNanos()).isEqualTo(1050.0);
        assertThat(second.changeRatio()).isEqualTo(0.05);
    }

    @Test
    void environmentsDoNotShareBaselines() {
        PerfRegression machineA = newFramework("machine-a");
        PerfRegression machineB = newFramework("machine-b");
        machineA.compare("bench", spread(1000));

        // machine-b has no baseline yet even though machine-a stored one
        ComparisonResult onB = machineB.compare("bench", spread(5000));
        assertThat(onB.baselineEstablished()).isTrue();
        assertThat(onB.verdict()).isEqualTo(Verdict.NO_REGRESSION);

        // machine-a still compares against its own baseline
        ComparisonResult onA = machineA.compare("bench", spread(1005));
        assertThat(onA.baselineEstablished()).isFalse();
        assertThat(onA.verdict()).isEqualTo(Verdict.NO_REGRESSION);
    }

    @Test
    void reportContainsValuesVerdictsAndStatistics() {
        PerfRegression framework = newFramework("report");
        framework.compare("fast-bench", spread(1000));
        framework.compare("fast-bench", spread(1100)); // regression
        framework.compare("slow-bench", spread(2000));
        framework.compare("slow-bench", spread(2005)); // jitter, no regression

        RegressionReport report = framework.report();
        assertThat(report.results()).hasSize(4);
        assertThat(report.totalSamples()).isEqualTo(4 * 21);
        assertThat(report.totalWarmupDiscarded()).isEqualTo(0); // compare() path: no warmup run
        assertThat(report.regressionCount()).isEqualTo(1);
        assertThat(report.hasRegression()).isTrue();

        String text = report.render();
        assertThat(text)
                .contains("fast-bench")
                .contains("slow-bench")
                .contains("REGRESSION")
                .contains("NO_REGRESSION")
                .contains("+10.00%")
                .contains("noise band")
                .contains("samples=84")
                .contains("warmupDiscarded=0")
                .contains("regressions=1");
    }

    @Test
    void benchmarkMethodRunsWarmupAndCountsStatistics() {
        PerfRegression framework = newFramework("bench");
        framework.benchmark("real-run", () -> { });
        RegressionReport report = framework.report();
        assertThat(report.totalSamples()).isEqualTo(21);
        assertThat(report.totalWarmupDiscarded()).isEqualTo(3);
        assertThat(report.results()).hasSize(1);
        assertThat(report.results().get(0).baselineEstablished()).isTrue();
    }
}
