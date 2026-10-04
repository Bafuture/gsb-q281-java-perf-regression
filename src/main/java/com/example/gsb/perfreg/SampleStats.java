package com.example.gsb.perfreg;

import java.util.Arrays;

/**
 * 一次基准采样的统计结果：保留预热后的全部原始样本（用于显著性检验），
 * 并提供中位数与任意分位值（线性插值法，与 numpy 默认 R7 方式一致）。
 */
public final class SampleStats {

    private final long[] sortedSamples;
    private final int warmupDiscarded;

    public SampleStats(long[] measuredSamples, int warmupDiscarded) {
        this.sortedSamples = measuredSamples.clone();
        Arrays.sort(this.sortedSamples);
        this.warmupDiscarded = warmupDiscarded;
    }

    public int sampleCount() {
        return sortedSamples.length;
    }

    public int warmupDiscarded() {
        return warmupDiscarded;
    }

    /** 预热后的原始样本（升序）。 */
    public long[] samples() {
        return sortedSamples.clone();
    }

    public double median() {
        return percentile(50.0);
    }

    public double p90() {
        return percentile(90.0);
    }

    public double p95() {
        return percentile(95.0);
    }

    public double p99() {
        return percentile(99.0);
    }

    public double min() {
        return sortedSamples.length == 0 ? Double.NaN : sortedSamples[0];
    }

    public double max() {
        return sortedSamples.length == 0 ? Double.NaN : sortedSamples[sortedSamples.length - 1];
    }

    /**
     * 取第 p 百分位（p 取值 0~100），使用线性插值：
     * rank = p/100 * (n-1)，再在相邻样本间插值。
     */
    public double percentile(double p) {
        if (sortedSamples.length == 0) {
            throw new IllegalStateException("no samples");
        }
        if (sortedSamples.length == 1) {
            return sortedSamples[0];
        }
        double rank = Math.max(0.0, Math.min(100.0, p)) / 100.0 * (sortedSamples.length - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return sortedSamples[low];
        }
        double fraction = rank - low;
        return sortedSamples[low] + fraction * (sortedSamples[high] - sortedSamples[low]);
    }
}
