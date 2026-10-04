package com.example.gsb.perfreg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineStoreTest {

    @TempDir
    Path dir;

    private static Environment env(String tag) {
        return new Environment("Linux", "amd64", "17.0.1", "OpenJDK", "host-a", 8, tag);
    }

    @Test
    void persistsAndLoadsBaseline() {
        BaselineStore store = new BaselineStore(dir);
        Environment environment = env("default");
        SampleStats stats = new SampleStats(new long[]{30, 10, 20, 50, 40}, 3);
        store.save(Baseline.of("sort-benchmark", environment, stats), environment);

        Baseline loaded = store.load("sort-benchmark", environment).orElseThrow();

        assertThat(loaded.benchmarkName()).isEqualTo("sort-benchmark");
        assertThat(loaded.environmentKey()).isEqualTo(environment.key());
        assertThat(loaded.stats().samples()).containsExactly(10, 20, 30, 40, 50);
        assertThat(loaded.stats().median()).isEqualTo(30.0);
        assertThat(loaded.stats().warmupDiscarded()).isEqualTo(3);
    }

    @Test
    void isolatesBaselinesByEnvironment() {
        BaselineStore store = new BaselineStore(dir);
        Environment machineA = env("machine-a");
        Environment machineB = new Environment("Mac OS X", "aarch64", "21", "OpenJDK", "host-b", 12, "machine-b");

        store.save(Baseline.of("bench", machineA, new SampleStats(new long[]{10, 20, 30}, 0)), machineA);

        assertThat(store.load("bench", machineA)).isPresent();
        assertThat(store.load("bench", machineB)).isEmpty();
        assertThat(store.fileFor("bench", machineA)).isNotEqualTo(store.fileFor("bench", machineB));
    }

    @Test
    void isolatesBaselinesByCustomTag() {
        BaselineStore store = new BaselineStore(dir);
        Environment g1 = env("-XX:+UseG1GC");
        Environment zgc = env("-XX:+UseZGC");

        store.save(Baseline.of("bench", g1, new SampleStats(new long[]{10, 20, 30}, 0)), g1);

        assertThat(store.load("bench", zgc)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoBaselineExists() {
        assertThat(new BaselineStore(dir).load("missing", env("x"))).isEmpty();
    }
}
