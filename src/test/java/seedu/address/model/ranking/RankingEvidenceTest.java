package seedu.address.model.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.junit.jupiter.api.Test;

import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.comparison.ComparisonDecision;
import seedu.address.model.comparison.ComparisonSettings;

public class RankingEvidenceTest {
    private static final ApplicantId A = new ApplicantId("00000000-0000-0000-0000-000000000001");
    private static final ApplicantId B = new ApplicantId("00000000-0000-0000-0000-000000000002");
    private static final ApplicantId C = new ApplicantId("00000000-0000-0000-0000-000000000003");

    @Test
    public void index_retainsInactiveOutcomesAndRejectsDuplicateIdentity() {
        ComparisonDecision first = new ComparisonDecision(A, B, A);
        ComparisonDecision reversal = new ComparisonDecision(B, A, B);
        ComparisonStatisticsIndex index = ComparisonStatisticsIndex.from(List.of(first, reversal));
        assertEquals(0, index.snapshot(Set.of(A, C)).count(0));
        ComparisonStatistics reactivated = index.snapshot(Set.of(A, B));
        assertEquals(2, reactivated.pairCount(0, 1));
        assertEquals(1, reactivated.wins(reactivated.indexOf(A)));
        assertEquals(1, reactivated.wins(reactivated.indexOf(B)));
        assertThrows(IllegalArgumentException.class, () -> index.record(first));
        assertThrows(IllegalArgumentException.class, () -> index.snapshotWith(Set.of(A, B), reversal));
    }

    @Test
    public void posterior_twoApplicants_matchesKnownModeAndPositiveVariance() {
        ComparisonStatistics statistics = ComparisonStatisticsIndex.from(
                List.of(new ComparisonDecision(A, B, A))).snapshot(Set.of(A, B));
        BayesianRankingModel posterior = BayesianRankingModel.fit(statistics, null);
        assertEquals(StrictMath.log(2), posterior.meanDifference(statistics.indexOf(A), statistics.indexOf(B)),
                1e-7);
        assertTrue(posterior.varianceUpperBound(statistics.indexOf(A), statistics.indexOf(B)) > 0);
        assertTrue(PairInformationGain.calculate(0, 1) > 0);
        assertTrue(PairInformationGain.calculate(0, 1) > PairInformationGain.calculate(3, 1));
        assertEquals(0, PairInformationGain.calculate(0, 0), 1e-12);
    }

    @Test
    public void sparseVariance_upperBoundsExactDenseDifference() {
        ComparisonStatistics statistics = ComparisonStatisticsIndex.from(List.of(
                new ComparisonDecision(A, B, A), new ComparisonDecision(B, C, B)))
                .snapshot(Set.of(A, B, C));
        double[][] precision = {
            {1.25, -0.25, 0},
            {-0.25, 1.50, -0.25},
            {0, -0.25, 1.25}
        };
        double[] right = {1, 0, -1};
        double exact = new ArrayRealVector(right).dotProduct(
                new LUDecomposition(new Array2DRowRealMatrix(precision)).getSolver()
                        .solve(new ArrayRealVector(right)));
        DifferenceVarianceSolver solver = new DifferenceVarianceSolver(statistics, new double[] {1, 1, 1});
        double upper = solver.varianceUpperBound(statistics.indexOf(A), statistics.indexOf(C));
        assertTrue(upper >= exact - 1e-12);
        assertTrue(upper - exact < 1e-6);
    }

    @Test
    public void readiness_requiresCoverageAndConnectionThenAcceptsClearRanking() {
        ComparisonSettings settings = new ComparisonSettings();
        ComparisonStatistics empty = ComparisonStatisticsIndex.from(List.of()).snapshot(Set.of(A, B));
        assertEquals(RankingReadiness.Status.INSUFFICIENT_DATA,
                evaluate(empty, settings).status());

        List<ComparisonDecision> outcomes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            outcomes.add(new ComparisonDecision(A, B, A));
        }
        ComparisonStatistics decisive = ComparisonStatisticsIndex.from(outcomes).snapshot(Set.of(A, B));
        assertTrue(evaluate(decisive, settings).isReady());

        ComparisonStatistics disconnected = ComparisonStatisticsIndex.from(outcomes).snapshot(Set.of(A, B, C));
        assertEquals(RankingReadiness.Status.INSUFFICIENT_DATA,
                evaluate(disconnected, settings).status());
    }

    @Test
    public void readiness_canCertifyNearEqualApplicantsWithoutClaimingStrictOrder() {
        List<ComparisonDecision> outcomes = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            outcomes.add(new ComparisonDecision(A, B, A));
            outcomes.add(new ComparisonDecision(A, B, B));
        }
        ComparisonStatistics statistics = ComparisonStatisticsIndex.from(outcomes).snapshot(Set.of(A, B));
        RankingReadiness readiness = evaluate(statistics, new ComparisonSettings());
        assertTrue(readiness.isReady());
        assertFalse(readiness.uncertainGroups().isEmpty());
    }

    private static RankingReadiness evaluate(ComparisonStatistics statistics, ComparisonSettings settings) {
        RankingEngine engine = new RankingEngine();
        engine.recalculate(statistics);
        return RankingReadinessEvaluator.evaluate(statistics, List.copyOf(engine.getRanking()),
                BayesianRankingModel.fit(statistics, null), settings);
    }
}
