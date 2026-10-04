package com.example.gsb.perfreg;

/**
 * 单个基准的对比报告：基准名、基线值、当前值、变化率、判定结果与置信说明，
 * 以及样本数 / 预热丢弃数等统计信息。
 */
public record BenchmarkReport(
        String benchmarkName,
        String environmentKey,
        Verdict verdict,
        double baselineMedianNanos,
        double currentMedianNanos,
        double changeRatio,
        int baselineSampleCount,
        int currentSampleCount,
        int warmupDiscarded,
        String confidenceNote) {

    public boolean isRegression() {
        return verdict == Verdict.REGRESSION;
    }

    public String render() {
        String change = verdict == Verdict.BASELINE_CREATED
                ? "n/a"
                : String.format("%+.2f%%", changeRatio * 100);
        return String.format(
                "%-28s baseline=%.0fns current=%.0fns change=%s verdict=%s%n  %s",
                benchmarkName, baselineMedianNanos, currentMedianNanos, change, verdict, confidenceNote);
    }
}
