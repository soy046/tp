package seedu.address.logic.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.logic.commands.CommandTestUtil.assertCommandFailure;
import static seedu.address.testutil.Assert.assertThrows;
import static seedu.address.testutil.TypicalApplicants.ALICE;
import static seedu.address.testutil.TypicalApplicants.BENSON;
import static seedu.address.testutil.TypicalApplicants.CARL;
import static seedu.address.testutil.TypicalApplicants.HOON;
import static seedu.address.testutil.TypicalApplicants.getTypicalApplicants;
import static seedu.address.testutil.TypicalApplicants.getTypicalRecruitDexData;
import static seedu.address.testutil.TypicalIndexes.INDEX_FIRST_APPLICANT;
import static seedu.address.testutil.TypicalIndexes.INDEX_SECOND_APPLICANT;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import seedu.address.commons.core.index.Index;
import seedu.address.logic.Messages;
import seedu.address.logic.parser.RecruitDexParser;
import seedu.address.model.ModelManager;
import seedu.address.model.RecruitDexData;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.comparison.ComparisonDecision;
import seedu.address.model.comparison.ComparisonList;
import seedu.address.model.comparison.ComparisonPair;
import seedu.address.model.comparison.ComparisonState;
import seedu.address.model.ranking.RankingEngine;
import seedu.address.model.ranking.RankingEntry;
import seedu.address.model.userprefs.UserPrefs;

/**
 * Tests command delegation in isolation and integration with the concrete comparison list and state.
 * Uses ranking spies for delegation checks and the real engine when testing the default ModelManager.
 */
public class RemoveFromComparisonCommandTest {
    private final ComparisonListStub comparisonList = new ComparisonListStub();
    private final ModelWithComparisonList model = new ModelWithComparisonList(comparisonList);

