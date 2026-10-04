package com.example.gsb.perf;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a benchmark operation: first {@code warmupIterations} unmeasured
 * iterations (JIT warmup, discarded), then {@code measuredIterations}
 * individually timed iterations.
 */
public final class BenchmarkRunner {

    private final int warmupIterations;
    private final int measuredIterations;

    public BenchmarkRunner(int warmupIterations, int measuredIterations) {
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be >= 0");
        }
        if (measuredIterations < 1) {
            throw new IllegalArgumentException("measuredIterations must be >= 1");
        }
        this.warmupIterations = warmupIterations;
        this.measuredIterations = measuredIterations;
    }

    public int warmupIterations() {
        return warmupIterations;
    }

    public int measuredIterations() {
        return measuredIterations;
    }

    /** Times each measured iteration with {@link System#nanoTime()}. */
    public Samples run(Runnable operation) {
        for (int i = 0; i < warmupIterations; i++) {
            operation.run();
        }
        List<Double> timings = new ArrayList<>(measuredIterations);
        for (int i = 0; i < measuredIterations; i++) {
            long start = System.nanoTime();
            operation.run();
            timings.add((double) (System.nanoTime() - start));
        }
        return Samples.of(timings);
    }
}
