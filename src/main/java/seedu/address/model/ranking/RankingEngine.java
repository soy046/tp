package seedu.address.model.ranking;

import static seedu.address.commons.util.AppUtil.checkArgument;
import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.comparison.ComparisonDecision;

/**
 * Derives the complete active ranking using a regularized Bradley-Terry model.
 * Positive strengths are fitted by maximum a posteriori estimation with independent
 * Gamma priors of shape 2 and rate 1. Unassessed applicants have strength 1 and score 0.
 * Scores are natural logarithms of fitted strengths, rounded to six decimal places.
 * Equal scores share competition positions, such as 1, 1, 3; IDs only order tied entries for display.
 *
 * <p>Every eligible decision has equal weight, including repeated judgments with distinct IDs.
 * Fitting is independent of decision execution order and timestamps. Only decisions whose two
 * participants are active contribute to strengths and comparison counts. Disconnected groups
 * share a reference supplied by the prior, rather than evidence of their relative strengths.
 *
 * <p>Rankings are runtime results rebuilt after loading, rather than saved applicant records.
 * Callers coordinate membership, history, and ranking changes and manage access to the observable view.
 */
public class RankingEngine {

    private static final double PRIOR_SHAPE = 2.0;
    private static final double PRIOR_RATE = 1.0;
    private static final double INITIAL_STRENGTH = 1.0;
    private static final int MAX_ITERATIONS = 10_000;
    private static final double RELATIVE_CHANGE_TOLERANCE = 1e-10;
    private static final double RESIDUAL_TOLERANCE = 1e-8;
    private static final int SCORE_DECIMAL_PLACES = 6;

    private final ObservableList<RankingEntry> ranking = FXCollections.observableArrayList();
    private final ObservableList<RankingEntry> unmodifiableRanking =
            FXCollections.unmodifiableObservableList(ranking);
    private List<ApplicantId> lastIds = List.of();
    private double[] lastStrengths = new double[0];

    /**
     * Constructs an engine with an empty ranking.
     */
    public RankingEngine() {}

    /**
     * Returns the same live, unmodifiable observable view of the complete current ranking.
     */
    public ObservableList<RankingEntry> getRanking() {
        return unmodifiableRanking;
    }

    /**
     * Rebuilds the ranking from active membership and eligible decisions.
     * Only the completed result is published.
     * Retained decisions become eligible again when both participants are reactivated.
     * All active applicants appear, including those with no eligible comparisons.
     *
     * <p>All supplied decisions are validated, including those involving inactive applicants.
     * Invalid input or fitting failure leaves this engine's existing ranking unchanged.
     * The calling model operation coordinates changes to membership and history separately.
     *
     * @param activeMembers the IDs of all applicants currently included in the ranking.
     * @param decisions the complete decision history, normally stored in execution order.
     * @throws NullPointerException if a collection, element, decision ID, participant ID, or winner ID is null.
     * @throws IllegalArgumentException if a decision has identical participants, an invalid winner,
     *     or an ID already present in the supplied history.
     * @throws IllegalStateException if fitting encounters invalid numerical values or fails to converge.
     */
    public void recalculate(Set<ApplicantId> activeMembers, List<ComparisonDecision> decisions) {
        ComparisonData data = aggregateComparisons(activeMembers, decisions);
        publish(data);
    }

    /** Rebuilds the ranking from already validated incremental aggregates. */
    public void recalculate(ComparisonStatistics statistics) {
        requireAllNonNull(statistics);
        List<PairCount> pairs = statistics.edges().stream()
                .map(edge -> new PairCount(edge.first(), edge.second(), edge.count()))
                .toList();
        int[] wins = new int[statistics.size()];
        int[] counts = new int[statistics.size()];
        for (int i = 0; i < statistics.size(); i++) {
            wins[i] = statistics.wins(i);
            counts[i] = statistics.count(i);
        }
        publish(new ComparisonData(statistics.ids(), pairs, wins, counts));
    }

