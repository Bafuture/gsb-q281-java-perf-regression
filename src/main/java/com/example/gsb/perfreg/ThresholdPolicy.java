package com.example.gsb.perfreg;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 阈值策略：绝对耗时阈值与相对退化比例，可用 AND / OR 组合。
 * 显著性检验负责排除噪声，阈值负责过滤“统计显著但业务上无所谓”的微小差异。
 */
public final class ThresholdPolicy {

    public enum Mode {AND, OR}

    private record Rule(String description, java.util.function.BiPredicate<Double, Double> test) {
        boolean exceeded(double baselineNanos, double currentNanos) {
            return test.test(baselineNanos, currentNanos);
        }
    }

    private final List<Rule> rules;
    private final Mode mode;

    private ThresholdPolicy(List<Rule> rules, Mode mode) {
        this.rules = rules;
        this.mode = mode;
    }

    /** 不做阈值过滤，只按统计显著性判定。 */
    public static ThresholdPolicy significanceOnly() {
        return new ThresholdPolicy(List.of(), Mode.OR);
    }

    /** 绝对阈值：当前中位数比基线中位数慢超过该时长即触发。 */
    public static ThresholdPolicy absolute(Duration maxSlowdown) {
        double nanos = maxSlowdown.toNanos();
        return new ThresholdPolicy(List.of(new Rule(
                "绝对阈值 " + maxSlowdown.toMillis() + "ms",
                (base, cur) -> cur - base > nanos)), Mode.AND);
    }

    /** 相对阈值：退化比例（如 0.10 表示允许 10% 退化）超过即触发。 */
    public static ThresholdPolicy relative(double maxDegradationRatio) {
        return new ThresholdPolicy(List.of(new Rule(
                String.format("相对阈值 %.1f%%", maxDegradationRatio * 100),
                (base, cur) -> base > 0 && (cur - base) / base > maxDegradationRatio)), Mode.AND);
    }

    /** 组合多个策略：AND 表示全部触发才算超阈，OR 表示任一触发即算超阈。 */
    public static ThresholdPolicy combine(Mode mode, ThresholdPolicy... policies) {
        List<Rule> merged = new ArrayList<>();
        for (ThresholdPolicy policy : policies) {
            merged.addAll(policy.rules);
        }
        return new ThresholdPolicy(merged, mode);
    }

    public boolean exceeded(double baselineNanos, double currentNanos) {
        if (rules.isEmpty()) {
            return true;
        }
        if (mode == Mode.AND) {
            return rules.stream().allMatch(r -> r.exceeded(baselineNanos, currentNanos));
        }
        return rules.stream().anyMatch(r -> r.exceeded(baselineNanos, currentNanos));
    }

    public String describe() {
        if (rules.isEmpty()) {
            return "无阈值（仅显著性判定）";
        }
        StringJoiner joiner = new StringJoiner(mode == Mode.AND ? " 且 " : " 或 ");
        rules.forEach(r -> joiner.add(r.description()));
        return joiner.toString();
    }
}
