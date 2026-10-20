package seedu.address.model.ranking;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Sparse precision operator and bounded-error conjugate-gradient difference queries. */
final class DifferenceVarianceSolver {
    private static final int MAX_ITERATIONS = 500;
    private static final double ABSOLUTE_TOLERANCE = 1e-10;
    private static final double RELATIVE_TOLERANCE = 1e-6;

    private final int size;
    private final double[] priorDiagonal;
    private final double[] preconditionerDiagonal;
    private final double[] weights;
    private final ComparisonStatistics.Edge[] edges;
    private final double lowerEigenvalueBound;
    private final Map<Long, Double> cache = new HashMap<>();

    DifferenceVarianceSolver(ComparisonStatistics statistics, double[] strengths) {
        size = statistics.size();
        priorDiagonal = Arrays.copyOf(strengths, size);
        preconditionerDiagonal = Arrays.copyOf(strengths, size);
        edges = statistics.edges().toArray(ComparisonStatistics.Edge[]::new);
        weights = new double[edges.length];
        double minimum = Double.POSITIVE_INFINITY;
        for (double strength : strengths) {
            minimum = Math.min(minimum, strength);
        }
        lowerEigenvalueBound = minimum;
        for (int i = 0; i < edges.length; i++) {
            ComparisonStatistics.Edge edge = edges[i];
            double first = strengths[edge.first()];
            double second = strengths[edge.second()];
            double probability = first / (first + second);
            weights[i] = edge.count() * probability * (1 - probability);
            preconditionerDiagonal[edge.first()] += weights[i];
            preconditionerDiagonal[edge.second()] += weights[i];
        }
    }

    double varianceUpperBound(int first, int second) {
        if (first == second) {
            return 0;
        }
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        long key = ((long) low << 32) | (high & 0xffffffffL);
        Double cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        double result = solve(first, second);
        if (cache.size() >= 4 * size + 64) {
            cache.clear();
        }
        cache.put(key, result);
        return result;
    }

    private double solve(int first, int second) {
        double[] right = new double[size];
        right[first] = 1;
        right[second] = -1;
        double[] solution = new double[size];
        double[] residual = Arrays.copyOf(right, size);
        double[] scaled = new double[size];
        double[] direction = new double[size];
        for (int i = 0; i < size; i++) {
            scaled[i] = residual[i] / preconditionerDiagonal[i];
            direction[i] = scaled[i];
        }
        double residualProduct = dot(residual, scaled);
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] product = multiply(direction);
            double denominator = dot(direction, product);
            if (!Double.isFinite(denominator) || denominator <= 0) {
                throw new IllegalStateException("Uncertainty precision is not positive definite.");
            }
            double step = residualProduct / denominator;
            for (int i = 0; i < size; i++) {
                solution[i] += step * direction[i];
                residual[i] -= step * product[i];
            }
            double[] currentProduct = multiply(solution);
            double[] trueResidual = subtract(right, currentProduct);
            double quadratic = dot(solution, currentProduct);
            double lower = Math.max(0, 2 * dot(right, solution) - quadratic);
            double bound = dot(trueResidual, trueResidual) / lowerEigenvalueBound;
            double upper = lower + bound;
            if (!Double.isFinite(upper) || upper < 0) {
                throw new IllegalStateException("Uncertainty variance is invalid.");
            }
            if (bound <= ABSOLUTE_TOLERANCE + RELATIVE_TOLERANCE * upper) {
                return upper;
            }
            double nextProduct = 0;
            for (int i = 0; i < size; i++) {
                scaled[i] = residual[i] / preconditionerDiagonal[i];
                nextProduct += residual[i] * scaled[i];
            }
            if (!Double.isFinite(nextProduct) || nextProduct <= 0) {
                throw new IllegalStateException("Uncertainty solve failed to converge.");
            }
            double ratio = nextProduct / residualProduct;
            for (int i = 0; i < size; i++) {
                direction[i] = scaled[i] + ratio * direction[i];
            }
            residualProduct = nextProduct;
        }
        throw new IllegalStateException("Uncertainty solve exceeded its iteration budget.");
    }

    private double[] multiply(double[] vector) {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = priorDiagonal[i] * vector[i];
        }
        for (int i = 0; i < edges.length; i++) {
            ComparisonStatistics.Edge edge = edges[i];
            double contribution = weights[i] * (vector[edge.first()] - vector[edge.second()]);
            result[edge.first()] += contribution;
            result[edge.second()] -= contribution;
        }
        return result;
    }

    private static double[] subtract(double[] first, double[] second) {
        double[] result = new double[first.length];
        for (int i = 0; i < first.length; i++) {
            result[i] = first[i] - second[i];
        }
        return result;
    }

    private static double dot(double[] first, double[] second) {
        double result = 0;
        for (int i = 0; i < first.length; i++) {
            result += first[i] * second[i];
        }
        return result;
    }
}
