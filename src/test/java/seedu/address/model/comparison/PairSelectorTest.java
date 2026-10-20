package seedu.address.model.comparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.testutil.Assert.assertThrows;
import static seedu.address.testutil.TypicalApplicants.ALICE;
import static seedu.address.testutil.TypicalApplicants.BENSON;
import static seedu.address.testutil.TypicalApplicants.CARL;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import seedu.address.model.applicant.ApplicantId;

public class PairSelectorTest {
    private final PairSelector selector = new PairSelector();

    @Test
    public void selectPair_fewerThanTwoMembers_returnsEmpty() {
        assertTrue(selector.selectPair(Set.of(), List.of(), List.of()).isEmpty());
        assertTrue(selector.selectPair(Set.of(ALICE.getId()), List.of(), List.of()).isEmpty());
    }

    @Test
    public void selectPair_twoMembers_alwaysContainsBothDistinctMembers() {
        Set<ApplicantId> members = Set.of(ALICE.getId(), BENSON.getId());
        for (int attempt = 0; attempt < 100; attempt++) {
            ComparisonPair pair = selector.selectPair(members, List.of(), List.of()).orElseThrow();
            assertNotEquals(pair.getFirstApplicantId(), pair.getSecondApplicantId());
            assertEquals(members, Set.of(pair.getFirstApplicantId(), pair.getSecondApplicantId()));
        }
    }

    @Test
    public void selectPair_largerPool_containsOnlyDistinctActiveMembersAndPreservesInputs() {
        Set<ApplicantId> members = new HashSet<>(Set.of(ALICE.getId(), BENSON.getId(), CARL.getId()));
        Set<ApplicantId> originalMembers = Set.copyOf(members);
        List<ComparisonDecision> history = List.of(
                new ComparisonDecision(ALICE.getId(), BENSON.getId(), ALICE.getId()));
        for (int attempt = 0; attempt < 100; attempt++) {
            ComparisonPair pair = selector.selectPair(members, history, List.of()).orElseThrow();
            assertNotEquals(pair.getFirstApplicantId(), pair.getSecondApplicantId());
            assertTrue(members.contains(pair.getFirstApplicantId()));
            assertTrue(members.contains(pair.getSecondApplicantId()));
        }
        assertEquals(originalMembers, members);
        assertEquals(1, history.size());
    }

    @Test
    public void selectPair_nullInput_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> selector.selectPair(null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> selector.selectPair(Set.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> selector.selectPair(Set.of(), List.of(), null));
        Set<ApplicantId> withNullMember = new HashSet<>();
        withNullMember.add(null);
        assertThrows(NullPointerException.class, () -> selector.selectPair(withNullMember, List.of(), List.of()));
    }

    @Test
    public void selectPair_threeApplicants_avoidsImmediateRematchAndCoversUnassessed() {
        PairSelector deterministic = new PairSelector(new Random(7));
        ComparisonDecision last = new ComparisonDecision(ALICE.getId(), BENSON.getId(), ALICE.getId());
        ComparisonPair next = deterministic.selectPair(Set.of(ALICE.getId(), BENSON.getId(), CARL.getId()),
                List.of(last), List.of()).orElseThrow();
        assertTrue(Set.of(next.getFirstApplicantId(), next.getSecondApplicantId()).contains(CARL.getId()));
    }
}