    private void publish(ComparisonData data) {
        List<int[]> components = findConnectedComponents(data);
        double[] strengths = fitStrengths(data, components, initialStrengths(data.applicantIds()));
        List<RankingEntry> calculatedEntries = createEntries(data, strengths);

        lastIds = data.applicantIds();
        lastStrengths = strengths;
        ranking.setAll(calculatedEntries);
    }

    private double[] initialStrengths(List<ApplicantId> applicantIds) {
        double[] initial = new double[applicantIds.size()];
        Arrays.fill(initial, INITIAL_STRENGTH);
        Map<ApplicantId, Double> previous = new HashMap<>();
        for (int i = 0; i < lastIds.size(); i++) {
            previous.put(lastIds.get(i), lastStrengths[i]);
        }
        for (int i = 0; i < applicantIds.size(); i++) {
            initial[i] = previous.getOrDefault(applicantIds.get(i), INITIAL_STRENGTH);
        }
        return initial;
    }

    /**
     * Validates input snapshots and aggregates eligible judgments in deterministic applicant and pair order.
     */
    private static ComparisonData aggregateComparisons(Set<ApplicantId> activeMembers,
            List<ComparisonDecision> decisions) {
        requireAllNonNull(activeMembers, decisions);
        List<ApplicantId> applicantIds = new ArrayList<>(List.copyOf(activeMembers));
        List<ComparisonDecision> decisionSnapshot = List.copyOf(decisions);
        applicantIds.sort(Comparator.comparing(applicantId -> applicantId.value));

        Map<ApplicantId, Integer> applicantIndices = new HashMap<>();
        for (int index = 0; index < applicantIds.size(); index++) {
            applicantIndices.put(applicantIds.get(index), index);
        }

        int[] winCounts = new int[applicantIds.size()];
        int[] comparisonCounts = new int[applicantIds.size()];
        Map<PairKey, Integer> pairCounts = new HashMap<>();
        Set<UUID> decisionIds = new HashSet<>();
        for (ComparisonDecision decision : decisionSnapshot) {
            UUID decisionId = decision.getDecisionId();
            ApplicantId firstApplicantId = decision.getFirstApplicantId();
            ApplicantId secondApplicantId = decision.getSecondApplicantId();
            ApplicantId winnerId = decision.getWinnerId();
            requireAllNonNull(decisionId, firstApplicantId, secondApplicantId, winnerId);
            checkArgument(!firstApplicantId.equals(secondApplicantId),
                    "A ranking decision must contain two distinct applicants.");
            checkArgument(winnerId.equals(firstApplicantId) || winnerId.equals(secondApplicantId),
                    "The winner must be a participant in the ranking decision.");
            checkArgument(decisionIds.add(decisionId),
                    "Decision IDs must be unique within the ranking history.");

            Integer firstIndex = applicantIndices.get(firstApplicantId);
            Integer secondIndex = applicantIndices.get(secondApplicantId);
            if (firstIndex == null || secondIndex == null) {
                continue;
            }

            int winnerIndex = winnerId.equals(firstApplicantId) ? firstIndex : secondIndex;
            winCounts[winnerIndex]++;
            comparisonCounts[firstIndex]++;
            comparisonCounts[secondIndex]++;
            PairKey key = new PairKey(Math.min(firstIndex, secondIndex), Math.max(firstIndex, secondIndex));
            pairCounts.merge(key, 1, Integer::sum);
        }

        List<PairCount> pairs = new ArrayList<>();
        for (Map.Entry<PairKey, Integer> entry : pairCounts.entrySet()) {
            pairs.add(new PairCount(entry.getKey().firstIndex(), entry.getKey().secondIndex(),
                    entry.getValue()));
        }
        pairs.sort(Comparator.comparingInt(PairCount::firstIndex).thenComparingInt(PairCount::secondIndex));
        return new ComparisonData(List.copyOf(applicantIds), List.copyOf(pairs), winCounts, comparisonCounts);
    }

