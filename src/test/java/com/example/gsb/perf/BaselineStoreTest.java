package com.example.gsb.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineStoreTest {

    @TempDir
    Path dir;

    private static EnvironmentFingerprint env(String tag, String jvmArgs) {
        return new EnvironmentFingerprint(tag, "Linux", "amd64", "17.0.8",
                "OpenJDK 64-Bit Server VM", jvmArgs);
    }

    @Test
    void persistsAndReloadsBaseline() {
        BaselineStore store = new BaselineStore(dir);
        EnvironmentFingerprint env = env("ci", "-Xmx1g");
        Samples samples = Samples.of(List.of(90.0, 100.0, 110.0, 100.0, 105.0));
        Baseline baseline = Baseline.fromSamples("sort-bench", env, samples, 3);
        store.save(baseline);

        Baseline loaded = store.find("sort-bench", env).orElseThrow();
        assertThat(loaded.benchmarkName()).isEqualTo("sort-bench");
        assertThat(loaded.sampleCount()).isEqualTo(5);
        assertThat(loaded.warmupDiscarded()).isEqualTo(3);
        assertThat(loaded.medianNanos()).isEqualTo(100.0);
        assertThat(loaded.p90Nanos()).isEqualTo(samples.percentile(90));
        assertThat(loaded.p95Nanos()).isEqualTo(samples.percentile(95));
        assertThat(loaded.p99Nanos()).isEqualTo(samples.percentile(99));
        assertThat(loaded.robustSigma()).isEqualTo(samples.robustSigma());
        assertThat(loaded.environment()).isEqualTo(env);
    }

    @Test
    void isolatesBaselinesPerEnvironment() {
        BaselineStore store = new BaselineStore(dir);
        EnvironmentFingerprint machineA = env("machine-a", "-Xmx1g");
        EnvironmentFingerprint machineB = env("machine-b", "-Xmx1g");
        EnvironmentFingerprint otherJvmArgs = env("machine-a", "-Xmx4g -XX:+UseZGC");

        store.save(Baseline.fromSamples("bench", machineA,
                Samples.of(List.of(100.0, 100.0, 100.0)), 1));
        store.save(Baseline.fromSamples("bench", machineB,
                Samples.of(List.of(500.0, 500.0, 500.0)), 1));

        assertThat(store.find("bench", machineA).orElseThrow().medianNanos()).isEqualTo(100.0);
        assertThat(store.find("bench", machineB).orElseThrow().medianNanos()).isEqualTo(500.0);
        assertThat(store.find("bench", otherJvmArgs)).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownBenchmark() {
        BaselineStore store = new BaselineStore(dir);
        assertThat(store.find("missing", env("ci", ""))).isEmpty();
    }

    @Test
    void sanitizesBenchmarkNamesForFileSystem() {
        BaselineStore store = new BaselineStore(dir);
        EnvironmentFingerprint env = env("ci", "");
        store.save(Baseline.fromSamples("a/b c:d", env, Samples.of(List.of(1.0, 2.0, 3.0)), 0));
        assertThat(store.find("a/b c:d", env)).isPresent();
    }
}
