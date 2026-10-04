package com.example.gsb.perfreg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThresholdPolicyTest {

    @Test
    void absoluteThreshold() {
        ThresholdPolicy policy = ThresholdPolicy.absolute(Duration.ofNanos(50));

        assertThat(policy.exceeded(100, 160)).isTrue();
        assertThat(policy.exceeded(100, 140)).isFalse();
        assertThat(policy.exceeded(100, 90)).isFalse();
    }

    @Test
    void relativeThreshold() {
        ThresholdPolicy policy = ThresholdPolicy.relative(0.10);

        assertThat(policy.exceeded(100, 111)).isTrue();
        assertThat(policy.exceeded(100, 109)).isFalse();
        assertThat(policy.exceeded(100, 80)).isFalse();
    }

    @Test
    void combineWithAnd() {
        ThresholdPolicy policy = ThresholdPolicy.combine(
                ThresholdPolicy.Mode.AND,
                ThresholdPolicy.relative(0.10),
                ThresholdPolicy.absolute(Duration.ofNanos(50)));

        // 30% 退化且绝对差 30ns：只满足相对条件，AND 不通过
        assertThat(policy.exceeded(100, 130)).isFalse();
        // 60% 退化且绝对差 60ns：两者都满足
        assertThat(policy.exceeded(100, 160)).isTrue();
    }

    @Test
    void combineWithOr() {
        ThresholdPolicy policy = ThresholdPolicy.combine(
                ThresholdPolicy.Mode.OR,
                ThresholdPolicy.relative(0.50),
                ThresholdPolicy.absolute(Duration.ofNanos(50)));

        // 30% 退化（不到 50%），但绝对差 30ns 也不到 50ns
        assertThat(policy.exceeded(100, 130)).isFalse();
        // 绝对差 60ns，虽然相对 6% 不到 50%，OR 仍触发
        assertThat(policy.exceeded(1000, 1060)).isTrue();
    }

    @Test
    void significanceOnlyAlwaysPassesThresholdGate() {
        assertThat(ThresholdPolicy.significanceOnly().exceeded(100, 101)).isTrue();
    }
}
