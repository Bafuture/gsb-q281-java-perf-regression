package com.example.gsb.perfreg;

/**
 * 显著性检验结果。
 *
 * @param u               当前样本组的 Mann-Whitney U 统计量
 * @param z               带连续修正与结对修正的正态近似 z 值
 * @param pValueOneSided  单侧 p 值（备择假设：当前样本显著慢于基线）
 */
public record SignificanceResult(double u, double z, double pValueOneSided) {

    /** 当 p 值小于显著性水平 alpha 时，认为差异超出了样本波动范围。 */
    public boolean isSignificant(double alpha) {
        return pValueOneSided < alpha;
    }
}
