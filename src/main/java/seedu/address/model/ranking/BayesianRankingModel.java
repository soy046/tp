package seedu.address.model.ranking;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import seedu.address.model.applicant.ApplicantId;

/** Laplace approximation to the Bradley-Terry posterior in log-strength coordinates. */
public final class BayesianRankingModel {
    private static final int MAX_FIT_ITERATIONS = 10_000;
    private static final double FIT_TOLERANCE = 1e-10;
    private static final double RESIDUAL_TOLERANCE = 1e-8;
    private static final double PRIOR_PSEUDO_WINS = 1.0;

    private final ComparisonStatistics statistics;
    private final double[] mode;
    private final DifferenceVarianceSolver variances;

    private BayesianRankingModel(ComparisonStatistics statistics, double[] strengths) {
        this.statistics = statistics;
        this.mode = Arrays.stream(strengths).map(StrictMath::log).toArray();
        this.variances = new DifferenceVarianceSolver(statistics, strengths);
    }

    /** Fits with a previous completed result as a starting point when available. */
    public static BayesianRankingModel fit(ComparisonStatistics statistics, BayesianRankingModel previous) {
        int size = statistics.size();
        double[] strengths = new double[size];
        Arrays.fill(strengths, PRIOR_PSEUDO_WINS);
        if (previous != null) {
            Map<ApplicantId, Double> previousStrengths = new HashMap<>();
            var previousIds = previous.statistics.ids();
            for (int i = 0; i < previous.statistics.size(); i++) {
                previousStrengths.put(previousIds.get(i), StrictMath.exp(previous.mode[i]));
            }
            var ids = statistics.ids();
            for (int i = 0; i < size; i++) {
                strengths[i] = previousStrengths.getOrDefault(ids.get(i), PRIOR_PSEUDO_WINS);
            }
        }
        for (int iteration = 0; iteration < MAX_FIT_ITERATIONS; iteration++) {
            double[] denominators = new double[size];
            Arrays.fill(denominators, 1.0);
            for (ComparisonStatistics.Edge edge : statistics.edges()) {
                double contribution = edge.count() / (strengths[edge.first()] + strengths[edge.second()]);
                denominators[edge.first()] += contribution;
                denominators[edge.second()] += contribution;
            }
            double[] updated = new double[size];
            for (int i = 0; i < size; i++) {
                updated[i] = (statistics.wins(i) + PRIOR_PSEUDO_WINS) / denominators[i];
                requirePositiveFinite(updated[i]);
            }
            normalizeComponents(statistics, updated);
            boolean converged = true;
            for (int i = 0; i < size; i++) {
                if (Math.abs(updated[i] - strengths[i]) / strengths[i] > FIT_TOLERANCE) {
                    converged = false;
                }
            }
            strengths = updated;
            if (converged && residualIsSmall(statistics, strengths)) {
                return new BayesianRankingModel(statistics, strengths);
            }
        }
        throw new IllegalStateException("Posterior fit did not converge.");
    }

    private static void normalizeComponents(ComparisonStatistics statistics, double[] strengths) {
        Map<Integer, Double> totals = new HashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int i = 0; i < strengths.length; i++) {
            totals.merge(statistics.component(i), strengths[i], Double::sum);
            sizes.merge(statistics.component(i), 1, Integer::sum);
        }
        for (int i = 0; i < strengths.length; i++) {
            int component = statistics.component(i);
            strengths[i] *= PRIOR_PSEUDO_WINS * sizes.get(component) / totals.get(component);
            requirePositiveFinite(strengths[i]);
        }
    }

    private static boolean residualIsSmall(ComparisonStatistics statistics, double[] strengths) {
        double[] gradient = new double[strengths.length];
        for (int i = 0; i < gradient.length; i++) {
            gradient[i] = statistics.wins(i) + PRIOR_PSEUDO_WINS - strengths[i];
        }
        for (ComparisonStatistics.Edge edge : statistics.edges()) {
            double total = strengths[edge.first()] + strengths[edge.second()];
            gradient[edge.first()] -= edge.count() * strengths[edge.first()] / total;
            gradient[edge.second()] -= edge.count() * strengths[edge.second()] / total;
        }
        for (double value : gradient) {
            if (!Double.isFinite(value) || Math.abs(value) > RESIDUAL_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static void requirePositiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalStateException("Posterior fit encountered an invalid strength.");
        }
    }

    public double meanDifference(int first, int second) {
        return mode[first] - mode[second];
    }

    /** Returns a conservative upper bound for one difference variance. */
    public double varianceUpperBound(int first, int second) {
        return variances.varianceUpperBound(first, second);
    }
}
