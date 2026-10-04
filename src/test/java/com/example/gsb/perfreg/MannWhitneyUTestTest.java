package com.example.gsb.perfreg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MannWhitneyUTestTest {

    private static long[] range(int startInclusive, int endExclusive) {
        long[] values = new long[endExclusive - startInclusive];
        for (int i = 0; i < values.length; i++) {
            values[i] = startInclusive + i;
        }
        return values;
    }

    @Test
    void overlappingDistributionsAreNotSignificant() {
        SignificanceResult result =
                MannWhitneyUTest.test(range(1, 11), range(1, 11));

        assertThat(result.pValueOneSided()).isGreaterThan(0.05);
        assertThat(result.isSignificant(0.05)).isFalse();
    }

    @Test
    void clearlyShiftedDistributionIsSignificant() {
        SignificanceResult result =
                MannWhitneyUTest.test(range(10, 20), range(30, 40));

        assertThat(result.pValueOneSided()).isLessThan(0.001);
        assertThat(result.isSignificant(0.05)).isTrue();
        assertThat(result.z()).isPositive();
    }

    @Test
    void noisySmallShiftIsNotSignificantWithFewSamples() {
        long[] baseline = {100, 102, 98, 101, 99, 103, 97, 100};
        long[] current = {101, 103, 100, 102, 99, 104, 98, 101};

        SignificanceResult result = MannWhitneyUTest.test(baseline, current);

        assertThat(result.pValueOneSided()).isGreaterThan(0.05);
        assertThat(result.isSignificant(0.05)).isFalse();
    }

    @Test
    void handlesTiesWithAverageRanks() {
        long[] baseline = {5, 5, 5, 5};
        long[] current = {6, 6, 6, 6};

        SignificanceResult result = MannWhitneyUTest.test(baseline, current);

        assertThat(result.pValueOneSided()).isLessThan(0.01);
        assertThat(result.u()).isCloseTo(16.0, within(1e-9));
    }

    @Test
    void rejectsSingleSampleGroups() {
        try {
            MannWhitneyUTest.test(new long[]{1}, new long[]{2, 3});
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
