package com.example.gsb.perfreg;

import java.time.Instant;

/** 一条持久化基线：基准名、所属环境、创建时间与采样统计。 */
public record Baseline(String benchmarkName, String environmentKey, Instant createdAt,
                       SampleStats stats) {

    public static Baseline of(String benchmarkName, Environment environment, SampleStats stats) {
        return new Baseline(benchmarkName, environment.key(), Instant.now(), stats);
    }
}
