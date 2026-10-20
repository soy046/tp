package seedu.address.model;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import javafx.collections.ObservableList;
import seedu.address.commons.util.ToStringBuilder;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.applicant.ApplicantRegistry;
import seedu.address.model.comparison.ComparisonList;
import seedu.address.model.comparison.ComparisonSettings;
import seedu.address.model.comparison.ComparisonState;
import seedu.address.model.comparison.ReadOnlyComparisonList;
import seedu.address.model.ranking.RankingEngine;

/**
 * Root of the application data saved together in one file.
 * Contains applicant records, comparison membership, ordered decisions, and the pending pair.
 * Display filters and user preferences do not belong to this saved state.
 */
public class RecruitDexData implements ReadOnlyRecruitDexData {

    private final ApplicantRegistry applicantRegistry = new ApplicantRegistry();
    private final ComparisonList comparisonList;

    public RecruitDexData() {
        this(new RankingEngine());
    }

    private RecruitDexData(RankingEngine rankingEngine) {
        this(rankingEngine, new ComparisonSettings());
    }

    private RecruitDexData(RankingEngine rankingEngine, ComparisonSettings settings) {
        comparisonList = new ComparisonList(applicantRegistry, new ComparisonState(applicantRegistry),
                rankingEngine, settings);
        applicantRegistry.setDeletionHandler(comparisonList::deleteApplicantReferences);
    }

    /**
     * Creates an independent data object containing all saved data in {@code toBeCopied}.
     */
    public RecruitDexData(ReadOnlyRecruitDexData toBeCopied) {
        this();
        resetData(toBeCopied);
    }

    /** Creates saved data connected to ModelManager's runtime ranking engine. */
    RecruitDexData(ReadOnlyRecruitDexData toBeCopied, RankingEngine rankingEngine) {
        this(rankingEngine);
        resetData(toBeCopied);
    }

    RecruitDexData(ReadOnlyRecruitDexData toBeCopied, RankingEngine rankingEngine, ComparisonSettings settings) {
        this(rankingEngine, settings);
        resetData(toBeCopied);
    }

    /**
     * Replaces saved data after validation, retaining both model components and their observable views.
     * Invalid replacement data leaves existing data unchanged. Copying first also permits self-reset.
     */
    public void resetData(ReadOnlyRecruitDexData newData) {
        requireNonNull(newData);
        List<Applicant> records = List.copyOf(newData.getApplicantList());
        ApplicantRegistry validatedRegistry = new ApplicantRegistry();
        validatedRegistry.setApplicants(records);
        ReadOnlyComparisonList sourceComparisons = newData.getComparisonList();
        ComparisonList validatedComparisons = new ComparisonList(validatedRegistry,
                new ComparisonState(validatedRegistry), new RankingEngine());
        validatedComparisons.resetData(sourceComparisons.getMemberIds(), sourceComparisons.getDecisionHistory(),
                sourceComparisons.getCurrentPair());

        applicantRegistry.setApplicants(records);
        comparisonList.resetData(validatedComparisons.getMemberIds(), validatedComparisons.getDecisionHistory(),
                validatedComparisons.getCurrentPair());
    }

    /** Returns the live registry responsible for applicant records. */
    public ApplicantRegistry getApplicantRegistry() {
        return applicantRegistry;
    }

    /** Returns the live comparison list responsible for membership, decisions, and pending state. */
    @Override
    public ComparisonList getComparisonList() {
        return comparisonList;
    }

    @Override
    public ObservableList<Applicant> getApplicantList() {
        return applicantRegistry.getApplicantList();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .add("applicants", applicantRegistry)
                .add("comparisonList", comparisonList)
                .toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof RecruitDexData otherRecruitDexData)) {
            return false;
        }

        return applicantRegistry.equals(otherRecruitDexData.applicantRegistry)
                && comparisonList.equals(otherRecruitDexData.comparisonList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicantRegistry, comparisonList);
    }
}
