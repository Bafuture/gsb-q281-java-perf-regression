# 性能回归基线对比框架

Pair-wise GSB 标注任务仓库（第 17 批 / 281）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们改了代码后经常悄悄变慢，跑基准又不稳定，希望有个能判定是否真回归的框架。请从零实现一个性能回归基线对比框架。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持为某个基准采集多次运行样本（含预热），并记录中位数与分位值；2) 支持建立基线并持久化，后续运行与基线对比；3) 支持显著性判定：用样本分布判断差异是否超出波动范围，避免把噪声当成回归，需说明判定方法；4) 支持阈值策略：绝对耗时阈值与相对退化比例，两者可组合；5) 支持环境标记：不同机器或 JVM 参数下的基线要分别保存，避免跨环境比较；6) 支持回归报告：基准名、基线值、当前值、变化率、判定结果与置信说明；7) 提供统计：样本数、预热丢弃数、被判定的回归项数；8) 测试覆盖多次采样、基线持久化、显著性判定、阈值组合、环境隔离与报告内容；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

## 实现说明

核心代码位于 `src/main/java/com/example/gsb/perf/`，零额外依赖。

### 用法

```java
PerfRegression perf = PerfRegression.builder()
        .baselineDirectory(Path.of("target/perf-baselines"))
        .environmentTag("ci-linux-x64")      // 机器/CI 画像，也可用自定义 JVM 参数标签
        .warmupIterations(5)                 // 预热次数，不计入样本
        .measuredIterations(20)              // 正式采样次数
        .thresholds(ThresholdPolicy.of(
                50_000,      // 绝对阈值：中位数退化 > 50µs
                0.05,        // 相对阈值：退化 > 5%
                ThresholdPolicy.Combination.ANY)) // 任一命中即超阈值（ALL = 两者都命中）
        .build();

ComparisonResult result = perf.benchmark("json-parse", () -> benchmarkBody());
// 首次运行自动建立并持久化基线；之后运行与基线对比
if (result.verdict() == Verdict.REGRESSION) { ... }
RegressionReport report = perf.report();
System.out.println(report.render());
```

### 显著性判定方法（避免把噪声当回归）

1. 每个基准采集多次样本，预热样本全部丢弃；统计量采用**中位数**与 p90/p95/p99 分位数（线性插值）。
2. 离散度用 **MAD（中位数绝对偏差）**，对离群值不敏感；按 `σ ≈ 1.4826 × MAD` 换算为标准差估计。MAD 为 0（近乎恒定的快速代码）时回退为中位数的 1%，保证噪声带宽不会塌缩为 0。
3. 中位数的标准误近似为 `SE = 1.2533 × σ / √n`，对基线与当前两组样本做双侧 z 检验：

   `|中位数_当前 − 中位数_基线| > z × √(SE_基线² + SE_当前²)`

   默认 `z = 1.96`（95% 置信，构造时可改 2.576 = 99%）。
4. 判定逻辑：差异不显著 → `NO_REGRESSION`（视为抖动）；显著且变慢、且**同时**超过阈值策略 → `REGRESSION`；显著变慢但在阈值容忍范围内 → `NO_REGRESSION`；显著变快 → `IMPROVEMENT`；任一侧样本数不足 → `INSUFFICIENT_DATA`。

### 环境隔离

`EnvironmentFingerprint` 由 OS、架构、JDK 版本、JVM 名称、JVM 启动参数（`RuntimeMXBean`）及自定义 tag 构成并取 SHA-256 前缀。基线文件名为 `<基准名>__<环境哈希>.properties`，不同机器 / 不同 `-Xmx` 等参数的基线互不读取、互不覆盖。

### 报告与统计

报告含基准名、基线中位数、当前中位数、变化率、判定结果，以及每条结果的置信说明（实际偏移、噪声带宽 ±值、阈值命中情况）；汇总统计包含基准数、总样本数、预热丢弃数、回归项数与改进项数（`RegressionReport` 的 `totalSamples()` / `totalWarmupDiscarded()` / `regressionCount()`）。
