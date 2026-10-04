package com.example.gsb.perf;

/**
 * Persisted reference distribution for one benchmark in one environment.
 */
public final class Baseline {

    private final String benchmarkName;
    private final EnvironmentFingerprint environment;
    private final int sampleCount;
    private final int warmupDiscarded;
    private final double medianNanos;
    private final double p90Nanos;
    private final double p95Nanos;
    private final double p99Nanos;
    private final double robustSigma;
    private final long createdAtEpochMillis;

    public Baseline(String benchmarkName, EnvironmentFingerprint environment,
                    int sampleCount, int warmupDiscarded,
                    double medianNanos, double p90Nanos, double p95Nanos,
                    double p99Nanos, double robustSigma, long createdAtEpochMillis) {
        this.benchmarkName = benchmarkName;
        this.environment = environment;
        this.sampleCount = sampleCount;
        this.warmupDiscarded = warmupDiscarded;
        this.medianNanos = medianNanos;
        this.p90Nanos = p90Nanos;
        this.p95Nanos = p95Nanos;
        this.p99Nanos = p99Nanos;
        this.robustSigma = robustSigma;
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public static Baseline fromSamples(String benchmarkName, EnvironmentFingerprint environment,
                                       Samples samples, int warmupDiscarded) {
        return new Baseline(benchmarkName, environment, samples.size(), warmupDiscarded,
                samples.median(), samples.percentile(90), samples.percentile(95),
                samples.percentile(99), samples.robustSigma(), System.currentTimeMillis());
    }

    public String benchmarkName() {
        return benchmarkName;
    }

    public EnvironmentFingerprint environment() {
        return environment;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public int warmupDiscarded() {
        return warmupDiscarded;
    }

    public double medianNanos() {
        return medianNanos;
    }

    public double p90Nanos() {
        return p90Nanos;
    }

    public double p95Nanos() {
        return p95Nanos;
    }

    public double p99Nanos() {
        return p99Nanos;
    }

    public double robustSigma() {
        return robustSigma;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }
}
