package com.example.gsb.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class SamplesTest {

    @Test
    void computesMedianForOddAndEvenCounts() {
        assertThat(Samples.of(List.of(3.0, 1.0, 2.0)).median()).isEqualTo(2.0);
        assertThat(Samples.of(List.of(4.0, 1.0, 3.0, 2.0)).median()).isEqualTo(2.5);
    }

    @Test
    void computesPercentilesWithInterpolation() {
        Samples samples = Samples.of(List.of(10.0, 20.0, 30.0, 40.0, 50.0));
        assertThat(samples.percentile(0)).isEqualTo(10.0);
        assertThat(samples.percentile(50)).isEqualTo(30.0);
        assertThat(samples.percentile(90)).isCloseTo(46.0, within(1e-9));
        assertThat(samples.percentile(95)).isCloseTo(48.0, within(1e-9));
        assertThat(samples.percentile(99)).isCloseTo(49.6, within(1e-9));
        assertThat(samples.percentile(100)).isEqualTo(50.0);
    }

    @Test
    void rejectsInvalidPercentile() {
        Samples samples = Samples.of(List.of(1.0, 2.0));
        assertThatThrownBy(() -> samples.percentile(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computesMadRobustToOutliers() {
        Samples samples = Samples.of(List.of(10.0, 11.0, 9.0, 10.0, 10.0, 1000.0));
        assertThat(samples.median()).isEqualTo(10.0);
        assertThat(samples.mad()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void robustSigmaFallsBackForDegenerateDistribution() {
        Samples constant = Samples.of(List.of(100.0, 100.0, 100.0, 100.0));
        assertThat(constant.mad()).isEqualTo(0.0);
        assertThat(constant.robustSigma()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rejectsEmptySamples() {
        assertThatThrownBy(() -> Samples.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
