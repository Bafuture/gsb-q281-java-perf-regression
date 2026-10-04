package com.example.gsb.perfreg;

import java.util.List;
import java.util.stream.Collectors;

/** 一轮运行的汇总：所有报告 + 样本数 / 预热丢弃数 / 回归项数统计。 */
public record RunSummary(List<BenchmarkReport> reports) {

    public int regressionCount() {
        return (int) reports.stream().filter(BenchmarkReport::isRegression).count();
    }

    public int totalSamples() {
        return reports.stream().mapToInt(BenchmarkReport::currentSampleCount).sum();
    }

    public int totalWarmupDiscarded() {
        return reports.stream().mapToInt(BenchmarkReport::warmupDiscarded).sum();
    }

    public String render() {
        String body = reports.stream().map(BenchmarkReport::render).collect(Collectors.joining("\n"));
        return body + String.format(
                "%n----%n基准数=%d, 回归项=%d, 总样本数=%d, 预热丢弃=%d",
                reports.size(), regressionCount(), totalSamples(), totalWarmupDiscarded());
    }
}