    /**
     * Finds undirected comparison components, including singletons, with ascending applicant indices in each.
     */
    private static List<int[]> findConnectedComponents(ComparisonData data) {
        List<List<Integer>> neighbours = new ArrayList<>();
        for (int index = 0; index < data.applicantIds().size(); index++) {
            neighbours.add(new ArrayList<>());
        }
        for (PairCount pair : data.pairs()) {
            neighbours.get(pair.firstIndex()).add(pair.secondIndex());
            neighbours.get(pair.secondIndex()).add(pair.firstIndex());
        }

        boolean[] visited = new boolean[neighbours.size()];
        List<int[]> components = new ArrayList<>();
        for (int start = 0; start < neighbours.size(); start++) {
            if (visited[start]) {
                continue;
            }

            List<Integer> component = new ArrayList<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.push(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                int index = pending.pop();
                component.add(index);
                for (int neighbour : neighbours.get(index)) {
                    if (!visited[neighbour]) {
                        visited[neighbour] = true;
                        pending.push(neighbour);
                    }
                }
            }

            component.sort(Integer::compareTo);
            components.add(component.stream().mapToInt(Integer::intValue).toArray());
        }
        return components;
    }

    /**
     * Fits positive strengths from a fresh neutral initialization, rejecting incomplete or invalid fits.
     */
    private static double[] fitStrengths(ComparisonData data, List<int[]> components) {
        double[] strengths = new double[data.applicantIds().size()];
        Arrays.fill(strengths, INITIAL_STRENGTH);
        return fitStrengths(data, components, strengths);
    }

