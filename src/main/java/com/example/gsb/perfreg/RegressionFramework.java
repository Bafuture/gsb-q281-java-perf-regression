package com.example.gsb.perfreg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * 性能回归对比框架门面。
 *
 * <p>使用流程：{@code check(name, benchmark)} 先按 {@link SamplingConfig} 采样，
 * 若该环境下无基线则建立并持久化基线；否则用 Mann-Whitney U 检验判断当前样本
 * 是否显著慢于基线，再用 {@link ThresholdPolicy} 过滤业务上可忽略的微小差异，
 * 二者同时成立才判定 {@link Verdict#REGRESSION}。
 */
public final class RegressionFramework {

    private final BaselineStore store;
    private final Environment environment;
    private final Sampler sampler;
    private final ThresholdPolicy thresholdPolicy;
    private final double alpha;
    private final List<BenchmarkReport> reports = new ArrayList<>();

    private RegressionFramework(Builder builder) {
        this.store = new BaselineStore(builder.baselineDir);
        this.environment = builder.environment;
        this.sampler = new Sampler(builder.samplingConfig);
        this.thresholdPolicy = builder.thresholdPolicy;
        this.alpha = builder.alpha;
    }

    public static Builder builder(Path baselineDir) {
        return new Builder(baselineDir);
    }

    /** 对一段代码计时并与基线对比。 */
    public BenchmarkReport check(String benchmarkName, Runnable benchmark) {
        return check(benchmarkName, () -> {
            long start = System.nanoTime();
            benchmark.run();
            return System.nanoTime() - start;
        });
    }

    /** 以“每次迭代耗时（纳秒）”为数据源与基线对比。 */
    public BenchmarkReport check(String benchmarkName, LongSupplier iterationNanos) {
        SampleStats current = sampler.sample(iterationNanos);
        Optional<Baseline> baseline = store.load(benchmarkName, environment);

        BenchmarkReport report;
        if (baseline.isEmpty()) {
            store.save(Baseline.of(benchmarkName, environment, current), environment);
            report = new BenchmarkReport(
                    benchmarkName, environment.key(), Verdict.BASELINE_CREATED,
                    Double.NaN, current.median(), Double.NaN,
                    0, current.sampleCount(), current.warmupDiscarded(),
                    "首次运行，已建立基线（环境: " + environment.key() + "）");
        } else {
            report = compare(benchmarkName, baseline.get(), current);
        }
        reports.add(report);
        return report;
    }

    /** 强制用当前采样覆盖基线（确认新性能水平后调用）。 */
    public BenchmarkReport recordBaseline(String benchmarkName, LongSupplier iterationNanos) {
        SampleStats current = sampler.sample(iterationNanos);
        store.save(Baseline.of(benchmarkName, environment, current), environment);
        BenchmarkReport report = new BenchmarkReport(
                benchmarkName, environment.key(), Verdict.BASELINE_CREATED,
                Double.NaN, current.median(), Double.NaN,
                0, current.sampleCount(), current.warmupDiscarded(),
                "基线已更新（环境: " + environment.key() + "）");
        reports.add(report);
        return report;
    }

    private BenchmarkReport compare(String name, Baseline baseline, SampleStats current) {
        double baselineMedian = baseline.stats().median();
        double currentMedian = current.median();
        double changeRatio = baselineMedian > 0 ? (currentMedian - baselineMedian) / baselineMedian : 0.0;

        SignificanceResult significance =
                MannWhitneyUTest.test(baseline.stats().samples(), current.samples());
        boolean significant = significance.isSignificant(alpha);
        boolean thresholdHit = thresholdPolicy.exceeded(baselineMedian, currentMedian);
        Verdict verdict = significant && thresholdHit ? Verdict.REGRESSION : Verdict.NO_REGRESSION;

        String note = String.format(Locale.ROOT,
                "Mann-Whitney U 单侧 p=%.4f (alpha=%.2f)%s；中位数变化 %+.2f%%，阈值策略[%s]%s",
                significance.pValueOneSided(), alpha,
                significant ? "，差异超出样本波动范围" : "，差异在样本波动范围内（视为噪声）",
                changeRatio * 100,
                thresholdPolicy.describe(),
                thresholdHit ? "被越过" : "未越过");

        return new BenchmarkReport(
                name, environment.key(), verdict,
                baselineMedian, currentMedian, changeRatio,
                baseline.stats().sampleCount(), current.sampleCount(), current.warmupDiscarded(),
                note);
    }

    /** 本轮运行的汇总统计。 */
    public RunSummary summary() {
        return new RunSummary(List.copyOf(reports));
    }

    public static final class Builder {
        private final Path baselineDir;
        private Environment environment = Environment.detect();
        private SamplingConfig samplingConfig = SamplingConfig.of(3, 15);
        private ThresholdPolicy thresholdPolicy = ThresholdPolicy.significanceOnly();
        private double alpha = 0.05;

        private Builder(Path baselineDir) {
            this.baselineDir = baselineDir;
        }

        /** 自定义环境标签（如 JVM 参数），不同标签的基线相互隔离。 */
        public Builder environmentTag(String tag) {
            this.environment = Environment.detect(tag);
            return this;
        }

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder sampling(SamplingConfig config) {
            this.samplingConfig = config;
            return this;
        }

        public Builder thresholds(ThresholdPolicy policy) {
            this.thresholdPolicy = policy;
            return this;
        }

        /** 显著性水平，默认 0.05。 */
        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public RegressionFramework build() {
            return new RegressionFramework(this);
        }
    }
}
