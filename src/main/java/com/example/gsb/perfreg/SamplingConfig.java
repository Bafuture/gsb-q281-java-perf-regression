package com.example.gsb.perfreg;

/** 采样参数：预热次数（结果被丢弃）与正式测量次数。 */
public record SamplingConfig(int warmupIterations, int measuredIterations) {

    public SamplingConfig {
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be >= 0");
        }
        if (measuredIterations < 2) {
            throw new IllegalArgumentException("measuredIterations must be >= 2");
        }
    }

    public static SamplingConfig of(int warmupIterations, int measuredIterations) {
        return new SamplingConfig(warmupIterations, measuredIterations);
    }
}
