package seedu.address.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.model.Model.PREDICATE_SHOW_ALL_APPLICANTS;
import static seedu.address.testutil.Assert.assertThrows;
import static seedu.address.testutil.TypicalApplicants.ALICE;
import static seedu.address.testutil.TypicalApplicants.BENSON;
import static seedu.address.testutil.TypicalApplicants.CARL;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import seedu.address.commons.core.GuiSettings;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.applicant.NameContainsKeywordsPredicate;
import seedu.address.model.applicant.exceptions.ApplicantNotFoundException;
import seedu.address.model.applicant.exceptions.DuplicateApplicantException;
import seedu.address.model.comparison.ComparisonDecision;
import seedu.address.model.comparison.ComparisonList;
import seedu.address.model.comparison.ComparisonPair;
import seedu.address.model.comparison.ComparisonSettings;
import seedu.address.model.comparison.ComparisonState;
import seedu.address.model.ranking.RankingEngine;
import seedu.address.model.ranking.RankingEntry;
import seedu.address.model.userprefs.UserPrefs;
import seedu.address.testutil.ApplicantBuilder;
import seedu.address.testutil.RecruitDexDataBuilder;

public class ModelManagerTest {

    private ModelManager modelManager = new ModelManager();

    @Test
    public void registryChanges_filteredView_updatesWithoutModelForwarding() {
        ObservableList<Applicant> displayed = modelManager.getFilteredApplicantList();
        modelManager.updateFilteredApplicantList(applicant -> applicant.getName().equals(ALICE.getName()));
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        assertEquals(List.of(ALICE), displayed);

        Applicant editedAlice = new ApplicantBuilder(ALICE).withName("Alice Tan").build();
        modelManager.getApplicantRegistry().edit(ALICE.getId(), editedAlice);
        assertTrue(displayed.isEmpty());
        assertSame(editedAlice, modelManager.getApplicantRegistry().get(ALICE.getId()));

        modelManager.updateFilteredApplicantList(PREDICATE_SHOW_ALL_APPLICANTS);
        modelManager.getApplicantRegistry().delete(ALICE.getId());
        assertEquals(List.of(BENSON), displayed);
    }

    @Test
    public void setRecruitDexData_keepsExistingFilteredViewAndPredicateConnected() {
        ApplicantRegistry registry = modelManager.getApplicantRegistry();
        ObservableList<Applicant> displayed = modelManager.getFilteredApplicantList();
        modelManager.updateFilteredApplicantList(applicant -> applicant.getId().equals(ALICE.getId()));

        RecruitDexData replacement = new RecruitDexDataBuilder().withApplicant(ALICE).withApplicant(BENSON).build();
        modelManager.setRecruitDexData(replacement);
        assertSame(registry, modelManager.getApplicantRegistry());
        assertSame(displayed, modelManager.getFilteredApplicantList());
        assertEquals(List.of(ALICE), displayed);

        modelManager.setRecruitDexData(new RecruitDexData());
        assertTrue(displayed.isEmpty());
        registry.add(ALICE);
        assertEquals(List.of(ALICE), displayed);
        assertEquals(List.of(ALICE, BENSON), replacement.getApplicantList());
    }

    @Test
    public void constructor() {
        assertEquals(new UserPrefs(), modelManager.getUserPrefs());
        assertEquals(new GuiSettings(), modelManager.getUserPrefs().getGuiSettings());
        assertEquals(new RecruitDexData(), new RecruitDexData(modelManager.getRecruitDexData()));
    }

    @Test
    public void comparisonSettings_changeRefreshesLiveReadiness() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getComparisonList().add(ALICE.getId());
        modelManager.getComparisonList().add(BENSON.getId());
        assertTrue(modelManager.getComparisonList().getReadiness().message().contains("of 5"));

