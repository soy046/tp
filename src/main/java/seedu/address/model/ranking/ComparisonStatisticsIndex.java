package seedu.address.model.ranking;

import static seedu.address.commons.util.AppUtil.checkArgument;
import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.comparison.ComparisonDecision;

/** Retains per-pair aggregates for all decisions, including currently inactive applicants. */
public final class ComparisonStatisticsIndex {
    /** Outcome counts oriented by the key's applicant IDs. */
    public record PairTally(ApplicantId first, ApplicantId second, int firstWins, int secondWins) {}

    private record PairKey(ApplicantId first, ApplicantId second) {
        private static PairKey of(ApplicantId first, ApplicantId second) {
            return first.value.compareTo(second.value) < 0
                    ? new PairKey(first, second) : new PairKey(second, first);
        }
    }

    private final Map<PairKey, PairTally> tallies = new HashMap<>();
    private final Set<UUID> decisionIds = new HashSet<>();

    /** Builds and validates an index over all stored decisions. */
    public static ComparisonStatisticsIndex from(List<ComparisonDecision> decisions) {
        requireAllNonNull(decisions);
        ComparisonStatisticsIndex index = new ComparisonStatisticsIndex();
        for (ComparisonDecision decision : decisions) {
            index.record(decision);
        }
        return index;
    }

    /** Adds one completed decision, rejecting duplicate decision identity. */
    public void record(ComparisonDecision decision) {
        requireAllNonNull(decision);
        requireAllNonNull(decision.getDecisionId(), decision.getFirstApplicantId(),
                decision.getSecondApplicantId(), decision.getWinnerId());
        checkArgument(!decision.getFirstApplicantId().equals(decision.getSecondApplicantId()),
                "A ranking decision must contain two distinct applicants.");
        checkArgument(decision.getWinnerId().equals(decision.getFirstApplicantId())
                || decision.getWinnerId().equals(decision.getSecondApplicantId()),
                "The winner must be a participant in the ranking decision.");
        checkArgument(decisionIds.add(decision.getDecisionId()),
                "Decision IDs must be unique within the ranking history.");
        PairKey key = PairKey.of(decision.getFirstApplicantId(), decision.getSecondApplicantId());
        PairTally previous = tallies.get(key);
        int firstWins = previous == null ? 0 : previous.firstWins();
        int secondWins = previous == null ? 0 : previous.secondWins();
        if (decision.getWinnerId().equals(key.first())) {
            firstWins++;
        } else {
            secondWins++;
        }
        tallies.put(key, new PairTally(key.first(), key.second(), firstWins, secondWins));
    }

    public ComparisonStatistics snapshot(Set<ApplicantId> activeMembers) {
        return new ComparisonStatistics(activeMembers, new ArrayList<>(tallies.values()));
    }

    /** Returns projected statistics without changing the committed index. */
    public ComparisonStatistics snapshotWith(Set<ApplicantId> activeMembers, ComparisonDecision decision) {
        requireAllNonNull(activeMembers, decision);
        checkArgument(!decisionIds.contains(decision.getDecisionId()),
                "Decision IDs must be unique within the ranking history.");
        ComparisonStatisticsIndex projected = new ComparisonStatisticsIndex();
        projected.tallies.putAll(tallies);
        projected.record(decision);
        return projected.snapshot(activeMembers);
    }
}
