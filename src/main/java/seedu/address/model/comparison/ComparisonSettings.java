package seedu.address.model.comparison;

import static seedu.address.commons.util.AppUtil.checkArgument;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Recruiter-controlled requirements for considering a ranking ready. */
public final class ComparisonSettings {
    public static final int DEFAULT_MINIMUM_COMPARISONS = 5;
    public static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    public static final double DEFAULT_NEAR_EQUAL_WIN_PROBABILITY = 0.60;
    public static final int DEFAULT_MAXIMUM_RANK_SPAN = 2;

    private final int minimumComparisons;
    private final double confidenceLevel;
    private final double nearEqualWinProbability;
    private final int maximumRankSpan;

    /** Creates the default evidence policy. */
    public ComparisonSettings() {
        this(DEFAULT_MINIMUM_COMPARISONS, DEFAULT_CONFIDENCE_LEVEL,
                DEFAULT_NEAR_EQUAL_WIN_PROBABILITY, DEFAULT_MAXIMUM_RANK_SPAN);
    }

    /** Creates a validated evidence policy. */
    @JsonCreator
    public ComparisonSettings(@JsonProperty("minimumComparisons") int minimumComparisons,
            @JsonProperty("confidenceLevel") double confidenceLevel,
            @JsonProperty("nearEqualWinProbability") double nearEqualWinProbability,
            @JsonProperty("maximumRankSpan") int maximumRankSpan) {
        checkArgument(minimumComparisons > 0, "Minimum comparisons must be positive.");
        checkArgument(Double.isFinite(confidenceLevel) && confidenceLevel > 0.5 && confidenceLevel < 1,
                "Confidence level must be between 0.5 and 1.");
        checkArgument(Double.isFinite(nearEqualWinProbability)
                && nearEqualWinProbability > 0.5 && nearEqualWinProbability < 1,
                "Near-equal probability must be between 0.5 and 1.");
        checkArgument(maximumRankSpan >= 0, "Maximum rank span cannot be negative.");
        this.minimumComparisons = minimumComparisons;
        this.confidenceLevel = confidenceLevel;
        this.nearEqualWinProbability = nearEqualWinProbability;
        this.maximumRankSpan = maximumRankSpan;
    }

    public int getMinimumComparisons() {
        return minimumComparisons;
    }

    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    public double getNearEqualWinProbability() {
        return nearEqualWinProbability;
    }

    public int getMaximumRankSpan() {
        return maximumRankSpan;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof ComparisonSettings settings
                && minimumComparisons == settings.minimumComparisons
                && Double.compare(confidenceLevel, settings.confidenceLevel) == 0
                && Double.compare(nearEqualWinProbability, settings.nearEqualWinProbability) == 0
                && maximumRankSpan == settings.maximumRankSpan;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimumComparisons, confidenceLevel, nearEqualWinProbability, maximumRankSpan);
    }
}
