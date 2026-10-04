package com.example.gsb.perf;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Identifies the execution environment of a run: OS, architecture, JDK and
 * JVM input arguments, optionally plus a user supplied tag (e.g. a machine
 * name or CI profile). Baselines are keyed by this fingerprint so results
 * from different machines or JVM configurations are never compared.
 */
public final class EnvironmentFingerprint {

    private final String tag;
    private final String osName;
    private final String osArch;
    private final String javaVersion;
    private final String vmName;
    private final String jvmArgs;

    public EnvironmentFingerprint(String tag, String osName, String osArch,
                                  String javaVersion, String vmName, String jvmArgs) {
        this.tag = tag == null ? "" : tag;
        this.osName = osName;
        this.osArch = osArch;
        this.javaVersion = javaVersion;
        this.vmName = vmName;
        this.jvmArgs = jvmArgs;
    }

    public static EnvironmentFingerprint detect() {
        return detect("");
    }

    public static EnvironmentFingerprint detect(String tag) {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String jvmArgs = args.stream().sorted().collect(Collectors.joining(" "));
        return new EnvironmentFingerprint(
                tag,
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vm.name", "unknown"),
                jvmArgs);
    }

    public String tag() {
        return tag;
    }

    public String osName() {
        return osName;
    }

    public String osArch() {
        return osArch;
    }

    public String javaVersion() {
        return javaVersion;
    }

    public String vmName() {
        return vmName;
    }

    public String jvmArgs() {
        return jvmArgs;
    }

    /** Short stable hash used as part of baseline file names. */
    public String hash() {
        String material = tag + '|' + osName + '|' + osArch + '|' + javaVersion
                + '|' + vmName + '|' + jvmArgs;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EnvironmentFingerprint other)) {
            return false;
        }
        return tag.equals(other.tag) && osName.equals(other.osName)
                && osArch.equals(other.osArch) && javaVersion.equals(other.javaVersion)
                && vmName.equals(other.vmName) && jvmArgs.equals(other.jvmArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, osName, osArch, javaVersion, vmName, jvmArgs);
    }

    @Override
    public String toString() {
        return "env[" + (tag.isEmpty() ? "default" : tag) + ':' + osName + '/'
                + osArch + "/jdk" + javaVersion + '/' + hash() + ']';
    }
}
