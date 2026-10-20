package seedu.address.logic.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.logic.commands.CommandTestUtil.assertCommandFailure;
import static seedu.address.testutil.Assert.assertThrows;
import static seedu.address.testutil.TypicalApplicants.ALICE;
import static seedu.address.testutil.TypicalApplicants.BENSON;
import static seedu.address.testutil.TypicalApplicants.CARL;
import static seedu.address.testutil.TypicalApplicants.getTypicalRecruitDexData;
import static seedu.address.testutil.TypicalIndexes.INDEX_FIRST_APPLICANT;
import static seedu.address.testutil.TypicalIndexes.INDEX_SECOND_APPLICANT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import seedu.address.commons.core.index.Index;
import seedu.address.logic.Messages;
import seedu.address.logic.parser.RecruitDexParser;
import seedu.address.model.ModelManager;
import seedu.address.model.RecruitDexData;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.applicant.exceptions.ApplicantNotFoundException;
import seedu.address.model.comparison.ComparisonDecision;
import seedu.address.model.comparison.ComparisonList;
import seedu.address.model.comparison.ComparisonPair;
import seedu.address.model.comparison.ComparisonState;
import seedu.address.model.comparison.exceptions.DuplicateComparisonMemberException;
import seedu.address.model.ranking.RankingEngine;
import seedu.address.model.ranking.RankingEntry;
import seedu.address.model.userprefs.UserPrefs;

/**
 * Tests command delegation in isolation and integration with the concrete comparison list and state.
 * Uses ranking spies for delegation checks and the real engine when testing the default ModelManager.
 */
public class AddToComparisonCommandTest {
    private final ComparisonListStub comparisonList = new ComparisonListStub();
    private final ModelWithComparisonList model = new ModelWithComparisonList(comparisonList);