        modelManager.getUserPrefs().setComparisonSettings(new ComparisonSettings(7, 0.90, 0.55, 1));
        assertTrue(modelManager.getComparisonList().getReadiness().message().contains("of 7"));
    }

    @Test
    public void constructor_validUserPrefs_copiesUserPrefs() {
        UserPrefs userPrefs = new UserPrefs();
        userPrefs.setGuiSettings(new GuiSettings(1, 2, 3, 4));
        modelManager = new ModelManager(new RecruitDexData(), userPrefs);
        assertEquals(userPrefs, modelManager.getUserPrefs());

        // Modifying userPrefs should not modify modelManager's userPrefs
        UserPrefs oldUserPrefs = new UserPrefs(userPrefs);
        userPrefs.setGuiSettings(new GuiSettings(5, 6, 7, 8));
        assertEquals(oldUserPrefs, modelManager.getUserPrefs());
    }

    @Test
    public void setGuiSettings_nullGuiSettings_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> modelManager.getUserPrefs().setGuiSettings(null));
    }

    @Test
    public void setGuiSettings_validGuiSettings_setsGuiSettings() {
        GuiSettings guiSettings = new GuiSettings(1, 2, 3, 4);
        modelManager.getUserPrefs().setGuiSettings(guiSettings);
        assertEquals(guiSettings, modelManager.getUserPrefs().getGuiSettings());
    }

    @Test
    public void hasApplicant_nullApplicant_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> modelManager.getApplicantRegistry().hasDuplicate(null));
    }

    @Test
    public void hasApplicant_applicantNotInRecruitDexData_returnsFalse() {
        assertFalse(modelManager.getApplicantRegistry().hasDuplicate(ALICE));
    }

    @Test
    public void hasApplicant_applicantInRecruitDexData_returnsTrue() {
        modelManager.getApplicantRegistry().add(ALICE);
        assertTrue(modelManager.getApplicantRegistry().hasDuplicate(ALICE));
    }

    @Test
    public void getFilteredApplicantList_modifyList_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> modelManager.getFilteredApplicantList().remove(0));
    }

    @Test
    public void equals() {
        RecruitDexData recruitDexData = new RecruitDexDataBuilder().withApplicant(ALICE).withApplicant(BENSON).build();
        RecruitDexData differentRecruitDexData = new RecruitDexData();
        UserPrefs userPrefs = new UserPrefs();

        // same values -> returns true
        modelManager = new ModelManager(recruitDexData, userPrefs);
        ModelManager modelManagerCopy = new ModelManager(recruitDexData, userPrefs);
        assertTrue(modelManager.equals(modelManagerCopy));

        // same object -> returns true
        assertTrue(modelManager.equals(modelManager));

        // null -> returns false
        assertFalse(modelManager.equals(null));

        // different types -> returns false
        assertFalse(modelManager.equals(5));

        // different recruitDexData -> returns false
        assertFalse(modelManager.equals(new ModelManager(differentRecruitDexData, userPrefs)));

        // different filteredList -> returns false
        String[] keywords = ALICE.getName().fullName.split("\\s+");
        modelManager.updateFilteredApplicantList(new NameContainsKeywordsPredicate(List.of(keywords)));
        assertFalse(modelManager.equals(new ModelManager(recruitDexData, userPrefs)));

        // resets modelManager to initial state for upcoming tests
        modelManager.updateFilteredApplicantList(PREDICATE_SHOW_ALL_APPLICANTS);

        // different userPrefs -> returns false
        UserPrefs differentUserPrefs = new UserPrefs();
        differentUserPrefs.setGuiSettings(new GuiSettings(1, 2, 3, 4));
        assertFalse(modelManager.equals(new ModelManager(recruitDexData, differentUserPrefs)));
    }

    @Test
    public void getComparisonState_isTheSameObjectAsTheDataState() {
        RecruitDexData data = (RecruitDexData) modelManager.getRecruitDexData();
        assertSame(modelManager.getComparisonList(), data.getComparisonList());
    }

    @Test
    public void getComparisonState_validatesAgainstTheModelRegistry() {
        ComparisonPair pair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        assertThrows(ApplicantNotFoundException.class, () ->
                modelManager.getComparisonList().getComparisonState().setCurrentPair(pair));

        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getComparisonList().getComparisonState().setCurrentPair(pair);

        assertEquals(Optional.of(pair), modelManager.getComparisonList().getCurrentPair());
    }

    @Test
    public void getComparisonState_ignoresTheSearchFilter() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        ComparisonPair pair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        modelManager.getComparisonList().getComparisonState().setCurrentPair(pair);

        modelManager.updateFilteredApplicantList(new NameContainsKeywordsPredicate(List.of("Alice")));

        assertEquals(Optional.of(pair), modelManager.getComparisonList().getCurrentPair());
    }

    @Test
    public void setRecruitDexData_clearsPendingPair() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        modelManager.setRecruitDexData(new RecruitDexData());

        assertEquals(Optional.empty(), modelManager.getComparisonList().getCurrentPair());
    }

    @Test
    public void getRankingEngine_repeatedAccess_returnsSameEngine() {
        RankingEngine engine = modelManager.getRankingEngine();

        assertSame(engine, modelManager.getRankingEngine());
        assertTrue(engine.getRanking().isEmpty());
    }

    @Test
    public void getRankingEngine_separateModels_haveIndependentRankings() {
        ModelManager otherModel = new ModelManager();
        modelManager.getApplicantRegistry().add(ALICE);

        modelManager.getRankingEngine().recalculate(Set.of(ALICE.getId()), List.of());

        assertNotSame(modelManager.getRankingEngine(), otherModel.getRankingEngine());
        assertEquals(1, modelManager.getRankingEngine().getRanking().size());
        assertTrue(otherModel.getRankingEngine().getRanking().isEmpty());
    }

    @Test
    public void constructor_existingRecords_doesNotAssumeComparisonMembership() {
        RecruitDexData data = new RecruitDexDataBuilder().withApplicant(ALICE).withApplicant(BENSON).build();

        ModelManager model = new ModelManager(data, new UserPrefs());

        assertEquals(2, model.getApplicantRegistry().getApplicantList().size());
        assertTrue(model.getRankingEngine().getRanking().isEmpty());
    }

    @Test
    public void setRecruitDexData_validReplacement_clearsExistingRankingView() {
        modelManager.getApplicantRegistry().add(ALICE);
        RankingEngine engine = modelManager.getRankingEngine();
        ObservableList<RankingEntry> rankingEntries = engine.getRanking();
        ComparisonList previousList = modelManager.getComparisonList();
        previousList.add(ALICE.getId());
        RecruitDexData replacement = new RecruitDexDataBuilder().withApplicant(BENSON).build();

        modelManager.setRecruitDexData(replacement);

        assertSame(engine, modelManager.getRankingEngine());
        assertSame(rankingEntries, engine.getRanking());
        assertTrue(rankingEntries.isEmpty());
        assertSame(previousList, modelManager.getComparisonList());
        assertTrue(modelManager.getComparisonList().getMemberIds().isEmpty());
        assertEquals(List.of(BENSON), modelManager.getApplicantRegistry().getApplicantList());

        engine.recalculate(Set.of(BENSON.getId()), List.of());
        assertEquals(BENSON.getId(), rankingEntries.get(0).getApplicantId());
    }

    @Test
    public void setRecruitDexData_comparisons_restoresDataAndRetainsComponentsAndViews() {
        ComparisonList comparisons = modelManager.getComparisonList();
        ComparisonState state = comparisons.getComparisonState();
        ObservableSet<?> members = comparisons.getMemberIds();
        ObservableList<ComparisonDecision> decisions = comparisons.getDecisionHistory();
        ObservableList<RankingEntry> ranking = modelManager.getRankingEngine().getRanking();
        var pairProperty = state.currentPairProperty();
        RecruitDexData replacement = new RecruitDexDataBuilder().withApplicant(ALICE).withApplicant(BENSON).build();
        replacement.getComparisonList().add(ALICE.getId());
        replacement.getComparisonList().add(BENSON.getId());
        ComparisonPair pair = new ComparisonPair(BENSON.getId(), ALICE.getId());
        replacement.getComparisonList().getComparisonState().setCurrentPair(pair);
        ComparisonDecision decision = replacement.getComparisonList().recordDecision(ALICE.getId());
        replacement.getComparisonList().getComparisonState().setCurrentPair(pair);

        modelManager.setRecruitDexData(replacement);

        assertSame(comparisons, modelManager.getComparisonList());
        assertSame(state, comparisons.getComparisonState());
        assertSame(pairProperty, state.currentPairProperty());
        assertSame(members, comparisons.getMemberIds());
        assertSame(decisions, comparisons.getDecisionHistory());
        assertSame(ranking, modelManager.getRankingEngine().getRanking());
        assertEquals(Set.of(ALICE.getId(), BENSON.getId()), members);
        assertEquals(List.of(decision), decisions);
        assertEquals(Optional.of(pair), pairProperty.get());
        assertEquals(ALICE.getId(), ranking.get(0).getApplicantId());
        assertEquals(1, ranking.get(0).getComparisonCount());

        modelManager.setRecruitDexData(modelManager.getRecruitDexData());
        assertEquals(replacement, modelManager.getRecruitDexData());
        assertEquals(List.of(decision), decisions);
        assertEquals(Optional.of(pair), pairProperty.get());

        modelManager.setRecruitDexData(new RecruitDexData());
        assertTrue(members.isEmpty());
        assertTrue(decisions.isEmpty());
        assertTrue(pairProperty.get().isEmpty());
        assertTrue(ranking.isEmpty());
        modelManager.getApplicantRegistry().add(CARL);
        comparisons.add(CARL.getId());
        assertEquals(Set.of(CARL.getId()), modelManager.getComparisonList().getMemberIds());
        assertEquals(CARL.getId(), ranking.get(0).getApplicantId());
    }

    @Test
    public void setRecruitDexData_invalidPendingMembership_preservesExistingDataAndRanking() {
        modelManager.getApplicantRegistry().add(CARL);
        modelManager.getComparisonList().add(CARL.getId());
        RecruitDexData original = new RecruitDexData(modelManager.getRecruitDexData());
        List<RankingEntry> originalRanking = List.copyOf(modelManager.getRankingEngine().getRanking());
        RecruitDexData invalid = new RecruitDexDataBuilder().withApplicant(ALICE).withApplicant(BENSON).build();
        invalid.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        assertThrows(IllegalArgumentException.class, () -> modelManager.setRecruitDexData(invalid));

        assertEquals(original, modelManager.getRecruitDexData());
        assertEquals(originalRanking, modelManager.getRankingEngine().getRanking());
    }

    @Test
    public void setRecruitDexData_invalidReplacement_preservesRanking() {
        modelManager.getApplicantRegistry().add(ALICE);
        RankingEngine engine = modelManager.getRankingEngine();
        engine.recalculate(Set.of(ALICE.getId()), List.of());
        ObservableList<RankingEntry> rankingEntries = engine.getRanking();
        List<RankingEntry> originalEntries = List.copyOf(rankingEntries);
        ReadOnlyRecruitDexData invalidData = new RecruitDexData() {
            @Override
            public ObservableList<Applicant> getApplicantList() {
                return FXCollections.observableArrayList(ALICE, ALICE);
            }
        };

        assertThrows(DuplicateApplicantException.class, () -> modelManager.setRecruitDexData(invalidData));

        assertSame(engine, modelManager.getRankingEngine());
        assertSame(rankingEntries, engine.getRanking());
        assertEquals(originalEntries, rankingEntries);
        assertEquals(List.of(ALICE), modelManager.getApplicantRegistry().getApplicantList());
    }

    @Test
    public void getRankingEngine_searchFilter_doesNotChangeRanking() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getRankingEngine().recalculate(Set.of(ALICE.getId(), BENSON.getId()),
                List.of(new ComparisonDecision(ALICE.getId(), BENSON.getId(), ALICE.getId())));
        List<RankingEntry> originalEntries = List.copyOf(modelManager.getRankingEngine().getRanking());

        modelManager.updateFilteredApplicantList(applicant -> false);

        assertTrue(modelManager.getFilteredApplicantList().isEmpty());
        assertEquals(originalEntries, modelManager.getRankingEngine().getRanking());
    }

    @Test
    public void equals_differentRuntimeRankings_ignoresEngineIdentityAndResults() {
        modelManager.getApplicantRegistry().add(ALICE);
        ModelManager otherModel = new ModelManager(modelManager.getRecruitDexData(), new UserPrefs());

        modelManager.getRankingEngine().recalculate(Set.of(ALICE.getId()), List.of());

        assertEquals(modelManager, otherModel);
    }

    @Test
    public void registryEdit_preservesComparisonMembershipAndPendingPair() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        ComparisonList comparisonList = modelManager.getComparisonList();
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        ComparisonPair pair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        modelManager.getComparisonList().getComparisonState().setCurrentPair(pair);
        Applicant editedAlice = new ApplicantBuilder(ALICE).withName("Alice Tan").build();

        modelManager.getApplicantRegistry().edit(ALICE.getId(), editedAlice);

        assertEquals(Set.of(ALICE.getId(), BENSON.getId()), comparisonList.getMemberIds());
        assertEquals(Optional.of(pair), modelManager.getComparisonList().getCurrentPair());
    }

    @Test
    public void registryDelete_activeMember_removesReferencesAndPreservesUnrelatedHistory() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getApplicantRegistry().add(CARL);
        ComparisonList comparisonList = modelManager.getComparisonList();
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());
        ComparisonPair affectedPair = new ComparisonPair(ALICE.getId(), BENSON.getId());
        modelManager.getComparisonList().getComparisonState().setCurrentPair(affectedPair);
        comparisonList.recordDecision(ALICE.getId());
        modelManager.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(BENSON.getId(), CARL.getId()));
        ComparisonDecision retainedDecision = comparisonList.recordDecision(BENSON.getId());
        modelManager.getComparisonList().getComparisonState().setCurrentPair(affectedPair);
        ObservableList<RankingEntry> ranking = modelManager.getRankingEngine().getRanking();

        modelManager.getApplicantRegistry().delete(ALICE.getId());

        assertEquals(List.of(BENSON, CARL), modelManager.getApplicantRegistry().getApplicantList());
        assertEquals(Set.of(BENSON.getId(), CARL.getId()), comparisonList.getMemberIds());
        assertEquals(List.of(retainedDecision), comparisonList.getDecisionHistory());
        ComparisonPair replacementPair = comparisonList.getCurrentPair().orElseThrow();
        assertEquals(Set.of(BENSON.getId(), CARL.getId()),
                Set.of(replacementPair.getFirstApplicantId(), replacementPair.getSecondApplicantId()));
        assertSame(ranking, modelManager.getRankingEngine().getRanking());
        assertEquals(2, ranking.size());
        assertEquals(BENSON.getId(), ranking.get(0).getApplicantId());
        assertEquals(CARL.getId(), ranking.get(1).getApplicantId());
        assertTrue(ranking.stream().allMatch(entry -> entry.getComparisonCount() == 1));
    }

    @Test
    public void registryDelete_inactiveApplicant_removesHistoryAndPreservesUnaffectedPair() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        modelManager.getApplicantRegistry().add(CARL);
        ComparisonList comparisonList = modelManager.getComparisonList();
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        comparisonList.add(CARL.getId());
        modelManager.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));
        comparisonList.recordDecision(ALICE.getId());
        comparisonList.remove(ALICE.getId());
        ComparisonPair unaffectedPair = new ComparisonPair(BENSON.getId(), CARL.getId());
        modelManager.getComparisonList().getComparisonState().setCurrentPair(unaffectedPair);

        modelManager.getApplicantRegistry().delete(ALICE.getId());

        assertEquals(List.of(BENSON, CARL), modelManager.getApplicantRegistry().getApplicantList());
        assertEquals(Set.of(BENSON.getId(), CARL.getId()), comparisonList.getMemberIds());
        assertTrue(comparisonList.getDecisionHistory().isEmpty());
        assertEquals(Optional.of(unaffectedPair), modelManager.getComparisonList().getCurrentPair());
        assertEquals(2, modelManager.getRankingEngine().getRanking().size());
        assertTrue(modelManager.getRankingEngine().getRanking().stream()
                .allMatch(entry -> entry.getComparisonCount() == 0));
    }

    @Test
    public void registryDelete_afterDataReset_cleansCurrentComparisonList() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getComparisonList().add(ALICE.getId());
        ApplicantRegistry registry = modelManager.getApplicantRegistry();
        RecruitDexData replacement = new RecruitDexDataBuilder().withApplicant(BENSON).withApplicant(CARL).build();
        modelManager.setRecruitDexData(replacement);
        ComparisonList currentList = modelManager.getComparisonList();
        currentList.add(BENSON.getId());
        currentList.add(CARL.getId());
        modelManager.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(BENSON.getId(), CARL.getId()));

        registry.delete(BENSON.getId());

        assertEquals(List.of(CARL), registry.getApplicantList());
        assertEquals(Set.of(CARL.getId()), currentList.getMemberIds());
        assertTrue(modelManager.getComparisonList().getCurrentPair().isEmpty());
        assertEquals(1, modelManager.getRankingEngine().getRanking().size());
        assertEquals(CARL.getId(), modelManager.getRankingEngine().getRanking().get(0).getApplicantId());
    }

    @Test
    public void comparisonList_manuallySelectedPair_recordsDecisionAndUpdatesSharedRanking() {
        modelManager.getApplicantRegistry().add(ALICE);
        modelManager.getApplicantRegistry().add(BENSON);
        ComparisonList comparisonList = modelManager.getComparisonList();
        comparisonList.add(ALICE.getId());
        comparisonList.add(BENSON.getId());
        modelManager.getComparisonList().getComparisonState()
                .setCurrentPair(new ComparisonPair(ALICE.getId(), BENSON.getId()));

        ComparisonDecision decision = comparisonList.recordDecision(ALICE.getId());

        assertEquals(List.of(decision), comparisonList.getDecisionHistory());
        assertEquals(ALICE.getId(), modelManager.getRankingEngine().getRanking().get(0).getApplicantId());
        assertEquals(1, modelManager.getRankingEngine().getRanking().get(0).getComparisonCount());
        ComparisonPair nextPair = comparisonList.getCurrentPair().orElseThrow();
        assertEquals(Set.of(ALICE.getId(), BENSON.getId()),
                Set.of(nextPair.getFirstApplicantId(), nextPair.getSecondApplicantId()));
    }
}
