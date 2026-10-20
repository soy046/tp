package seedu.address.model.userprefs;

import seedu.address.commons.core.GuiSettings;
import seedu.address.model.comparison.ComparisonSettings;

/**
 * Unmodifiable view of user prefs.
 */
public interface ReadOnlyUserPrefs {

    GuiSettings getGuiSettings();

    ComparisonSettings getComparisonSettings();

}
