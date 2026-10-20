package seedu.address.ui;

import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.util.HashMap;
import java.util.Map;

import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.applicant.ApplicantId;
import seedu.address.model.ranking.RankingEntry;
import seedu.address.model.ranking.RankingReadiness;

/**
 * Panel displaying the engine's complete ranking with current applicant names resolved by ID.
 * Observes ranking changes and applicant record changes independently of the search filter.
 * Observable updates are supplied on the JavaFX application thread.
 */
public class RankingPanel extends UiPart<Region> {

    private static final String FXML = "RankingPanel.fxml";

    private final ObservableList<RankingEntry> rankingEntries;
    private final ObservableList<Applicant> applicants;
    private final Map<ApplicantId, Applicant> applicantsById = new HashMap<>();

    @FXML
    private Label subtitle;
    @FXML
    private Label readinessLabel;
    @FXML
    private ListView<RankingEntry> rankingListView;

    /**
     * Creates a leaderboard observing {@code rankingEntries} and the complete applicant registry view.
     * Applicant edits refresh names without recalculating positions or scores.
     *
     * @throws NullPointerException if either list is null.
     */
    public RankingPanel(ObservableList<RankingEntry> rankingEntries, ObservableList<Applicant> applicants) {
        super(FXML);
        requireAllNonNull(rankingEntries, applicants);
        this.rankingEntries = rankingEntries;
        this.applicants = applicants;
        rebuildApplicantIndex();
        rankingListView.setCellFactory(listView -> new RankingCell(applicantsById));
        rankingListView.setItems(rankingEntries);
        rankingEntries.addListener((ListChangeListener<RankingEntry>) change -> updateSubtitle());
        applicants.addListener((ListChangeListener<Applicant>) change -> {
            rebuildApplicantIndex();
            rankingListView.refresh();
        });
        updateSubtitle();
    }

    /** Displays evidence status alongside the complete ranking. */
    public RankingPanel(ObservableList<RankingEntry> rankingEntries, ObservableList<Applicant> applicants,
            ObservableValue<RankingReadiness> readiness) {
        this(rankingEntries, applicants);
        java.util.Objects.requireNonNull(readiness);
        renderReadiness(readiness.getValue());
        readiness.addListener((observable, oldValue, newValue) -> renderReadiness(newValue));
        applicants.addListener((ListChangeListener<Applicant>) change -> renderReadiness(readiness.getValue()));
    }

    private void renderReadiness(RankingReadiness readiness) {
        StringBuilder text = new StringBuilder(readiness.message());
        if (!readiness.uncertainGroups().isEmpty()) {
            text.append(readiness.isReady() ? " Close/uncertain: " : " Unresolved: ");
            for (int groupIndex = 0; groupIndex < readiness.uncertainGroups().size(); groupIndex++) {
                if (groupIndex > 0) {
                    text.append("; ");
                }
                var group = readiness.uncertainGroups().get(groupIndex);
                for (int applicantIndex = 0; applicantIndex < group.size(); applicantIndex++) {
                    if (applicantIndex > 0) {
                        text.append(" / ");
                    }
                    Applicant applicant = applicantsById.get(group.get(applicantIndex));
                    text.append(applicant == null ? group.get(applicantIndex) : applicant.getName());
                }
            }
            text.append('.');
        }
        readinessLabel.setText(text.toString());
    }

    private void rebuildApplicantIndex() {
        applicantsById.clear();
        for (Applicant applicant : applicants) {
            applicantsById.put(applicant.getId(), applicant);
        }
    }

    private void updateSubtitle() {
        int count = rankingEntries.size();
        subtitle.setText(count + (count == 1 ? " applicant" : " applicants") + " in comparison list");
    }

    /**
     * Shows an entry's shared ranking position, current name, score, and comparison count.
     * Reuses its controls while clearing content and styling from previous items.
     */
    private static class RankingCell extends ListCell<RankingEntry> {
        private final Map<ApplicantId, Applicant> applicantsById;
        private final Label positionLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label metadataLabel = new Label();
        private final Tooltip nameTooltip = new Tooltip();
        private final HBox row;

        private RankingCell(Map<ApplicantId, Applicant> applicantsById) {
            this.applicantsById = applicantsById;
            // Let the viewport constrain rows instead of using long names to determine the list width.
            setPrefWidth(0);
            positionLabel.getStyleClass().add("ranking-position");
            positionLabel.setMinWidth(Region.USE_PREF_SIZE);
            nameLabel.getStyleClass().add("ranking-name");
            nameLabel.setMinWidth(0);
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            nameLabel.setTooltip(nameTooltip);
            metadataLabel.getStyleClass().add("ranking-metadata");
            metadataLabel.setWrapText(true);
            metadataLabel.setTooltip(new Tooltip("Relative ranking score; higher values rank earlier."));

            VBox details = new VBox(3, nameLabel, metadataLabel);
            details.setMinWidth(0);
            HBox.setHgrow(details, Priority.ALWAYS);
            row = new HBox(8, positionLabel, details);
            row.setMinWidth(0);
            row.setMaxWidth(Double.MAX_VALUE);
            row.getStyleClass().add("ranking-row");
        }

        @Override
        protected void updateItem(RankingEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            render();
        }

        private void render() {
            setText(null);
            row.getStyleClass().remove("ranking-row-first");
            RankingEntry entry = getItem();
            if (isEmpty() || entry == null) {
                positionLabel.setText(null);
                nameLabel.setText(null);
                metadataLabel.setText(null);
                nameTooltip.setText(null);
                setGraphic(null);
                return;
            }

            RankingEntryDisplay display = RankingEntryDisplay.create(entry, applicantsById);
            positionLabel.setText(display.positionText());
            nameLabel.setText(display.nameText());
            metadataLabel.setText(display.metadataText());
            nameTooltip.setText(display.nameText());
            if (display.isFirstPosition()) {
                row.getStyleClass().add("ranking-row-first");
            }
            setGraphic(row);
        }
    }
}
