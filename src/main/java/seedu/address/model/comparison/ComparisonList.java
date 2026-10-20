package seedu.address.model.comparison;

import static java.util.Objects.requireNonNull;
import static seedu.address.commons.util.AppUtil.checkArgument;
import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import seedu.address.commons.util.ToStringBuilder;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.applicant.exceptions.ApplicantNotFoundException;
import seedu.address.model.comparison.exceptions.DuplicateComparisonMemberException;
import seedu.address.model.ranking.BayesianRankingModel;
import seedu.address.model.ranking.ComparisonStatistics;
import seedu.address.model.ranking.ComparisonStatisticsIndex;
import seedu.address.model.ranking.RankingEngine;
import seedu.address.model.ranking.RankingReadiness;
import seedu.address.model.ranking.RankingReadinessEvaluator;

/**
 * Manages active comparison membership and the ordered history of decisions.
 *
 * <p>This class coordinates membership changes and completed decisions with the
 * applicant registry, pending comparison state, ranking engine, and pair selector.
 * It stores applicant IDs and decision records, rather than copies of applicant
 * records. The observable collections exposed by this class are unmodifiable views.
 */
public class ComparisonList implements ReadOnlyComparisonList {
    private final ApplicantRegistry applicantRegistry;
    private final ComparisonState comparisonState;
    private final RankingEngine rankingEngine;
    private final PairSelector pairSelector;
    private ComparisonSettings settings;
    private ComparisonStatisticsIndex statisticsIndex = ComparisonStatisticsIndex.from(List.of());
    private BayesianRankingModel posterior;
    private final ReadOnlyObjectWrapper<RankingReadiness> readiness = new ReadOnlyObjectWrapper<>(
            new RankingReadiness(RankingReadiness.Status.INSUFFICIENT_DATA,
                    "Add at least two applicants.", List.of()));

    private final ObservableSet<ApplicantId> memberIds = FXCollections.observableSet();
    private final ObservableSet<ApplicantId> unmodifiableMemberIds =
            FXCollections.unmodifiableObservableSet(memberIds);
    private final ObservableList<ComparisonDecision> decisionHistory =
            FXCollections.observableArrayList();
    private final ObservableList<ComparisonDecision> unmodifiableDecisionHistory =
            FXCollections.unmodifiableObservableList(decisionHistory);

    /**
     * Constructs a comparison list with the components required to coordinate comparisons.
     *
     * @param applicantRegistry the registry used to verify applicant records
     * @param comparisonState the state containing the pending pair
     * @param rankingEngine the engine used to recalculate the ranking
     * @throws NullPointerException if any collaborator is null
     */
    public ComparisonList(ApplicantRegistry applicantRegistry,
                          ComparisonState comparisonState,
                          RankingEngine rankingEngine) {
        this(applicantRegistry, comparisonState, rankingEngine, new ComparisonSettings());
    }

    /** Constructs a comparison list with explicit evidence settings. */
    public ComparisonList(ApplicantRegistry applicantRegistry,
            ComparisonState comparisonState, RankingEngine rankingEngine, ComparisonSettings settings) {
        this.applicantRegistry = requireNonNull(applicantRegistry);
        this.comparisonState = requireNonNull(comparisonState);
        this.rankingEngine = requireNonNull(rankingEngine);
        this.settings = requireNonNull(settings);
        pairSelector = new PairSelector();
    }

    public ReadOnlyObjectProperty<RankingReadiness> readinessProperty() {
        return readiness.getReadOnlyProperty();
    }

    public RankingReadiness getReadiness() {
        return readiness.get();
    }

    /** Applies new evidence thresholds without altering judgments or the pending pair. */
    public void setSettings(ComparisonSettings settings) {
        this.settings = requireNonNull(settings);
        refreshReadiness(statisticsIndex.snapshot(Set.copyOf(memberIds)));
    }

    /** Returns a live, unmodifiable observable set of unique active applicant IDs. */
    @Override
    public ObservableSet<ApplicantId> getMemberIds() {
        return unmodifiableMemberIds;
    }

    /** Returns a live, unmodifiable observable history in decision execution order. */
    @Override
    public ObservableList<ComparisonDecision> getDecisionHistory() {
        return unmodifiableDecisionHistory;
    }

    /** Returns the live state holding the pending comparison pair. */
    public ComparisonState getComparisonState() {
        return comparisonState;
    }

    @Override
    public Optional<ComparisonPair> getCurrentPair() {
        return comparisonState.getCurrentPair();
    }

