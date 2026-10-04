package com.example.gsb.perf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * File based baseline persistence. Each (benchmark, environment) pair maps to
 * a separate file: {@code <safe-benchmark-name>__<env-hash>.properties}.
 * Different environments therefore cannot read or overwrite each other.
 */
public final class BaselineStore {

    private final Path directory;

    public BaselineStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create baseline directory: " + directory, e);
        }
    }

    public Path directory() {
        return directory;
    }

    public void save(Baseline baseline) {
        Properties props = new Properties();
        props.setProperty("benchmarkName", baseline.benchmarkName());
        props.setProperty("sampleCount", Integer.toString(baseline.sampleCount()));
        props.setProperty("warmupDiscarded", Integer.toString(baseline.warmupDiscarded()));
        props.setProperty("medianNanos", Double.toString(baseline.medianNanos()));
        props.setProperty("p90Nanos", Double.toString(baseline.p90Nanos()));
        props.setProperty("p95Nanos", Double.toString(baseline.p95Nanos()));
        props.setProperty("p99Nanos", Double.toString(baseline.p99Nanos()));
        props.setProperty("robustSigma", Double.toString(baseline.robustSigma()));
        props.setProperty("createdAtEpochMillis", Long.toString(baseline.createdAtEpochMillis()));
        EnvironmentFingerprint env = baseline.environment();
        props.setProperty("env.tag", env.tag());
        props.setProperty("env.osName", env.osName());
        props.setProperty("env.osArch", env.osArch());
        props.setProperty("env.javaVersion", env.javaVersion());
        props.setProperty("env.vmName", env.vmName());
        props.setProperty("env.jvmArgs", env.jvmArgs());
        try {
            try (java.io.OutputStream out = Files.newOutputStream(fileFor(baseline.benchmarkName(), env))) {
                props.store(out, "perf-regression baseline");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot save baseline", e);
        }
    }

    public java.util.Optional<Baseline> find(String benchmarkName, EnvironmentFingerprint env) {
        Path file = fileFor(benchmarkName, env);
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        Properties props = new Properties();
        try (java.io.InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load baseline: " + file, e);
        }
        EnvironmentFingerprint loadedEnv = new EnvironmentFingerprint(
                props.getProperty("env.tag", ""),
                props.getProperty("env.osName", ""),
                props.getProperty("env.osArch", ""),
                props.getProperty("env.javaVersion", ""),
                props.getProperty("env.vmName", ""),
                props.getProperty("env.jvmArgs", ""));
        if (!loadedEnv.equals(env)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Baseline(
                benchmarkName,
                loadedEnv,
                Integer.parseInt(props.getProperty("sampleCount")),
                Integer.parseInt(props.getProperty("warmupDiscarded")),
                Double.parseDouble(props.getProperty("medianNanos")),
                Double.parseDouble(props.getProperty("p90Nanos")),
                Double.parseDouble(props.getProperty("p95Nanos")),
                Double.parseDouble(props.getProperty("p99Nanos")),
                Double.parseDouble(props.getProperty("robustSigma")),
                Long.parseLong(props.getProperty("createdAtEpochMillis"))));
    }

    private Path fileFor(String benchmarkName, EnvironmentFingerprint env) {
        String safe = benchmarkName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return directory.resolve(safe + "__" + env.hash() + ".properties");
    }
}
