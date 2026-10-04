package com.example.gsb.perfreg;

import java.util.Arrays;

/**
 * Mann-Whitney U 检验（Wilcoxon 秩和检验），非参数两样本检验，
 * 不假设耗时服从正态分布，适合基准耗时这类偏态数据。
 *
 * <p>判定方法：将基线样本与当前样本混合后按耗时排序求秩（相同值取平均秩），
 * 计算当前组秩和对应的 U 统计量。U 越大说明当前样本越倾向于比基线大（更慢）。
 * 在样本量足够时用正态近似（含连续修正与结对方差修正）求单侧 p 值，
 * p &lt; alpha（默认 0.05）即判定“显著变慢”，从而把正常抖动与真实回归区分开。
 */
public final class MannWhitneyUTest {

    private MannWhitneyUTest() {
    }

    /**
     * @param baseline 基线样本
     * @param current  当前样本
     * @return 检验结果（单侧方向：current 是否显著大于 baseline）
     */
    public static SignificanceResult test(long[] baseline, long[] current) {
        int n1 = baseline.length;
        int n2 = current.length;
        if (n1 < 2 || n2 < 2) {
            throw new IllegalArgumentException("each group needs at least 2 samples");
        }

        long[] pooled = new long[n1 + n2];
        System.arraycopy(baseline, 0, pooled, 0, n1);
        System.arraycopy(current, 0, pooled, n1, n2);
        long[] sorted = pooled.clone();
        Arrays.sort(sorted);

        double rankSumCurrent = 0.0;
        for (long value : current) {
            rankSumCurrent += averageRank(sorted, value);
        }

        double u2 = rankSumCurrent - n2 * (n2 + 1) / 2.0;

        double mean = (double) n1 * n2 / 2.0;
        double tieCorrection = tieCorrection(sorted, n1 + n2);
        double variance = (double) n1 * n2 / 12.0
                * ((n1 + n2 + 1) - tieCorrection / ((double) (n1 + n2) * (n1 + n2 - 1)));
        double sd = Math.sqrt(variance);

        double z = sd == 0 ? 0 : (u2 - mean - 0.5) / sd;
        double p = 1.0 - normalCdf(z);
        return new SignificanceResult(u2, z, Math.max(0.0, Math.min(1.0, p)));
    }

    private static double averageRank(long[] sorted, long value) {
        int first = lowerBound(sorted, value);
        int last = upperBound(sorted, value);
        int count = last - first;
        if (count == 0) {
            throw new IllegalStateException("value not found in pooled samples");
        }
        double rankSum = 0.0;
        for (int i = first; i < last; i++) {
            rankSum += i + 1;
        }
        return rankSum / count;
    }

    private static int lowerBound(long[] sorted, long value) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int upperBound(long[] sorted, long value) {
        int lo = 0;
        int hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** 结对修正项：sum(t^3 - t)，t 为每个相同值的出现次数。 */
    private static long tieCorrection(long[] sorted, int n) {
        long correction = 0;
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n && sorted[j] == sorted[i]) {
                j++;
            }
            long t = j - i;
            if (t > 1) {
                correction += t * t * t - t;
            }
            i = j;
        }
        return correction;
    }

    /** 标准正态分布 CDF（通过 erf 的 Abramowitz-Stegun 7.1.26 近似）。 */
    static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-ax * ax);
        return sign * y;
    }
}
