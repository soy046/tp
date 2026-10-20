package seedu.address.model.ranking;

import org.apache.commons.math3.analysis.integration.gauss.GaussIntegrator;
import org.apache.commons.math3.analysis.integration.gauss.GaussIntegratorFactory;

/** Predictive information gained from one Bradley-Terry judgment under a Gaussian log-strength approximation. */
public final class PairInformationGain {
    private static final double ROOT_PI = StrictMath.sqrt(StrictMath.PI);
    private static final double ROOT_TWO = StrictMath.sqrt(2);
    private static final GaussIntegrator RULE_20 = new GaussIntegratorFactory().hermite(20);
    private static final GaussIntegrator RULE_40 = new GaussIntegratorFactory().hermite(40);
    private static final GaussIntegrator RULE_80 = new GaussIntegratorFactory().hermite(80);

    private PairInformationGain() {}

    /** Returns nonnegative expected binary information gain, in nats. */
    public static double calculate(double meanDifference, double varianceUpperBound) {
        if (!Double.isFinite(meanDifference) || !Double.isFinite(varianceUpperBound)
                || varianceUpperBound < 0) {
            throw new IllegalArgumentException("Invalid posterior difference.");
        }
        double gain20 = integrate(meanDifference, varianceUpperBound, RULE_20);
        double gain40 = integrate(meanDifference, varianceUpperBound, RULE_40);
        if (Math.abs(gain20 - gain40) <= 1e-8) {
            return Math.max(0, gain40);
        }
        double gain80 = integrate(meanDifference, varianceUpperBound, RULE_80);
        if (Math.abs(gain40 - gain80) > 1e-8) {
            throw new IllegalStateException("Information gain quadrature did not converge.");
        }
        return Math.max(0, gain80);
    }

    private static double integrate(double mean, double variance, GaussIntegrator rule) {
        double scale = ROOT_TWO * StrictMath.sqrt(variance);
        double meanProbability = rule.integrate(x -> sigmoid(mean + scale * x)) / ROOT_PI;
        double meanEntropy = rule.integrate(x -> entropy(sigmoid(mean + scale * x))) / ROOT_PI;
        return entropy(meanProbability) - meanEntropy;
    }

    private static double sigmoid(double value) {
        if (value >= 0) {
            return 1 / (1 + StrictMath.exp(-value));
        }
        double exponent = StrictMath.exp(value);
        return exponent / (1 + exponent);
    }

    private static double entropy(double probability) {
        if (probability <= 0 || probability >= 1) {
            return 0;
        }
        return -probability * StrictMath.log(probability)
                - (1 - probability) * StrictMath.log1p(-probability);
    }
}
