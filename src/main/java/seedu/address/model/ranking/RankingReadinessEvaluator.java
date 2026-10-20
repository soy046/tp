package seedu.address.model.ranking;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;

import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.comparison.ComparisonSettings;

/** Builds a conservative adjacent-boundary certificate; decisions remain the recruiter's choice. */
public final class RankingReadinessEvaluator {
    private RankingReadinessEvaluator() {}

    /** Evaluates coverage, graph connectivity, and simultaneous uncertainty bounds. */
    public static RankingReadiness evaluate(ComparisonStatistics statistics, List<RankingEntry> ranking,
            BayesianRankingModel posterior, ComparisonSettings settings) {
        if (statistics.size() < 2) {
            return result(RankingReadiness.Status.INSUFFICIENT_DATA, "Add at least two applicants.", List.of());
        }
        int minimum = Integer.MAX_VALUE;
        for (int i = 0; i < statistics.size(); i++) {
            minimum = Math.min(minimum, statistics.count(i));
        }
        if (minimum < settings.getMinimumComparisons()) {
            return result(RankingReadiness.Status.INSUFFICIENT_DATA,
                    "More comparisons needed: least assessed applicant has " + minimum + " of "
                            + settings.getMinimumComparisons() + ".", List.of());
        }
        if (!statistics.isConnected()) {
            return result(RankingReadiness.Status.INSUFFICIENT_DATA,
                    "Connect the comparison groups before assessing the full ranking.", List.of());
        }
        if (posterior == null || ranking.size() != statistics.size()) {
            return result(RankingReadiness.Status.UNAVAILABLE,
                    "Ranking uncertainty is temporarily unavailable.", List.of());
        }
        int boundaries = ranking.size() - 1;
        double familyError = 1 - settings.getConfidenceLevel();
        double comparisonsBudget = (double) boundaries * Math.max(1, settings.getMaximumRankSpan());
        double z = new NormalDistribution().inverseCumulativeProbability(1 - familyError / (2 * comparisonsBudget));
        double closeLogOdds = StrictMath.log(settings.getNearEqualWinProbability()
                / (1 - settings.getNearEqualWinProbability()));
        List<List<ApplicantId>> groups = new ArrayList<>();
        List<ApplicantId> group = new ArrayList<>();
        group.add(ranking.get(0).getApplicantId());
        boolean unresolved = false;
        try {
            for (int i = 0; i < boundaries; i++) {
                RankingEntry first = ranking.get(i);
                RankingEntry second = ranking.get(i + 1);
                int firstIndex = statistics.indexOf(first.getApplicantId());
                int secondIndex = statistics.indexOf(second.getApplicantId());
                double mean = posterior.meanDifference(firstIndex, secondIndex);
                double margin = z * StrictMath.sqrt(posterior.varianceUpperBound(firstIndex, secondIndex));
                boolean separated = mean - margin > 0;
                boolean close = mean - margin >= -closeLogOdds && mean + margin <= closeLogOdds;
                if (separated) {
                    if (group.size() > 1) {
                        groups.add(List.copyOf(group));
                    }
                    group = new ArrayList<>();
                } else if (!close) {
                    unresolved = true;
                }
                group.add(second.getApplicantId());
            }
        } catch (IllegalStateException exception) {
            return result(RankingReadiness.Status.UNAVAILABLE,
                    "Ranking uncertainty is temporarily unavailable.", List.of());
        }
        if (group.size() > 1) {
            groups.add(List.copyOf(group));
        }
        for (List<ApplicantId> uncertain : groups) {
            if (uncertain.size() - 1 > settings.getMaximumRankSpan()) {
                unresolved = true;
                continue;
            }
            // A chain of individually close neighbours need not be close from end to end.
            for (int first = 0; first < uncertain.size(); first++) {
                for (int second = first + 1; second < uncertain.size(); second++) {
                    int firstIndex = statistics.indexOf(uncertain.get(first));
                    int secondIndex = statistics.indexOf(uncertain.get(second));
                    try {
                        double mean = posterior.meanDifference(firstIndex, secondIndex);
                        double margin = z * StrictMath.sqrt(posterior.varianceUpperBound(firstIndex, secondIndex));
                        if (mean - margin < -closeLogOdds || mean + margin > closeLogOdds) {
                            unresolved = true;
                        }
                    } catch (IllegalStateException exception) {
                        return result(RankingReadiness.Status.UNAVAILABLE,
                                "Ranking uncertainty is temporarily unavailable.", List.of());
                    }
                }
            }
        }
        if (unresolved) {
            return result(RankingReadiness.Status.NEEDS_EVIDENCE,
                    "More evidence is needed to distinguish the current ranking.", groups);
        }
        return result(RankingReadiness.Status.READY,
                groups.isEmpty() ? "Ranking has enough supporting evidence; you may stop comparing."
                        : "Ranking is supported; close applicants are grouped as uncertain. You may stop comparing.",
                groups);
    }

    private static RankingReadiness result(RankingReadiness.Status status, String message,
            List<List<ApplicantId>> groups) {
        return new RankingReadiness(status, message, groups);
    }
}
