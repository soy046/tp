package seedu.address.model.comparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.testutil.Assert.assertThrows;
import static seedu.address.testutil.TypicalApplicants.ALICE;
import static seedu.address.testutil.TypicalApplicants.BENSON;
import static seedu.address.testutil.TypicalApplicants.CARL;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.applicant.exceptions.ApplicantNotFoundException;
import seedu.address.model.comparison.exceptions.DuplicateComparisonMemberException;
import seedu.address.model.ranking.RankingEngine;
import seedu.address.model.ranking.RankingEntry;

public class ComparisonListTest {

    private ApplicantRegistry applicantRegistry;
    private ComparisonState comparisonState;
    private FakeRankingEngine rankingEngine;
    private ComparisonList comparisonList;

    @BeforeEach
    public void setUp() {
        applicantRegistry = new ApplicantRegistry();
        applicantRegistry.add(ALICE);
        applicantRegistry.add(BENSON);
        applicantRegistry.add(CARL);

        comparisonState = new ComparisonState(applicantRegistry);
        rankingEngine = new FakeRankingEngine();
        comparisonList = new ComparisonList(
                applicantRegistry, comparisonState, rankingEngine);
    }

    @Test
    public void add_registeredApplicant_addsMemberAndRecalculatesRanking() {
        ApplicantId applicantId = ALICE.getId();

        comparisonList.add(applicantId);

        assertEquals(Set.of(applicantId), comparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
        assertEquals(Set.of(applicantId), rankingEngine.lastActiveMembers);
        assertEquals(List.of(), rankingEngine.lastDecisions);
        assertTrue(comparisonList.getCurrentPair().isEmpty());
    }

    @Test
    public void add_secondMember_selectsInitialPairAndPreservesItOnFurtherAdditions() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        assertCurrentPairContains(Set.of(ALICE.getId(), BENSON.getId()));
        Optional<ComparisonPair> initialPair = comparisonList.getCurrentPair();

        comparisonList.add(CARL.getId());

        assertEquals(initialPair, comparisonList.getCurrentPair());
        assertTrue(comparisonList.getDecisionHistory().isEmpty());
    }

    @Test
    public void add_afterPoolDropsBelowTwoMembers_selectsPairWhenPoolGrowsAgain() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.remove(ALICE.getId());
        assertTrue(comparisonList.getCurrentPair().isEmpty());

        comparisonList.add(CARL.getId());

