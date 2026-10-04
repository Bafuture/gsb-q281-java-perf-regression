package com.example.gsb.perfreg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

/**
 * 基线持久化：按“环境指纹 / 基准名.properties”存放，天然实现环境隔离。
 * 文件内保留全部原始样本，供后续显著性检验使用。
 */
public final class BaselineStore {

    private final Path rootDir;

    public BaselineStore(Path rootDir) {
        this.rootDir = rootDir;
    }

    public Optional<Baseline> load(String benchmarkName, Environment environment) {
        Path file = fileFor(benchmarkName, environment);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load baseline " + file, e);
        }
        String[] parts = props.getProperty("samples").split(",");
        long[] samples = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            samples[i] = Long.parseLong(parts[i].trim());
        }
        int warmupDiscarded = Integer.parseInt(props.getProperty("warmupDiscarded", "0"));
        return Optional.of(new Baseline(
                props.getProperty("benchmarkName"),
                props.getProperty("environmentKey"),
                Instant.parse(props.getProperty("createdAt")),
                new SampleStats(samples, warmupDiscarded)));
    }

    public void save(Baseline baseline, Environment environment) {
        Path file = fileFor(baseline.benchmarkName(), environment);
        try {
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("benchmarkName", baseline.benchmarkName());
            props.setProperty("environmentKey", baseline.environmentKey());
            props.setProperty("createdAt", baseline.createdAt().toString());
            props.setProperty("warmupDiscarded", String.valueOf(baseline.stats().warmupDiscarded()));
            props.setProperty("medianNanos", String.valueOf(baseline.stats().median()));
            props.setProperty("p90Nanos", String.valueOf(baseline.stats().p90()));
            props.setProperty("p95Nanos", String.valueOf(baseline.stats().p95()));
            props.setProperty("p99Nanos", String.valueOf(baseline.stats().p99()));
            StringBuilder csv = new StringBuilder();
            for (long sample : baseline.stats().samples()) {
                if (csv.length() > 0) {
                    csv.append(',');
                }
                csv.append(sample);
            }
            props.setProperty("samples", csv.toString());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "perf-regression baseline");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save baseline " + file, e);
        }
    }

    Path fileFor(String benchmarkName, Environment environment) {
        return rootDir.resolve(environment.fingerprint()).resolve(sanitize(benchmarkName) + ".properties");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
