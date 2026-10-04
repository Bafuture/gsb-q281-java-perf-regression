package com.example.gsb.perfreg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class SamplerTest {

    private static LongSupplier fromValues(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
    }

    @Test
    void discardsWarmupIterations() {
        Sampler sampler = new Sampler(SamplingConfig.of(3, 5));
        SampleStats stats = sampler.sample(fromValues(100, 200, 300, 10, 20, 30, 40, 50));

        assertThat(stats.warmupDiscarded()).isEqualTo(3);
        assertThat(stats.sampleCount()).isEqualTo(5);
        assertThat(stats.samples()).containsExactly(10, 20, 30, 40, 50);
    }

    @Test
    void computesMedianAndPercentiles() {
        Sampler sampler = new Sampler(SamplingConfig.of(0, 5));
        SampleStats stats = sampler.sample(fromValues(50, 10, 40, 20, 30));

        assertThat(stats.median()).isEqualTo(30.0);
        assertThat(stats.p90()).isCloseTo(46.0, within(1e-9));
        assertThat(stats.min()).isEqualTo(10.0);
        assertThat(stats.max()).isEqualTo(50.0);
    }

    @Test
    void interpolatesPercentilesForEvenSampleCount() {
        Sampler sampler = new Sampler(SamplingConfig.of(0, 4));
        SampleStats stats = sampler.sample(fromValues(10, 20, 30, 40));

        assertThat(stats.median()).isCloseTo(25.0, within(1e-9));
        assertThat(stats.p95()).isCloseTo(38.5, within(1e-9));
        assertThat(stats.p99()).isCloseTo(39.7, within(1e-9));
    }

    @Test
    void timesRunnableIterations() {
        Sampler sampler = new Sampler(SamplingConfig.of(2, 3));
        AtomicInteger calls = new AtomicInteger();
        SampleStats stats = sampler.sample(calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(5);
        assertThat(stats.sampleCount()).isEqualTo(3);
        assertThat(stats.warmupDiscarded()).isEqualTo(2);
    }
}
