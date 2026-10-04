package com.example.gsb.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BenchmarkRunnerTest {

    @Test
    void discardsWarmupAndKeepsExactlyMeasuredIterations() {
        BenchmarkRunner runner = new BenchmarkRunner(4, 7);
        AtomicInteger invocations = new AtomicInteger();
        Samples samples = runner.run(invocations::incrementAndGet);
        assertThat(invocations.get()).isEqualTo(11);
        assertThat(samples.size()).isEqualTo(7);
        assertThat(runner.warmupIterations()).isEqualTo(4);
        assertThat(runner.measuredIterations()).isEqualTo(7);
    }

    @Test
    void recordsPositiveTimings() {
        BenchmarkRunner runner = new BenchmarkRunner(1, 5);
        Samples samples = runner.run(() -> {
            long sum = 0;
            for (int i = 0; i < 1000; i++) {
                sum += i;
            }
            if (sum < 0) {
                throw new IllegalStateException();
            }
        });
        assertThat(samples.values()).allMatch(v -> v >= 0);
        assertThat(samples.median()).isGreaterThanOrEqualTo(0);
    }
}