    @Test
    public void constructor_nullIndex_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new RemoveFromComparisonCommand(null));
    }

    @Test
    public void execute_nullModel_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(null));
    }

    @Test
    public void execute_validIndexUnfilteredList_delegatesIdAndPreservesRecords() throws Exception {
        List<Applicant> displayedBefore = List.copyOf(model.getFilteredApplicantList());
        RecruitDexData dataBefore = new RecruitDexData(model.getRecruitDexData());
        Index lastIndex = Index.fromOneBased(displayedBefore.size());
        Applicant selectedApplicant = displayedBefore.get(lastIndex.getZeroBased());

        Command command = new RecruitDexParser().parseCommand(
                RemoveFromComparisonCommand.COMMAND_WORD + " " + lastIndex.getOneBased());
        CommandResult result = command.execute(model);

        assertEquals(String.format(RemoveFromComparisonCommand.MESSAGE_SUCCESS, Messages.format(selectedApplicant)),
                result.getFeedbackToUser());
        assertEquals(List.of(selectedApplicant.getId()), comparisonList.removedApplicantIds);
        assertEquals(dataBefore, model.getRecruitDexData());
        assertEquals(displayedBefore, model.getFilteredApplicantList());
    }

    @Test
    public void execute_filterChangedAfterConstruction_resolvesCurrentDisplayedIndex() throws Exception {
        RemoveFromComparisonCommand command = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT);
        model.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        RecruitDexData dataBefore = new RecruitDexData(model.getRecruitDexData());

        CommandResult result = command.execute(model);

        assertEquals(String.format(RemoveFromComparisonCommand.MESSAGE_SUCCESS, Messages.format(BENSON)),
                result.getFeedbackToUser());
        assertEquals(List.of(BENSON.getId()), comparisonList.removedApplicantIds);
        assertEquals(dataBefore, model.getRecruitDexData());
        assertEquals(List.of(BENSON), model.getFilteredApplicantList());
    }

    @Test
    public void execute_indexPastUnfilteredList_throwsCommandException() {
        Index invalidIndex = Index.fromOneBased(model.getFilteredApplicantList().size() + 1);

        assertCommandFailure(new RemoveFromComparisonCommand(invalidIndex), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
        assertTrue(comparisonList.removedApplicantIds.isEmpty());
    }

    @Test
    public void execute_indexPastFilteredList_throwsCommandException() {
        model.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        assertTrue(INDEX_SECOND_APPLICANT.getZeroBased() < model.getRecruitDexData().getApplicantList().size());

        assertCommandFailure(new RemoveFromComparisonCommand(INDEX_SECOND_APPLICANT), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
        assertTrue(comparisonList.removedApplicantIds.isEmpty());
    }

    @Test
    public void execute_emptyDisplayedList_throwsCommandException() {
        model.updateFilteredApplicantList(applicant -> false);

        assertCommandFailure(new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
    }

    @Test
    public void execute_realComparisonList_nonMemberLeavesStateUnchanged() throws Exception {
        RankingEngineStub rankingEngine = new RankingEngineStub();
        ModelWithComparisonList connectedModel = new ModelWithComparisonList(rankingEngine);
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        realComparisonList.add(ALICE.getId());
        connectedModel.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        List<Applicant> recordsBefore = List.copyOf(connectedModel.getApplicantRegistry().getApplicantList());

        CommandResult result = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(RemoveFromComparisonCommand.MESSAGE_NOT_A_MEMBER, result.getFeedbackToUser());
        assertEquals(Set.of(ALICE.getId()), realComparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
        assertEquals(Set.of(ALICE.getId()), rankingEngine.lastActiveMembers);
        assertTrue(rankingEngine.lastDecisions.isEmpty());
        assertTrue(realComparisonList.getDecisionHistory().isEmpty());
        assertTrue(connectedModel.getComparisonList().getCurrentPair().isEmpty());
        assertEquals(recordsBefore, connectedModel.getApplicantRegistry().getApplicantList());
        assertEquals(List.of(BENSON), connectedModel.getFilteredApplicantList());
    }

    @Test
    public void execute_defaultModelManager_removesMembershipAndUpdatesRanking() throws Exception {
        ModelManager liveModel = new ModelManager(getTypicalRecruitDexData(), new UserPrefs());
        liveModel.getComparisonList().add(ALICE.getId());
        liveModel.getComparisonList().add(BENSON.getId());
        List<Applicant> recordsBefore = List.copyOf(liveModel.getApplicantRegistry().getApplicantList());

        CommandResult result = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(liveModel);

        assertEquals(String.format(RemoveFromComparisonCommand.MESSAGE_SUCCESS, Messages.format(ALICE)),
                result.getFeedbackToUser());
        assertEquals(Set.of(BENSON.getId()), liveModel.getComparisonList().getMemberIds());
        assertEquals(1, liveModel.getRankingEngine().getRanking().size());
        assertEquals(BENSON.getId(), liveModel.getRankingEngine().getRanking().get(0).getApplicantId());
        assertEquals(recordsBefore, liveModel.getApplicantRegistry().getApplicantList());
        assertEquals(recordsBefore, liveModel.getFilteredApplicantList());
    }

    @Test
    public void execute_unexpectedUnsupportedRemove_propagatesException() {
        comparisonList.failure = new UnsupportedOperationException("Unexpected model failure");

        assertThrows(UnsupportedOperationException.class, "Unexpected model failure", () ->
                new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(model));
    }

    @Test
    public void execute_realComparisonList_removesFilteredApplicantAndRefreshesRanking() throws Exception {
        RankingEngineStub rankingEngine = new RankingEngineStub();
        ModelWithComparisonList connectedModel = new ModelWithComparisonList(rankingEngine);
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        realComparisonList.add(ALICE.getId());
        realComparisonList.add(BENSON.getId());
        connectedModel.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        List<Applicant> recordsBefore = List.copyOf(connectedModel.getApplicantRegistry().getApplicantList());

        CommandResult result = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(String.format(RemoveFromComparisonCommand.MESSAGE_SUCCESS, Messages.format(BENSON)),
                result.getFeedbackToUser());
        assertEquals(Set.of(ALICE.getId()), realComparisonList.getMemberIds());
        assertEquals(3, rankingEngine.recalculationCount);
        assertEquals(Set.of(ALICE.getId()), rankingEngine.lastActiveMembers);
        assertTrue(rankingEngine.lastDecisions.isEmpty());
        assertTrue(realComparisonList.getDecisionHistory().isEmpty());
        assertTrue(connectedModel.getComparisonList().getCurrentPair().isEmpty());
        assertEquals(recordsBefore, connectedModel.getApplicantRegistry().getApplicantList());
        assertEquals(List.of(BENSON), connectedModel.getFilteredApplicantList());
    }

    @Test
    public void execute_nonMember_stillDelegatesRemovalToModel() throws Exception {
        model.getApplicantRegistry().add(HOON);
        RecruitDexData dataBefore = new RecruitDexData(model.getRecruitDexData());
        Index index = Index.fromOneBased(model.getFilteredApplicantList().size());
        assertFalse(comparisonList.getMemberIds().contains(HOON.getId()));

        CommandResult result = new RemoveFromComparisonCommand(index).execute(model);

        assertEquals(RemoveFromComparisonCommand.MESSAGE_NOT_A_MEMBER, result.getFeedbackToUser());
        assertEquals(List.of(HOON.getId()), comparisonList.removedApplicantIds);
        assertEquals(dataBefore, model.getRecruitDexData());
    }

    @Test
    public void execute_defaultModelManager_retainsHistoryAndClearsAffectedPair() throws Exception {
        ModelManager connectedModel = new ModelManager(getTypicalRecruitDexData(), new UserPrefs());
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        realComparisonList.add(ALICE.getId());
        realComparisonList.add(BENSON.getId());
        realComparisonList.add(CARL.getId());
        ComparisonPair pair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        connectedModel.getComparisonList().getComparisonState().setCurrentPair(pair);
        ComparisonDecision decision = realComparisonList.recordDecision(ALICE.getId());
        connectedModel.getComparisonList().getComparisonState().setCurrentPair(pair);
        connectedModel.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        List<Applicant> recordsBefore = List.copyOf(connectedModel.getApplicantRegistry().getApplicantList());

        new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(Set.of(ALICE.getId(), CARL.getId()), realComparisonList.getMemberIds());
        assertEquals(List.of(decision), realComparisonList.getDecisionHistory());
        ComparisonPair replacementPair = realComparisonList.getCurrentPair().orElseThrow();
        assertEquals(Set.of(ALICE.getId(), CARL.getId()),
                Set.of(replacementPair.getFirstApplicantId(), replacementPair.getSecondApplicantId()));
        ObservableList<RankingEntry> ranking = connectedModel.getRankingEngine().getRanking();
        assertEquals(2, ranking.size());
        assertTrue(ranking.stream().allMatch(entry -> entry.getComparisonCount() == 0));
        assertTrue(ranking.stream().noneMatch(entry -> entry.getApplicantId().equals(BENSON.getId())));
        assertEquals(recordsBefore, connectedModel.getApplicantRegistry().getApplicantList());
        assertEquals(List.of(BENSON), connectedModel.getFilteredApplicantList());

        new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(List.of(decision), realComparisonList.getDecisionHistory());
        assertEquals(3, ranking.size());
        assertEquals(ALICE.getId(), ranking.get(0).getApplicantId());
        assertEquals(1, ranking.get(0).getComparisonCount());
        assertEquals(1, ranking.stream().filter(entry -> entry.getApplicantId().equals(BENSON.getId()))
                .findFirst().orElseThrow().getComparisonCount());
    }

    @Test
    public void equals() {
        RemoveFromComparisonCommand firstCommand = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT);
        RemoveFromComparisonCommand secondCommand = new RemoveFromComparisonCommand(INDEX_SECOND_APPLICANT);

        assertTrue(firstCommand.equals(firstCommand));
        assertTrue(firstCommand.equals(new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT)));
        assertFalse(firstCommand.equals(secondCommand));
        assertFalse(firstCommand.equals(null));
        assertFalse(firstCommand.equals(new AddToComparisonCommand(INDEX_FIRST_APPLICANT)));
    }

    @Test
    public void toStringMethod() {
        RemoveFromComparisonCommand command = new RemoveFromComparisonCommand(INDEX_FIRST_APPLICANT);
        assertEquals(RemoveFromComparisonCommand.class.getCanonicalName()
                + "{targetIndex=" + INDEX_FIRST_APPLICANT + "}", command.toString());
    }

    /**
     * Supplies either a comparison-list test double or a concrete list sharing the real registry and state.
     */
    private static class ModelWithComparisonList extends ModelManager {
        private final ComparisonList comparisonList;
        private int comparisonListAccessCount;

        ModelWithComparisonList(ComparisonList comparisonList) {
            super(getTypicalRecruitDexData(), new UserPrefs());
            this.comparisonList = comparisonList;
        }

        ModelWithComparisonList(RankingEngine rankingEngine) {
            super(getTypicalRecruitDexData(), new UserPrefs());
            comparisonList = new ComparisonList(getApplicantRegistry(), super.getComparisonList().getComparisonState(),
                    rankingEngine);
        }

        @Override
        public ComparisonList getComparisonList() {
            comparisonListAccessCount++;
            return comparisonList;
        }
    }

    /**
     * Captures successful removals or simulates a model rejection. Other operations are forbidden in this command.
     */
    private static class ComparisonListStub extends ComparisonList {
        private final List<ApplicantId> removedApplicantIds = new ArrayList<>();
        private RuntimeException failure;

        ComparisonListStub() {
            this(new ApplicantRegistry());
        }

        private ComparisonListStub(ApplicantRegistry registry) {
            super(registry, new ComparisonState(registry),
                    new RankingEngineStub());
            for (Applicant applicant : getTypicalApplicants()) {
                registry.add(applicant);
                super.add(applicant.getId());
            }
        }

        @Override
        public void remove(ApplicantId applicantId) {
            if (failure != null) {
                throw failure;
            }
            removedApplicantIds.add(applicantId);
        }

        @Override
        public ObservableList<ComparisonDecision> getDecisionHistory() {
            throw new AssertionError("Command must not access decision history.");
        }

        @Override
        public void add(ApplicantId applicantId) {
            throw new AssertionError("Command must not add membership.");
        }

        @Override
        public ComparisonDecision recordDecision(ApplicantId winnerId) {
            throw new AssertionError("Command must not record decisions.");
        }

        @Override
        public void deleteApplicantReferences(ApplicantId applicantId) {
            throw new AssertionError("Command must not delete applicant references.");
        }
    }

    /**
     * Records ranking refresh requests without inventing a ranking algorithm for the tests.
     */
    private static class RankingEngineStub extends RankingEngine {
        private final ObservableList<RankingEntry> ranking =
                FXCollections.unmodifiableObservableList(FXCollections.observableArrayList());
        private int recalculationCount;
        private Set<ApplicantId> lastActiveMembers = Set.of();
        private List<ComparisonDecision> lastDecisions = List.of();

        @Override
        public ObservableList<RankingEntry> getRanking() {
            return ranking;
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
}
