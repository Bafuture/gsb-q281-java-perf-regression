package com.example.gsb.perfreg;

import java.util.function.LongSupplier;

/**
 * 基准采样器。先执行若干次预热（JIT 预热、缓存预热等，结果丢弃），
 * 再执行正式测量并保留每次迭代的耗时（纳秒）。
 *
 * <p>为便于测试，测量对象既可以是一段 {@link Runnable}（用 {@code System.nanoTime()} 计时），
 * 也可以直接提供每次迭代耗时的 {@link LongSupplier}（测试时可注入确定性样本）。
 */
public final class Sampler {

    private final SamplingConfig config;

    public Sampler(SamplingConfig config) {
        this.config = config;
    }

    /** 对一段代码计时采样，返回每次迭代耗时（纳秒）。 */
    public SampleStats sample(Runnable benchmark) {
        return sample(() -> {
            long start = System.nanoTime();
            benchmark.run();
            return System.nanoTime() - start;
        });
    }

    /** 以“每次迭代耗时（纳秒）”为数据源进行采样。 */
    public SampleStats sample(LongSupplier iterationNanos) {
        for (int i = 0; i < config.warmupIterations(); i++) {
            iterationNanos.getAsLong();
        }
        long[] measured = new long[config.measuredIterations()];
        for (int i = 0; i < config.measuredIterations(); i++) {
            measured[i] = iterationNanos.getAsLong();
        }
        return new SampleStats(measured, config.warmupIterations());
    }
}
