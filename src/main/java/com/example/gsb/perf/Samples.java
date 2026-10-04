package com.example.gsb.perf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Immutable collection of measured samples (nanoseconds), kept sorted.
 * Provides median, arbitrary percentiles and MAD (median absolute deviation).
 */
public final class Samples {

    private final List<Double> sorted;

    private Samples(List<Double> sorted) {
        this.sorted = sorted;
    }

    public static Samples of(Collection<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return new Samples(Collections.unmodifiableList(copy));
    }

    public int size() {
        return sorted.size();
    }

    public List<Double> values() {
        return sorted;
    }

    public double min() {
        return sorted.get(0);
    }

    public double max() {
        return sorted.get(sorted.size() - 1);
    }

    public double mean() {
        double sum = 0;
        for (double v : sorted) {
            sum += v;
        }
        return sum / sorted.size();
    }

    public double median() {
        return percentile(50.0);
    }

    /** Percentile with linear interpolation, p in [0, 100]. */
    public double percentile(double p) {
        if (p < 0 || p > 100) {
            throw new IllegalArgumentException("p must be in [0,100]: " + p);
        }
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double rank = p / 100.0 * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, n - 1);
        double frac = rank - lower;
        return sorted.get(lower) + frac * (sorted.get(upper) - sorted.get(lower));
    }

    /** Median absolute deviation: median of |x - median|. Robust to outliers. */
    public double mad() {
        double median = median();
        List<Double> deviations = new ArrayList<>(sorted.size());
        for (double v : sorted) {
            deviations.add(Math.abs(v - median));
        }
        Collections.sort(deviations);
        return Samples.of(deviations).median();
    }

    /**
     * Robust spread estimate scaled to be comparable to a standard deviation
     * for roughly normal data (1.4826 * MAD). Falls back to 1% of the median
     * when the distribution is (nearly) degenerate, so the noise band never
     * collapses to zero width.
     */
    public double robustSigma() {
        double sigma = 1.4826 * mad();
        if (sigma <= 0) {
            sigma = Math.max(1e-9, Math.abs(median()) * 0.01);
        }
        return sigma;
    }
}
