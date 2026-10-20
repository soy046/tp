package seedu.address.model.comparison;

import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.ranking.BayesianRankingModel;
import seedu.address.model.ranking.ComparisonStatistics;
import seedu.address.model.ranking.ComparisonStatisticsIndex;
import seedu.address.model.ranking.PairInformationGain;
import seedu.address.model.ranking.RankingEntry;

/** Selects informative comparisons while covering every active applicant. */
public class PairSelector {
    private static final int SHORTLIST_LIMIT = 64;
    private final RandomGenerator random;

    public PairSelector() {
        this(ThreadLocalRandom.current());
    }

    public PairSelector(RandomGenerator random) {
        this.random = java.util.Objects.requireNonNull(random);
    }

    /** Compatibility entry point for callers supplying raw history. */
    public Optional<ComparisonPair> selectPair(Set<ApplicantId> activeMembers,
            List<ComparisonDecision> decisions, List<RankingEntry> ranking) {
        requireAllNonNull(activeMembers, decisions, ranking);
        ComparisonStatistics statistics = ComparisonStatisticsIndex.from(decisions).snapshot(activeMembers);
        BayesianRankingModel posterior;
        try {
            posterior = BayesianRankingModel.fit(statistics, null);
        } catch (IllegalStateException exception) {
            posterior = null;
        }
        return selectPair(statistics, ranking, posterior,
                decisions.isEmpty() ? Optional.empty() : Optional.of(decisions.get(decisions.size() - 1)),
                new ComparisonSettings());
    }

    /** Returns a pair without mutating the source statistics or ranking. */
    public Optional<ComparisonPair> selectPair(ComparisonStatistics statistics, List<RankingEntry> ranking,
            BayesianRankingModel posterior, Optional<ComparisonDecision> lastDecision,
            ComparisonSettings settings) {
        requireAllNonNull(statistics, ranking, lastDecision, settings);
        int size = statistics.size();
        if (size < 2) {
            return Optional.empty();
        }
        List<ApplicantId> ids = statistics.ids();
        List<int[]> candidates = new ArrayList<>();
        Set<Long> candidateKeys = new HashSet<>();
        List<Integer> ordered = new ArrayList<>(size);
        for (RankingEntry entry : ranking) {
            int index = statistics.indexOf(entry.getApplicantId());
            if (index >= 0) {
                ordered.add(index);
            }
        }
        if (ordered.size() != size) {
            ordered.clear();
            for (int i = 0; i < size; i++) {
                ordered.add(i);
            }
        }
        int least = 0;
        for (int i = 1; i < size; i++) {
            if (statistics.count(i) < statistics.count(least)) {
                least = i;
            }
        }
        boolean disconnected = !statistics.isConnected();
        int focus = disconnected && statistics.count(least) >= settings.getMinimumComparisons()
                ? ordered.get(random.nextInt(size)) : least;
        int focusPosition = ordered.indexOf(focus);
        for (int offset = 0; offset < size && candidates.size() < SHORTLIST_LIMIT / 2; offset++) {
            int other = ordered.get((focusPosition + offset + 1) % size);
            if (other != focus && (!disconnected || statistics.component(other) != statistics.component(focus))) {
                add(candidates, candidateKeys, focus, other);
            }
        }
        int rotation = random.nextInt(size);
        for (int offset = 0; offset < size && candidates.size() < SHORTLIST_LIMIT; offset++) {
            int position = (rotation + offset) % size;
            add(candidates, candidateKeys, ordered.get(position), ordered.get((position + 1) % size));
        }
        for (int attempts = 0; attempts < SHORTLIST_LIMIT * 4 && candidates.size() < SHORTLIST_LIMIT; attempts++) {
            add(candidates, candidateKeys, random.nextInt(size), random.nextInt(size));
        }
        if (candidates.isEmpty()) {
            add(candidates, candidateKeys, 0, 1);
        }
        boolean alternatives = size > 2 && candidates.stream().anyMatch(pair -> !isLast(pair, ids, lastDecision));
        int selected = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < candidates.size(); index++) {
            int[] pair = candidates.get(index);
            if (alternatives && isLast(pair, ids, lastDecision)) {
                continue;
            }
            double score = score(statistics, posterior, settings, pair[0], pair[1]);
            if (score > best || score == best && random.nextBoolean()) {
                best = score;
                selected = index;
            }
        }
        int[] pair = candidates.get(selected < 0 ? 0 : selected);
        return random.nextBoolean()
                ? Optional.of(new ComparisonPair(ids.get(pair[0]), ids.get(pair[1])))
                : Optional.of(new ComparisonPair(ids.get(pair[1]), ids.get(pair[0])));
    }

    private static double score(ComparisonStatistics statistics, BayesianRankingModel posterior,
            ComparisonSettings settings, int first, int second) {
        int floor = settings.getMinimumComparisons();
        int undercovered = Math.max(0, floor - statistics.count(first))
                + Math.max(0, floor - statistics.count(second));
        double componentPriority = statistics.component(first) == statistics.component(second) ? 0 : 1000;
        double coveragePriority = undercovered * 10;
        double sparsePriority = 1.0 / (1 + statistics.pairCount(first, second));
        if (posterior == null) {
            return componentPriority + coveragePriority + sparsePriority;
        }
        try {
            double gain = PairInformationGain.calculate(posterior.meanDifference(first, second),
                    posterior.varianceUpperBound(first, second));
            return componentPriority + coveragePriority + gain + 0.001 * sparsePriority;
        } catch (IllegalStateException exception) {
            return componentPriority + coveragePriority + sparsePriority;
        }
    }

    private static boolean isLast(int[] pair, List<ApplicantId> ids,
            Optional<ComparisonDecision> lastDecision) {
        if (lastDecision.isEmpty()) {
            return false;
        }
        ApplicantId first = ids.get(pair[0]);
        ApplicantId second = ids.get(pair[1]);
        ComparisonDecision last = lastDecision.get();
        return first.equals(last.getFirstApplicantId()) && second.equals(last.getSecondApplicantId())
                || first.equals(last.getSecondApplicantId()) && second.equals(last.getFirstApplicantId());
    }

    private static void add(List<int[]> pairs, Set<Long> keys, int first, int second) {
        if (first == second) {
            return;
        }
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        long key = ((long) low << 32) | (high & 0xffffffffL);
        if (keys.add(key)) {
            pairs.add(new int[] {low, high});
        }
    }
}