    @Test
    public void constructor_nullIndex_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new AddToComparisonCommand(null));
    }

    @Test
    public void execute_nullModel_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(null));
    }

    @Test
    public void execute_validIndexUnfilteredList_delegatesIdAndPreservesRecords() throws Exception {
        List<Applicant> displayedBefore = List.copyOf(model.getFilteredApplicantList());
        RecruitDexData dataBefore = new RecruitDexData(model.getRecruitDexData());
        Index lastIndex = Index.fromOneBased(displayedBefore.size());
        Applicant selectedApplicant = displayedBefore.get(lastIndex.getZeroBased());

        Command command = new RecruitDexParser().parseCommand(
                AddToComparisonCommand.COMMAND_WORD + " " + lastIndex.getOneBased());
        CommandResult result = command.execute(model);

        assertEquals(String.format(AddToComparisonCommand.MESSAGE_SUCCESS, Messages.format(selectedApplicant)),
                result.getFeedbackToUser());
        assertEquals(List.of(selectedApplicant.getId()), comparisonList.addedApplicantIds);
        assertEquals(dataBefore, model.getRecruitDexData());
        assertEquals(displayedBefore, model.getFilteredApplicantList());
    }

    @Test
    public void execute_filterChangedAfterConstruction_resolvesCurrentDisplayedIndex() throws Exception {
        AddToComparisonCommand command = new AddToComparisonCommand(INDEX_FIRST_APPLICANT);
        model.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        RecruitDexData dataBefore = new RecruitDexData(model.getRecruitDexData());

        CommandResult result = command.execute(model);

        assertEquals(String.format(AddToComparisonCommand.MESSAGE_SUCCESS, Messages.format(BENSON)),
                result.getFeedbackToUser());
        assertEquals(List.of(BENSON.getId()), comparisonList.addedApplicantIds);
        assertEquals(dataBefore, model.getRecruitDexData());
        assertEquals(List.of(BENSON), model.getFilteredApplicantList());
    }

    @Test
    public void execute_indexPastUnfilteredList_throwsCommandException() {
        Index invalidIndex = Index.fromOneBased(model.getFilteredApplicantList().size() + 1);

        assertCommandFailure(new AddToComparisonCommand(invalidIndex), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
        assertTrue(comparisonList.addedApplicantIds.isEmpty());
    }

    @Test
    public void execute_indexPastFilteredList_throwsCommandException() {
        model.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        assertTrue(INDEX_SECOND_APPLICANT.getZeroBased() < model.getRecruitDexData().getApplicantList().size());

        assertCommandFailure(new AddToComparisonCommand(INDEX_SECOND_APPLICANT), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
        assertTrue(comparisonList.addedApplicantIds.isEmpty());
    }

    @Test
    public void execute_emptyDisplayedList_throwsCommandException() {
        model.updateFilteredApplicantList(applicant -> false);

        assertCommandFailure(new AddToComparisonCommand(INDEX_FIRST_APPLICANT), model,
                Messages.MESSAGE_INVALID_APPLICANT_DISPLAYED_INDEX);
        assertEquals(0, model.comparisonListAccessCount);
    }

    @Test
    public void execute_duplicateMember_throwsCommandException() {
        comparisonList.failure = new DuplicateComparisonMemberException();

        assertCommandFailure(new AddToComparisonCommand(INDEX_FIRST_APPLICANT), model,
                AddToComparisonCommand.MESSAGE_DUPLICATE_MEMBER);
        assertTrue(comparisonList.addedApplicantIds.isEmpty());
        assertEquals(1, comparisonList.addCallCount);
    }

    @Test
    public void execute_applicantRejectedByModel_throwsCommandException() {
        comparisonList.failure = new ApplicantNotFoundException();

        assertCommandFailure(new AddToComparisonCommand(INDEX_FIRST_APPLICANT), model,
                AddToComparisonCommand.MESSAGE_APPLICANT_NOT_FOUND);
        assertTrue(comparisonList.addedApplicantIds.isEmpty());
        assertEquals(1, comparisonList.addCallCount);
    }

    @Test
    public void execute_defaultModelManager_addsMembershipAndUpdatesRanking() throws Exception {
        ModelManager liveModel = new ModelManager(getTypicalRecruitDexData(), new UserPrefs());

        CommandResult result = new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(liveModel);

        assertEquals(String.format(AddToComparisonCommand.MESSAGE_SUCCESS, Messages.format(ALICE)),
                result.getFeedbackToUser());
        assertEquals(Set.of(ALICE.getId()), liveModel.getComparisonList().getMemberIds());
        assertEquals(1, liveModel.getRankingEngine().getRanking().size());
        assertEquals(ALICE.getId(), liveModel.getRankingEngine().getRanking().get(0).getApplicantId());
    }

    @Test
    public void execute_unexpectedUnsupportedAdd_propagatesException() {
        comparisonList.failure = new UnsupportedOperationException("Unexpected model failure");

        assertThrows(UnsupportedOperationException.class, "Unexpected model failure", () ->
                new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(model));
    }

    @Test
    public void execute_realComparisonList_addsFilteredApplicantAndRefreshesRanking() throws Exception {
        RankingEngineStub rankingEngine = new RankingEngineStub();
        ModelWithComparisonList connectedModel = new ModelWithComparisonList(rankingEngine);
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        connectedModel.updateFilteredApplicantList(applicant -> applicant.getId().equals(BENSON.getId()));
        List<Applicant> recordsBefore = List.copyOf(connectedModel.getApplicantRegistry().getApplicantList());

        CommandResult result = new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(String.format(AddToComparisonCommand.MESSAGE_SUCCESS, Messages.format(BENSON)),
                result.getFeedbackToUser());
        assertEquals(Set.of(BENSON.getId()), realComparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
        assertEquals(Set.of(BENSON.getId()), rankingEngine.lastActiveMembers);
        assertTrue(rankingEngine.lastDecisions.isEmpty());
        assertTrue(realComparisonList.getDecisionHistory().isEmpty());
        assertTrue(connectedModel.getComparisonList().getCurrentPair().isEmpty());
        assertEquals(recordsBefore, connectedModel.getApplicantRegistry().getApplicantList());
        assertEquals(List.of(BENSON), connectedModel.getFilteredApplicantList());
    }

    @Test
    public void execute_realComparisonList_duplicateMemberPreservesState() {
        RankingEngineStub rankingEngine = new RankingEngineStub();
        ModelWithComparisonList connectedModel = new ModelWithComparisonList(rankingEngine);
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        realComparisonList.add(ALICE.getId());

        assertCommandFailure(new AddToComparisonCommand(INDEX_FIRST_APPLICANT), connectedModel,
                AddToComparisonCommand.MESSAGE_DUPLICATE_MEMBER);

        assertEquals(Set.of(ALICE.getId()), realComparisonList.getMemberIds());
        assertEquals(1, rankingEngine.recalculationCount);
        assertEquals(Set.of(ALICE.getId()), rankingEngine.lastActiveMembers);
        assertTrue(rankingEngine.lastDecisions.isEmpty());
        assertTrue(realComparisonList.getDecisionHistory().isEmpty());
        assertTrue(connectedModel.getComparisonList().getCurrentPair().isEmpty());
    }

    @Test
    public void execute_realComparisonList_preservesDecisionHistoryAndPendingPair() throws Exception {
        RankingEngineStub rankingEngine = new RankingEngineStub();
        ModelWithComparisonList connectedModel = new ModelWithComparisonList(rankingEngine);
        ComparisonList realComparisonList = connectedModel.getComparisonList();
        realComparisonList.add(ALICE.getId());
        realComparisonList.add(BENSON.getId());
        ComparisonPair pair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        connectedModel.getComparisonList().getComparisonState().setCurrentPair(pair);
        ComparisonDecision decision = realComparisonList.recordDecision(ALICE.getId());
        connectedModel.getComparisonList().getComparisonState().setCurrentPair(pair);
        connectedModel.updateFilteredApplicantList(applicant -> applicant.getId().equals(CARL.getId()));
        List<Applicant> recordsBefore = List.copyOf(connectedModel.getApplicantRegistry().getApplicantList());
        int recalculationsBefore = rankingEngine.recalculationCount;

        new AddToComparisonCommand(INDEX_FIRST_APPLICANT).execute(connectedModel);

        assertEquals(Set.of(ALICE.getId(), BENSON.getId(), CARL.getId()), realComparisonList.getMemberIds());
        assertEquals(List.of(decision), realComparisonList.getDecisionHistory());
        assertEquals(Optional.of(pair), connectedModel.getComparisonList().getCurrentPair());
        assertEquals(recalculationsBefore + 1, rankingEngine.recalculationCount);
        assertEquals(Set.of(ALICE.getId(), BENSON.getId(), CARL.getId()), rankingEngine.lastActiveMembers);
        assertEquals(List.of(decision), realComparisonList.getDecisionHistory());
        assertEquals(recordsBefore, connectedModel.getApplicantRegistry().getApplicantList());
        assertEquals(List.of(CARL), connectedModel.getFilteredApplicantList());
    }

    @Test
    public void equals() {
        AddToComparisonCommand firstCommand = new AddToComparisonCommand(INDEX_FIRST_APPLICANT);
        AddToComparisonCommand secondCommand = new AddToComparisonCommand(INDEX_SECOND_APPLICANT);

        assertTrue(firstCommand.equals(firstCommand));
        assertTrue(firstCommand.equals(new AddToComparisonCommand(INDEX_FIRST_APPLICANT)));
        assertFalse(firstCommand.equals(secondCommand));
        assertFalse(firstCommand.equals(null));
        assertFalse(firstCommand.equals(new DeleteCommand(INDEX_FIRST_APPLICANT)));
    }

    @Test
    public void toStringMethod() {
        AddToComparisonCommand command = new AddToComparisonCommand(INDEX_FIRST_APPLICANT);
        assertEquals(AddToComparisonCommand.class.getCanonicalName()
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
     * Captures successful requests or simulates a model rejection. Other operations are forbidden in this command.
     */
    private static class ComparisonListStub extends ComparisonList {
        private final List<ApplicantId> addedApplicantIds = new ArrayList<>();
        private RuntimeException failure;
        private int addCallCount;

        ComparisonListStub() {
            this(new ApplicantRegistry());
        }

        private ComparisonListStub(ApplicantRegistry registry) {
            super(registry, new ComparisonState(registry),
                    new RankingEngineStub());
        }

        @Override
        public void add(ApplicantId applicantId) {
            addCallCount++;
            if (failure != null) {
                throw failure;
            }
            addedApplicantIds.add(applicantId);
        }

        @Override
        public ObservableSet<ApplicantId> getMemberIds() {
            throw new AssertionError("Command must delegate membership validation to the model.");
        }

        @Override
        public ObservableList<ComparisonDecision> getDecisionHistory() {
            throw new AssertionError("Command must not access decision history.");
        }

        @Override
        public void remove(ApplicantId applicantId) {
            throw new AssertionError("Command must not remove membership.");
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
