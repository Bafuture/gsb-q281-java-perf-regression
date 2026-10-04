package com.example.gsb.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Aggregated results of a run: per-benchmark comparisons plus run statistics. */
public final class RegressionReport {

    private final EnvironmentFingerprint environment;
    private final List<ComparisonResult> results;
    private final int totalSamples;
    private final int totalWarmupDiscarded;

    public RegressionReport(EnvironmentFingerprint environment, List<ComparisonResult> results,
                            int totalSamples, int totalWarmupDiscarded) {
        this.environment = environment;
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.totalSamples = totalSamples;
        this.totalWarmupDiscarded = totalWarmupDiscarded;
    }

    public EnvironmentFingerprint environment() {
        return environment;
    }

    public List<ComparisonResult> results() {
        return results;
    }

    /** Number of measured (non-warmup) samples collected across all benchmarks. */
    public int totalSamples() {
        return totalSamples;
    }

    /** Number of warmup iterations discarded across all benchmarks. */
    public int totalWarmupDiscarded() {
        return totalWarmupDiscarded;
    }

    public long regressionCount() {
        return results.stream().filter(r -> r.verdict() == Verdict.REGRESSION).count();
    }

    public long improvementCount() {
        return results.stream().filter(r -> r.verdict() == Verdict.IMPROVEMENT).count();
    }

    public boolean hasRegression() {
        return regressionCount() > 0;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("Performance regression report - ").append(environment).append(System.lineSeparator());
        sb.append("benchmark                 baseline(ns)   current(ns)       change   verdict")
                .append(System.lineSeparator());
        sb.append("------------------------  ------------  ------------  -----------  -------------")
                .append(System.lineSeparator());
        for (ComparisonResult r : results) {
            sb.append(String.format(Locale.ROOT, "%-24s  %12.1f  %12.1f  %11s  %-13s%n",
                    abbreviate(r.benchmarkName()), r.baselineMedianNanos(),
                    r.currentMedianNanos(), r.formatChangePercent(), r.verdict()));
        }
        sb.append(System.lineSeparator());
        for (ComparisonResult r : results) {
            sb.append("  ").append(r.benchmarkName()).append(": ").append(r.confidenceNote())
                    .append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        sb.append("stats: benchmarks=").append(results.size())
                .append(", samples=").append(totalSamples)
                .append(", warmupDiscarded=").append(totalWarmupDiscarded)
                .append(", regressions=").append(regressionCount())
                .append(", improvements=").append(improvementCount())
                .append(System.lineSeparator());
        return sb.toString();
    }

    private static String abbreviate(String name) {
        return name.length() <= 24 ? name : name.substring(0, 21) + "...";
    }
}