        assertCurrentPairContains(Set.of(BENSON.getId(), CARL.getId()));
    }

    @Test
    public void add_unregisteredApplicant_throwsApplicantNotFoundException() {
        ApplicantId unregisteredId = new ApplicantId();

        assertThrows(ApplicantNotFoundException.class, () -> comparisonList.add(unregisteredId));

        assertTrue(comparisonList.getMemberIds().isEmpty());
        assertEquals(0, rankingEngine.recalculationCount);
    }

    @Test
    public void add_existingMember_throwsDuplicateComparisonMemberException() {
        comparisonList.add(ALICE.getId());

        assertThrows(DuplicateComparisonMemberException.class, () ->
                comparisonList.add(ALICE.getId()));

        assertEquals(Set.of(ALICE.getId()), comparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
    }

    @Test
    public void add_nullApplicantId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> comparisonList.add(null));

        assertTrue(comparisonList.getMemberIds().isEmpty());
        assertEquals(0, rankingEngine.recalculationCount);
    }

    @Test
    public void getMemberIds_unmodifiableView_rejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () ->
                comparisonList.getMemberIds().add(ALICE.getId()));
    }

    @Test
    public void getDecisionHistory_unmodifiableView_rejectsMutation() {
        assertThrows(UnsupportedOperationException.class, () ->
                comparisonList.getDecisionHistory().add(
                        new ComparisonDecision(
                                ALICE.getId(), BENSON.getId(), ALICE.getId())));
    }

    @Test
    public void remove_activeMember_removesMemberAndRecalculatesRanking() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());

        comparisonList.remove(ALICE.getId());

        assertEquals(Set.of(BENSON.getId()), comparisonList.getMemberIds());
        assertEquals(3, rankingEngine.recalculationCount);
        assertEquals(Set.of(BENSON.getId()), rankingEngine.lastActiveMembers);
    }

    @Test
    public void remove_nonMember_leavesStateUnchanged() {
        comparisonList.add(ALICE.getId());

        comparisonList.remove(BENSON.getId());

        assertEquals(Set.of(ALICE.getId()), comparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
    }

    @Test
    public void remove_nullApplicantId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> comparisonList.remove(null));

        assertTrue(comparisonList.getMemberIds().isEmpty());
        assertEquals(0, rankingEngine.recalculationCount);
    }

    @Test
    public void remove_memberInCurrentPair_replacesCurrentPair() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());

        ComparisonPair currentPair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        comparisonState.setCurrentPair(currentPair);

        comparisonList.remove(ALICE.getId());

        assertCurrentPairContains(Set.of(BENSON.getId(), CARL.getId()));
    }

    @Test
    public void remove_memberNotInCurrentPair_keepsCurrentPair() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());

        ComparisonPair currentPair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        comparisonState.setCurrentPair(currentPair);

        comparisonList.remove(CARL.getId());

        assertEquals(Optional.of(currentPair), comparisonState.getCurrentPair());
    }

    @Test
    public void recordDecision_validWinner_recordsDecisionAndSelectsNextPair() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());

        ComparisonPair currentPair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        comparisonState.setCurrentPair(currentPair);

        ComparisonDecision decision = comparisonList.recordDecision(ALICE.getId());

        assertEquals(List.of(decision), comparisonList.getDecisionHistory());
        assertEquals(ALICE.getId(), decision.getWinnerId());
        assertEquals(ALICE.getId(), decision.getFirstApplicantId());
        assertEquals(BENSON.getId(), decision.getSecondApplicantId());
        assertEquals(List.of(decision), comparisonList.getDecisionHistory());
        ComparisonPair nextPair = comparisonList.getCurrentPair().orElseThrow();
        assertNotEquals(nextPair.getFirstApplicantId(), nextPair.getSecondApplicantId());
        assertTrue(comparisonList.getMemberIds().contains(nextPair.getFirstApplicantId()));
        assertTrue(comparisonList.getMemberIds().contains(nextPair.getSecondApplicantId()));
    }

    @Test
    public void recordDecision_noPendingPair_throwsIllegalStateException() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonState.clearCurrentPair();

        assertThrows(IllegalStateException.class, () ->
                comparisonList.recordDecision(ALICE.getId()));

        assertTrue(comparisonList.getDecisionHistory().isEmpty());
        assertEquals(2, rankingEngine.recalculationCount);
    }

    @Test
    public void recordDecision_winnerNotInPair_throwsIllegalArgumentException() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());
        comparisonState.setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        assertThrows(IllegalArgumentException.class, () ->
                comparisonList.recordDecision(CARL.getId()));

        assertTrue(comparisonList.getDecisionHistory().isEmpty());
        assertEquals(3, rankingEngine.recalculationCount);
    }

    @Test
    public void recordDecision_nullWinner_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                comparisonList.recordDecision(null));

        assertTrue(comparisonList.getDecisionHistory().isEmpty());
    }

    @Test
    public void recordDecision_twoActiveMembers_selectsSameApplicantsForNextComparison() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonState.setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        comparisonList.recordDecision(BENSON.getId());

        assertCurrentPairContains(Set.of(ALICE.getId(), BENSON.getId()));
    }

    @Test
    public void deleteApplicantReferences_removesMembershipAndInvalidatesPair() {
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonState.setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        comparisonList.deleteApplicantReferences(ALICE.getId());

        assertEquals(Set.of(BENSON.getId()), comparisonList.getMemberIds());
        assertTrue(comparisonState.getCurrentPair().isEmpty());
        assertEquals(Set.of(BENSON.getId()), rankingEngine.lastActiveMembers);
    }

    @Test
    public void deleteApplicantReferences_nullApplicantId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                comparisonList.deleteApplicantReferences(null));

        assertTrue(comparisonList.getMemberIds().isEmpty());
        assertEquals(0, rankingEngine.recalculationCount);
    }

    private static class FakeRankingEngine extends RankingEngine {
        private final ObservableList<RankingEntry> ranking = FXCollections.observableArrayList();
        private int recalculationCount;
        private Set<ApplicantId> lastActiveMembers = Set.of();
        private List<ComparisonDecision> lastDecisions = List.of();

        @Override
        public ObservableList<RankingEntry> getRanking() {
            return FXCollections.unmodifiableObservableList(ranking);
        }

        @Override
        public void recalculate(Set<ApplicantId> activeMembers, List<ComparisonDecision> decisions) {
            recalculationCount++;
            lastActiveMembers = Set.copyOf(activeMembers);
            lastDecisions = List.copyOf(decisions);
        }

        @Override
        public void recalculate(seedu.address.model.ranking.ComparisonStatistics statistics) {
            recalculationCount++;
            lastActiveMembers = Set.copyOf(statistics.ids());
        }
    }

    private void assertCurrentPairContains(Set<ApplicantId> expectedMembers) {
        ComparisonPair pair = comparisonList.getCurrentPair().orElseThrow();
        assertEquals(expectedMembers, Set.of(pair.getFirstApplicantId(), pair.getSecondApplicantId()));
    }
}