    /**
     * Restores comparison data after validating all references against the registry.
     * Retains this object and its observable views, and rebuilds the runtime ranking.
     * Decisions involving inactive applicants are retained. A pending pair must contain active members.
     * Restoring does not record a decision or select a replacement pair.
     */
    public void resetData(Set<ApplicantId> members, List<ComparisonDecision> decisions,
            Optional<ComparisonPair> pendingPair) {
        requireAllNonNull(members, decisions, pendingPair);
        Set<ApplicantId> memberSnapshot = Set.copyOf(members);
        List<ComparisonDecision> decisionSnapshot = List.copyOf(decisions);
        for (ApplicantId member : memberSnapshot) {
            requireRegisteredApplicant(member);
        }
        for (ComparisonDecision decision : decisionSnapshot) {
            requireRegisteredApplicant(decision.getFirstApplicantId());
            requireRegisteredApplicant(decision.getSecondApplicantId());
        }
        pendingPair.ifPresent(pair -> checkArgument(
                memberSnapshot.contains(pair.getFirstApplicantId())
                        && memberSnapshot.contains(pair.getSecondApplicantId()),
                "A pending comparison pair must contain two active members."));

        // RankingEngine validates decision structure and IDs before publishing a complete ranking.
        ComparisonStatisticsIndex rebuilt = ComparisonStatisticsIndex.from(decisionSnapshot);
        ComparisonStatistics statistics = rebuilt.snapshot(memberSnapshot);
        rankingEngine.recalculate(statistics);
        memberIds.clear();
        memberIds.addAll(memberSnapshot);
        decisionHistory.setAll(decisionSnapshot);
        statisticsIndex = rebuilt;
        refreshReadiness(statistics);
        if (pendingPair.isPresent()) {
            comparisonState.setCurrentPair(pendingPair.get());
        } else {
            comparisonState.clearCurrentPair();
        }
    }

    private void requireRegisteredApplicant(ApplicantId applicantId) {
        if (!applicantRegistry.containsId(applicantId)) {
            throw new ApplicantNotFoundException();
        }
    }

    /**
     * Adds an existing applicant to the pool, without adding or copying a record.
     * Refreshes the ranking, making retained decisions eligible when both participants are active.
     * Selects an initial pair when none is pending, and preserves an existing pending pair.
     * Rejected membership changes leave all model state unchanged.
     * @throws NullPointerException if {@code applicantId} is null.
     * @throws ApplicantNotFoundException if the applicant has no record in the registry.
     * @throws DuplicateComparisonMemberException if the applicant is already an active member.
     */
    public void add(ApplicantId applicantId) {
        requireNonNull(applicantId);

        if (!applicantRegistry.containsId(applicantId)) {
            throw new ApplicantNotFoundException();
        }
        if (memberIds.contains(applicantId)) {
            throw new DuplicateComparisonMemberException();
        }

        memberIds.add(applicantId);
        recalculateRanking(statisticsIndex.snapshot(Set.copyOf(memberIds)));
        if (comparisonState.getCurrentPair().isEmpty()) {
            setNextPairOrClear();
        }
    }

    /**
     * Removes membership while retaining the applicant record and decision history.
     * Refreshes the ranking, excluding decisions involving inactive applicants,
     * and clears or replaces a current pair that is no longer valid.
     *
     * <p>Removing an ID that is not currently a member has no effect.
     */
    public void remove(ApplicantId applicantId) {
        requireNonNull(applicantId);

        if (!memberIds.remove(applicantId)) {
            return;
        }

        recalculateRanking(statisticsIndex.snapshot(Set.copyOf(memberIds)));
        replaceCurrentPairIfInvalid();
    }

