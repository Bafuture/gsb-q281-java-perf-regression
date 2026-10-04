package com.example.gsb.perfreg;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 运行环境标记：操作系统、架构、JVM 版本、CPU 核数、主机名，
 * 以及调用方自定义标签（例如 JVM 参数 {@code -Xmx.../-XX:+UseZGC}）。
 * 基线按环境指纹分目录保存，避免跨机器 / 跨 JVM 参数直接比较。
 */
public record Environment(String osName, String osArch, String javaVersion, String jvmName,
                          String host, int availableProcessors, String tag) {

    public static Environment detect() {
        return detect("");
    }

    public static Environment detect(String tag) {
        return new Environment(
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vm.name", "unknown"),
                resolveHost(),
                Runtime.getRuntime().availableProcessors(),
                tag == null ? "" : tag.trim());
    }

    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    /** 可读的环境标识。 */
    public String key() {
        return osName + " | " + osArch + " | " + javaVersion + " | " + jvmName
                + " | cores=" + availableProcessors + " | host=" + host
                + (tag.isEmpty() ? "" : " | tag=" + tag);
    }

    /** 文件名安全的短指纹（SHA-256 前 16 位），用作基线目录名。 */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
