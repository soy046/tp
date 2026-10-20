package seedu.address.logic;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Optional;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.logic.commands.Command;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.RecruitDexParser;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.Model;
import seedu.address.model.applicant.Applicant;
import seedu.address.model.comparison.ComparisonPair;
import seedu.address.model.ranking.RankingEntry;
import seedu.address.model.ranking.RankingReadiness;
import seedu.address.storage.Storage;

/**
 * The main LogicManager of the app.
 */
public class LogicManager implements Logic {
    public static final String FILE_OPS_ERROR_FORMAT = "Could not save data due to the following error: %s";

    public static final String FILE_OPS_PERMISSION_ERROR_FORMAT =
            "Could not save data to file %s due to insufficient permissions to write to the file or the folder.";

    private final Logger logger = LogsCenter.getLogger(LogicManager.class);

    private final Model model;
    private final Storage storage;
    private final RecruitDexParser recruitDexParser;

    /**
     * Constructs a {@code LogicManager} with the given {@code Model} and {@code Storage}.
     */
    public LogicManager(Model model, Storage storage) {
        this.model = model;
        this.storage = storage;
        recruitDexParser = new RecruitDexParser();
    }

    @Override
    public CommandResult execute(String commandText) throws CommandException, ParseException {
        logger.info("----------------[USER COMMAND][" + commandText + "]");

        CommandResult commandResult;
        Command command = recruitDexParser.parseCommand(commandText);
        commandResult = command.execute(model);

        try {
            storage.saveRecruitDexData(model.getRecruitDexData());
        } catch (AccessDeniedException e) {
            throw new CommandException(String.format(FILE_OPS_PERMISSION_ERROR_FORMAT, e.getMessage()), e);
        } catch (IOException ioe) {
            throw new CommandException(String.format(FILE_OPS_ERROR_FORMAT, ioe.getMessage()), ioe);
        }

        return commandResult;
    }

    @Override
    public ObservableList<Applicant> getFilteredApplicantList() {
        return model.getFilteredApplicantList();
    }

    @Override
    public ObservableList<Applicant> getApplicantList() {
        return model.getApplicantRegistry().getApplicantList();
    }

    @Override
    public ReadOnlyObjectProperty<Optional<ComparisonPair>> getCurrentPairProperty() {
        return model.getComparisonList().getComparisonState().currentPairProperty();
    }

    @Override
    public ObservableList<RankingEntry> getRanking() {
        return model.getRankingEngine().getRanking();
    }

    @Override
    public ReadOnlyObjectProperty<RankingReadiness> getRankingReadinessProperty() {
        return model.getComparisonList().readinessProperty();
    }

    @Override
    public GuiSettings getGuiSettings() {
        return model.getUserPrefs().getGuiSettings();
    }

    @Override
    public void setGuiSettings(GuiSettings guiSettings) {
        model.getUserPrefs().setGuiSettings(guiSettings);
    }
}
