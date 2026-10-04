package com.example.gsb.perf;

import java.util.Locale;

/** Outcome of comparing one current sample set against its baseline. */
public final class ComparisonResult {

    private final String benchmarkName;
    private final double baselineMedianNanos;
    private final double currentMedianNanos;
    private final double deltaNanos;
    private final double changeRatio;
    private final boolean significant;
    private final boolean thresholdBreached;
    private final Verdict verdict;
    private final String confidenceNote;
    private final boolean baselineEstablished;

    public ComparisonResult(String benchmarkName, double baselineMedianNanos,
                            double currentMedianNanos, double deltaNanos, double changeRatio,
                            boolean significant, boolean thresholdBreached,
                            Verdict verdict, String confidenceNote,
                            boolean baselineEstablished) {
        this.benchmarkName = benchmarkName;
        this.baselineMedianNanos = baselineMedianNanos;
        this.currentMedianNanos = currentMedianNanos;
        this.deltaNanos = deltaNanos;
        this.changeRatio = changeRatio;
        this.significant = significant;
        this.thresholdBreached = thresholdBreached;
        this.verdict = verdict;
        this.confidenceNote = confidenceNote;
        this.baselineEstablished = baselineEstablished;
    }

    public String benchmarkName() {
        return benchmarkName;
    }

    public double baselineMedianNanos() {
        return baselineMedianNanos;
    }

    public double currentMedianNanos() {
        return currentMedianNanos;
    }

    public double deltaNanos() {
        return deltaNanos;
    }

    public double changeRatio() {
        return changeRatio;
    }

    public boolean significant() {
        return significant;
    }

    public boolean thresholdBreached() {
        return thresholdBreached;
    }

    public Verdict verdict() {
        return verdict;
    }

    public String confidenceNote() {
        return confidenceNote;
    }

    public boolean baselineEstablished() {
        return baselineEstablished;
    }

    public String formatChangePercent() {
        return String.format(Locale.ROOT, "%+.2f%%", changeRatio * 100);
    }
}
