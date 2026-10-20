package seedu.address.model.ranking;

import java.util.List;

import seedu.address.model.applicant.ApplicantId;

/** Conservative evidence certificate for the active ranking. */
public record RankingReadiness(Status status, String message, List<List<ApplicantId>> uncertainGroups) {
    /** Current level of support for the active ranking. */
    public enum Status { INSUFFICIENT_DATA, NEEDS_EVIDENCE, READY, UNAVAILABLE }

    public RankingReadiness {
        uncertainGroups = uncertainGroups.stream().map(List::copyOf).toList();
    }

    public boolean isReady() {
        return status == Status.READY;
    }
}
