package com.example.gsb.perf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entry point of the framework. Measures benchmarks, compares against the
 * persisted baseline for the configured environment, establishes a new
 * baseline when none exists, and accumulates a {@link RegressionReport}.
 */
public final class PerfRegression {

    private final BaselineStore store;
    private final EnvironmentFingerprint environment;
    private final BenchmarkRunner runner;
    private final RegressionComparator comparator;
    private final List<ComparisonResult> results = new ArrayList<>();
    private int totalSamples;
    private int totalWarmupDiscarded;

    private PerfRegression(Builder builder) {
        this.store = new BaselineStore(builder.baselineDirectory);
        this.environment = builder.environment;
        this.runner = new BenchmarkRunner(builder.warmupIterations, builder.measuredIterations);
        this.comparator = new RegressionComparator(builder.thresholdPolicy,
                builder.zScore, builder.minSamples);
    }

    public static Builder builder() {
        return new Builder();
    }

    public EnvironmentFingerprint environment() {
        return environment;
    }

    public BaselineStore baselineStore() {
        return store;
    }

    public ThresholdPolicy thresholdPolicy() {
        return comparator.thresholdPolicy();
    }

    /** Measure a runnable benchmark and compare it to the stored baseline. */
    public ComparisonResult benchmark(String name, Runnable operation) {
        Samples samples = runner.run(operation);
        totalWarmupDiscarded += runner.warmupIterations();
        return compare(name, samples);
    }

    /**
     * Compare an externally collected sample set to the stored baseline.
     * If no baseline exists for this benchmark/environment yet, one is
     * persisted from the samples and a baselineEstablished result returned.
     */
    public ComparisonResult compare(String name, Samples currentSamples) {
        totalSamples += currentSamples.size();
        Optional<Baseline> existing = store.find(name, environment);
        if (existing.isEmpty()) {
            Baseline baseline = Baseline.fromSamples(name, environment, currentSamples,
                    runner.warmupIterations());
            store.save(baseline);
            String note = "no prior baseline in " + environment
                    + "; established baseline from n=" + currentSamples.size() + " samples";
            ComparisonResult result = new ComparisonResult(name, baseline.medianNanos(),
                    currentSamples.median(), 0, 0, false, false,
                    Verdict.NO_REGRESSION, note, true);
            results.add(result);
            return result;
        }
        ComparisonResult result = comparator.compare(existing.get(), currentSamples);
        results.add(result);
        return result;
    }

    /** Persist the current samples as the new baseline (e.g. after an intentional update). */
    public void establishBaseline(String name, Samples currentSamples) {
        store.save(Baseline.fromSamples(name, environment, currentSamples,
                runner.warmupIterations()));
    }

    public RegressionReport report() {
        return new RegressionReport(environment, results, totalSamples, totalWarmupDiscarded);
    }

    public static final class Builder {
        private Path baselineDirectory;
        private EnvironmentFingerprint environment = EnvironmentFingerprint.detect();
        private int warmupIterations = 5;
        private int measuredIterations = 20;
        private ThresholdPolicy thresholdPolicy = ThresholdPolicy.none();
        private double zScore = RegressionComparator.CONFIDENCE_95;
        private int minSamples = 3;

        public Builder baselineDirectory(Path directory) {
            this.baselineDirectory = directory;
            return this;
        }

        public Builder environment(EnvironmentFingerprint environment) {
            this.environment = environment;
            return this;
        }

        public Builder environmentTag(String tag) {
            this.environment = EnvironmentFingerprint.detect(tag);
            return this;
        }

        public Builder warmupIterations(int warmupIterations) {
            this.warmupIterations = warmupIterations;
            return this;
        }

        public Builder measuredIterations(int measuredIterations) {
            this.measuredIterations = measuredIterations;
            return this;
        }

        public Builder thresholds(ThresholdPolicy thresholdPolicy) {
            this.thresholdPolicy = thresholdPolicy;
            return this;
        }

        public Builder confidenceZScore(double zScore) {
            this.zScore = zScore;
            return this;
        }

        public Builder minSamples(int minSamples) {
            this.minSamples = minSamples;
            return this;
        }

        public PerfRegression build() {
            if (baselineDirectory == null) {
                throw new IllegalStateException("baselineDirectory must be set");
            }
            return new PerfRegression(this);
        }
    }
}