    /**
     * Records a new judgment for the current pair and refreshes the ranking as one successful operation.
     * Both participants must still be active and the winner must belong to the current pair.
     * A rejected judgment changes neither history nor ranking. A completed pair is no longer pending.
     *
     * <p>After recording a decision, this method selects and saves a subsequent pair.
     * If no pair is available, it clears the pending pair.
     *
     * @throws NullPointerException if {@code winnerId} is null
     * @throws IllegalStateException if there is no pending pair, or if either participant
     *         is no longer an active member or registered applicant
     * @throws IllegalArgumentException if {@code winnerId} is not one of the pending pair's
     *         applicants
     */
    public ComparisonDecision recordDecision(ApplicantId winnerId) {
        requireNonNull(winnerId);

        ComparisonPair currentPair = comparisonState.getCurrentPair()
                .orElseThrow(() -> new IllegalStateException("There is no pending comparison pair."));
        ApplicantId firstApplicantId = currentPair.getFirstApplicantId();
        ApplicantId secondApplicantId = currentPair.getSecondApplicantId();

        if (!memberIds.contains(firstApplicantId) || !memberIds.contains(secondApplicantId)
                || !applicantRegistry.containsId(firstApplicantId)
                || !applicantRegistry.containsId(secondApplicantId)) {
            throw new IllegalStateException("The pending pair is no longer valid.");
        }
        if (!winnerId.equals(firstApplicantId) && !winnerId.equals(secondApplicantId)) {
            throw new IllegalArgumentException("The winner must be one of the applicants in the pending pair.");
        }

        ComparisonDecision decision =
                new ComparisonDecision(firstApplicantId, secondApplicantId, winnerId);
        ComparisonStatistics projected = statisticsIndex.snapshotWith(Set.copyOf(memberIds), decision);
        recalculateRanking(projected);
        statisticsIndex.record(decision);
        decisionHistory.add(decision);
        setNextPairOrClear();

        return decision;
    }

    /**
     * Removes membership and all decisions involving an applicant being permanently deleted.
     * Refreshes the ranking and invalidates an affected pending pair. The registry coordinates
     * this operation with record deletion; commands only request deletion through the registry.
     *
     * <p>This method removes the applicant from active membership and removes
     * decisions involving that applicant. The applicant record itself is retained
     * for the registry to delete as part of its coordinated operation.
     *
     * @throws NullPointerException if {@code applicantId} is null
     */
    public void deleteApplicantReferences(ApplicantId applicantId) {
        requireNonNull(applicantId);

        memberIds.remove(applicantId);
        decisionHistory.removeIf(decision ->
                decision.getFirstApplicantId().equals(applicantId)
                        || decision.getSecondApplicantId().equals(applicantId));

        statisticsIndex = ComparisonStatisticsIndex.from(List.copyOf(decisionHistory));
        recalculateRanking(statisticsIndex.snapshot(Set.copyOf(memberIds)));
        replaceCurrentPairIfInvalid();
    }

    /**
     * Recalculates the ranking from the given decision history and current membership.
     *
     * @param decisions the decisions to use when rebuilding the ranking
     */
    private void recalculateRanking(ComparisonStatistics statistics) {
        rankingEngine.recalculate(statistics);
        refreshReadiness(statistics);
    }

    private void refreshReadiness(ComparisonStatistics statistics) {
        try {
            posterior = BayesianRankingModel.fit(statistics, posterior);
        } catch (IllegalStateException exception) {
            posterior = null;
        }
        readiness.set(RankingReadinessEvaluator.evaluate(statistics,
                List.copyOf(rankingEngine.getRanking()), posterior, settings));
    }

    /**
     * Replaces the pending pair if one of its applicants is no longer eligible.
     */
    private void replaceCurrentPairIfInvalid() {
        Optional<ComparisonPair> currentPair = comparisonState.getCurrentPair();
        if (currentPair.isEmpty()) {
            return;
        }

        ComparisonPair pair = currentPair.get();
        boolean firstApplicantIsActive = memberIds.contains(pair.getFirstApplicantId())
                && applicantRegistry.containsId(pair.getFirstApplicantId());
        boolean secondApplicantIsActive = memberIds.contains(pair.getSecondApplicantId())
                && applicantRegistry.containsId(pair.getSecondApplicantId());

        if (!firstApplicantIsActive || !secondApplicantIsActive) {
            setNextPairOrClear();
        }
    }

    /**
     * Selects and saves a subsequent pair, or clears the pending pair if none is available.
     */
    private void setNextPairOrClear() {
        Optional<ComparisonPair> nextPair = pairSelector.selectPair(
                statisticsIndex.snapshot(Set.copyOf(memberIds)), List.copyOf(rankingEngine.getRanking()), posterior,
                decisionHistory.isEmpty() ? Optional.empty()
                        : Optional.of(decisionHistory.get(decisionHistory.size() - 1)), settings);

        if (nextPair.isPresent()) {
            comparisonState.setCurrentPair(nextPair.get());
        } else {
            comparisonState.clearCurrentPair();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof ComparisonList otherList
                && memberIds.equals(otherList.memberIds)
                && decisionHistory.equals(otherList.decisionHistory)
                && getCurrentPair().equals(otherList.getCurrentPair());
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberIds, decisionHistory, getCurrentPair());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .add("memberIds", memberIds)
                .add("decisionHistory", decisionHistory)
                .add("currentPair", getCurrentPair())
                .toString();
    }
}
