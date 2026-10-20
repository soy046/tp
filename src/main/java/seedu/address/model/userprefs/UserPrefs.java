package seedu.address.model.userprefs;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Consumer;

import seedu.address.commons.core.GuiSettings;
import seedu.address.model.comparison.ComparisonSettings;

/**
 * Represents User's preferences.
 */
public class UserPrefs implements ReadOnlyUserPrefs {

    private GuiSettings guiSettings = new GuiSettings();
    private ComparisonSettings comparisonSettings = new ComparisonSettings();
    private transient Consumer<ComparisonSettings> comparisonSettingsListener = ignored -> {};

    /**
     * Creates a {@code UserPrefs} with default values.
     */
    public UserPrefs() {}

    /**
     * Creates a {@code UserPrefs} with the prefs in {@code userPrefs}.
     */
    public UserPrefs(ReadOnlyUserPrefs userPrefs) {
        requireNonNull(userPrefs);
        setGuiSettings(userPrefs.getGuiSettings());
        setComparisonSettings(userPrefs.getComparisonSettings());
    }

    public GuiSettings getGuiSettings() {
        return guiSettings;
    }

    public void setGuiSettings(GuiSettings guiSettings) {
        requireNonNull(guiSettings);
        this.guiSettings = guiSettings;
    }

    @Override
    public ComparisonSettings getComparisonSettings() {
        return comparisonSettings;
    }

    public void setComparisonSettings(ComparisonSettings comparisonSettings) {
        this.comparisonSettings = requireNonNull(comparisonSettings);
        comparisonSettingsListener.accept(comparisonSettings);
    }

    /** Connects changes to the live comparison model after preferences are copied. */
    public void setComparisonSettingsListener(Consumer<ComparisonSettings> listener) {
        comparisonSettingsListener = requireNonNull(listener);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        // instanceof handles nulls
        if (!(other instanceof UserPrefs otherUserPrefs)) {
            return false;
        }

        return guiSettings.equals(otherUserPrefs.guiSettings)
                && comparisonSettings.equals(otherUserPrefs.comparisonSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(guiSettings, comparisonSettings);
    }

    @Override
    public String toString() {
        return "Gui Settings : " + guiSettings;
    }

}