    private static double[] fitStrengths(ComparisonData data, List<int[]> components, double[] initial) {
        double[] strengths = Arrays.copyOf(initial, initial.length);
        if (data.pairs().isEmpty()) {
            Arrays.fill(strengths, INITIAL_STRENGTH);
            return strengths;
        }

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] updatedStrengths = updateStrengths(data, strengths);
            normalizeStrengths(updatedStrengths, components);
            if (hasConverged(data, strengths, updatedStrengths)) {
                return updatedStrengths;
            }
            strengths = updatedStrengths;
        }
        throw new IllegalStateException("Bradley-Terry ranking did not converge within the iteration limit.");
    }

    /**
     * Applies the Gamma-prior MAP update to all applicants using only the preceding iteration's strengths.
     */
    private static double[] updateStrengths(ComparisonData data, double[] strengths) {
        double[] denominators = new double[strengths.length];
        Arrays.fill(denominators, PRIOR_RATE);
        for (PairCount pair : data.pairs()) {
            double totalStrength = strengths[pair.firstIndex()] + strengths[pair.secondIndex()];
            requirePositiveFinite(totalStrength);
            double contribution = pair.comparisonCount() / totalStrength;
            requirePositiveFinite(contribution);
            denominators[pair.firstIndex()] += contribution;
            denominators[pair.secondIndex()] += contribution;
        }

        double[] updatedStrengths = new double[strengths.length];
        for (int index = 0; index < strengths.length; index++) {
            requirePositiveFinite(denominators[index]);
            updatedStrengths[index] = (data.winCounts()[index] + PRIOR_SHAPE - 1) / denominators[index];
            requirePositiveFinite(updatedStrengths[index]);
        }
        return updatedStrengths;
    }

    /**
     * Maximizes each component's scale under the prior while preserving its pairwise probabilities.
     */
    private static void normalizeStrengths(double[] strengths, List<int[]> components) {
        for (int[] component : components) {
            double totalStrength = 0;
            for (int index : component) {
                totalStrength += strengths[index];
            }
            requirePositiveFinite(totalStrength);
            double targetTotal = component.length * (PRIOR_SHAPE - 1) / PRIOR_RATE;
            double scale = targetTotal / totalStrength;
            requirePositiveFinite(scale);

            for (int index : component) {
                strengths[index] *= scale;
                requirePositiveFinite(strengths[index]);
            }
        }
    }

    /**
     * Checks both relative parameter changes and the log-strength stationarity residuals.
     */
    private static boolean hasConverged(ComparisonData data, double[] previous, double[] current) {
        for (int index = 0; index < current.length; index++) {
            double relativeChange = Math.abs(current[index] - previous[index]) / previous[index];
            if (!Double.isFinite(relativeChange)) {
                throw new IllegalStateException(
                        "Bradley-Terry ranking encountered an invalid parameter change.");
            }
            if (relativeChange > RELATIVE_CHANGE_TOLERANCE) {
                return false;
            }
        }

        double[] residuals = new double[current.length];
        for (int index = 0; index < current.length; index++) {
            residuals[index] = data.winCounts()[index] + PRIOR_SHAPE - 1 - PRIOR_RATE * current[index];
        }
        for (PairCount pair : data.pairs()) {
            double totalStrength = current[pair.firstIndex()] + current[pair.secondIndex()];
            requirePositiveFinite(totalStrength);
            residuals[pair.firstIndex()] -=
                    pair.comparisonCount() * (current[pair.firstIndex()] / totalStrength);
            residuals[pair.secondIndex()] -=
                    pair.comparisonCount() * (current[pair.secondIndex()] / totalStrength);
        }
        for (double residual : residuals) {
            if (!Double.isFinite(residual)) {
                throw new IllegalStateException("Bradley-Terry ranking encountered an invalid residual.");
            }
            if (Math.abs(residual) > RESIDUAL_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rejects non-positive or non-finite numerical values before any results are published.
     */
    private static void requirePositiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalStateException("Bradley-Terry ranking encountered an invalid numerical value.");
        }
    }

    /**
     * Creates entries with canonical log-strength scores, competition positions, and stable tie order.
     */
    private static List<RankingEntry> createEntries(ComparisonData data, double[] strengths) {
        List<ApplicantScore> scores = new ArrayList<>();
        for (int index = 0; index < strengths.length; index++) {
            double score = BigDecimal.valueOf(StrictMath.log(strengths[index]))
                    .setScale(SCORE_DECIMAL_PLACES, RoundingMode.HALF_UP)
                    .doubleValue();
            score = score == 0.0 ? 0.0 : score;
            scores.add(new ApplicantScore(data.applicantIds().get(index), score,
                    data.comparisonCounts()[index]));
        }
        scores.sort(Comparator.comparingDouble(ApplicantScore::score).reversed()
                .thenComparing(applicantScore -> applicantScore.applicantId().value));

        List<RankingEntry> entries = new ArrayList<>();
        int position = 0;
        for (int index = 0; index < scores.size(); index++) {
            ApplicantScore applicantScore = scores.get(index);
            if (index == 0 || Double.compare(applicantScore.score(), scores.get(index - 1).score()) != 0) {
                position = index + 1;
            }
            entries.add(new RankingEntry(applicantScore.applicantId(), position,
                    OptionalDouble.of(applicantScore.score()), applicantScore.comparisonCount()));
        }
        return entries;
    }

    /**
     * Identifies an unordered applicant pair using ascending indices.
     */
    private record PairKey(int firstIndex, int secondIndex) {}

    /**
     * Holds the number of eligible comparisons for one unordered applicant pair.
     */
    private record PairCount(int firstIndex, int secondIndex, int comparisonCount) {}

    /**
     * Holds local aggregate statistics with deterministic applicant and pair order.
     */
    private record ComparisonData(List<ApplicantId> applicantIds, List<PairCount> pairs,
            int[] winCounts, int[] comparisonCounts) {}

    /**
     * Holds a canonical score and actual comparison count before positions are assigned.
     */
    private record ApplicantScore(ApplicantId applicantId, double score, int comparisonCount) {}
}
