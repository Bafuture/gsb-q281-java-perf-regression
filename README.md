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

---

## 框架说明（本次实现）

源码位于 `src/main/java/com/example/gsb/perfreg/`，测试位于 `src/test/java/com/example/gsb/perfreg/`。

### 快速上手

```java
RegressionFramework framework = RegressionFramework.builder(Path.of("build/baselines"))
        .environmentTag("-Xmx2g -XX:+UseG1GC")          // 环境标记，跨环境基线自动隔离
        .sampling(SamplingConfig.of(5, 20))             // 5 次预热（丢弃）+ 20 次正式采样
        .thresholds(ThresholdPolicy.combine(            // 阈值策略：显著性之外的业务过滤
                ThresholdPolicy.Mode.AND,
                ThresholdPolicy.relative(0.10),         // 退化超过 10%
                ThresholdPolicy.absolute(Duration.ofMillis(2)))) // 且慢超过 2ms
        .alpha(0.05)                                    // 显著性水平
        .build();

BenchmarkReport report = framework.check("json-parse", () -> parse(json));
System.out.println(report.render());
System.out.println(framework.summary().render());       // 样本数 / 预热丢弃数 / 回归项数
```

### 判定方法

一次 `check` 的完整流程：

1. **采样**：先跑 `warmupIterations` 次预热（结果丢弃，消除 JIT/缓存冷启动），再采集
   `measuredIterations` 次耗时，记录中位数与 p90/p95/p99（线性插值分位）。
2. **基线**：首次运行将样本按“环境指纹 / 基准名.properties”持久化；环境指纹由
   OS、架构、JVM 版本、核数、主机名与自定义标签（如 JVM 参数）SHA-256 生成，
   不同机器或 JVM 参数的基线互不干扰。
3. **显著性判定**：使用 **Mann-Whitney U 检验**（非参数秩和检验，不假设耗时正态分布），
   将基线样本与当前样本混合排序求秩，取单侧备择“当前显著更慢”，正态近似含连续修正与
   结对修正，`p < alpha`（默认 0.05）才认为差异超出样本波动范围，避免把噪声当回归。
4. **阈值门控**：显著性之上再叠加阈值策略——绝对耗时阈值与相对退化比例可按
   AND / OR 组合，过滤“统计显著但业务无感”的微小差异。两者同时成立才判定 `REGRESSION`。
5. **报告**：`BenchmarkReport` 含基准名、基线/当前中位数、变化率、判定结果与置信说明
   （p 值、alpha、阈值命中情况）；`RunSummary` 汇总样本数、预热丢弃数与回归项数。
