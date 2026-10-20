---
  layout: default.md
  title: "Developer Guide"
  pageNav: 3
---

# RecruitDex Developer Guide

<!-- * Table of Contents -->
<page-nav-print />

--------------------------------------------------------------------------------------------------------------------

## **Overview**

RecruitDex helps recruiters rank applicants through a series of side-by-side comparisons. Instead of placing every applicant in a complete ranking at once, a recruiter chooses which of two applicants is stronger, and RecruitDex updates the ranking as decisions are made. Recruiters can review the full ranking of applicants in the active comparison list and use it to inform hiring decisions.

The workflow has three distinct parts:

1. **Manage applicant records.** Add applicants and maintain details such as names, contact information, and interview notes. Records can be edited or deleted.
2. **Manage the active comparison list.** Add or remove applicants from the set to be ranked. Adding an applicant record and adding that applicant to the comparison list are separate operations in the system, even if the interface offers them together.
3. **Compare applicants.** Open the comparison view, review the two applicants RecruitDex presents, and choose the stronger one. The ranking updates immediately so the recruiter can see the effect of each decision.

These parts can be revisited as recruitment progresses. An applicant must have a record before joining the comparison list and must join the list before being compared, but recruiters can compare existing applicants before adding more. RecruitDex is designed for command-line input, with a graphical interface primarily for displaying applicants, comparisons, and ranking changes.

--------------------------------------------------------------------------------------------------------------------

## **Acknowledgements**

* _{List the sources of reused or adapted ideas, code, documentation, and third-party libraries here, with links to the originals.}_

--------------------------------------------------------------------------------------------------------------------

## **Setting up, getting started**

Refer to the guide [_Setting up and getting started_](SettingUp.md).

--------------------------------------------------------------------------------------------------------------------

## **Design**

Some diagrams still show legacy AB3 class names.

### Architecture

<puml src="diagrams/ArchitectureDiagram.puml" width="280" />

The ***Architecture Diagram*** given above explains the high-level design of the App.

The following provides a quick overview of the main components and their interactions.

**Main components of the architecture**

**`Main`** (consisting of classes [`Main`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/Main.java) and [`MainApp`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/MainApp.java)) is in charge of the app launch and shut down.
* At app launch, it initializes the other components in the correct sequence, and connects them up with each other.
* At shut down, it shuts down the other components and invokes cleanup methods where necessary.

The bulk of the app's work is done by the following four components:

* [**`UI`**](#ui-component): The UI of the App.
* [**`Logic`**](#logic-component): The command executor.
* [**`Model`**](#model-component): Holds the data of the App in memory.
* [**`Storage`**](#storage-component): Reads data from, and writes data to, the hard disk.

[**`Commons`**](#common-classes) represents a collection of classes used by multiple other components.

**How the architecture components interact with each other**

The *Sequence Diagram* below shows how the components interact with each other for the scenario where the user issues the command `delete 1`.

<puml src="diagrams/ArchitectureSequenceDiagram.puml" width="574" />

Each of the four main components (also shown in the diagram above),

* defines its *API* in an `interface` with the same name as the Component.
* provides its functionality through a concrete `{Component Name}Manager` class that implements the corresponding API interface.

For example, the `Logic` component defines its API in `Logic.java` and implements it in `LogicManager.java`. Other components interact with a component through its interface rather than its concrete class, preventing them from coupling to that component's implementation, as illustrated in the following partial class diagram.

<puml src="diagrams/ComponentManagers.puml" width="300" />

The sections below give more details of each component.

### UI component

The **API** of this component is specified in [`Ui.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/ui/Ui.java)

<puml src="diagrams/UiClassDiagram.puml" alt="Structure of the UI Component"/>

The UI consists of a `MainWindow` and its parts, including `CommandBox`, `ResultDisplay`, `ApplicantListPanel`, `ComparisonPanel`, `RankingPanel`, and `StatusBarFooter`. These parts inherit from `UiPart`, which captures common behavior among visible GUI parts.

The `UI` component uses the JavaFX UI framework. The layouts of these UI parts are defined in matching `.fxml` files in `src/main/resources/view`. For example, [`MainWindow.fxml`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/resources/view/MainWindow.fxml) specifies the layout of [`MainWindow`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/ui/MainWindow.java).

The `UI` component,

* executes user commands using the `Logic` component.
* listens for changes to `Model` data so that the UI can be updated with the modified data.
* keeps a reference to the `Logic` component, because the `UI` relies on the `Logic` to execute commands.
* displays model objects such as `Applicant` and `RankingEntry`. The leaderboard observes `Logic.getRanking()` and resolves current applicant names from `Logic.getApplicantList()`.

### Logic component

**API** : [`Logic.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/logic/Logic.java)

Here's a (partial) class diagram of the `Logic` component:

<puml src="diagrams/LogicClassDiagram.puml" width="550"/>

The sequence diagram below illustrates the interactions within the `Logic` component, taking `execute("delete 1")` API call as an example.

<puml src="diagrams/DeleteSequenceDiagram.puml" alt="Interactions Inside the Logic Component for the `delete 1` Command" />

<box type="info" seamless>

**Note:** The lifeline for `DeleteCommandParser` should end at the destroy marker (X), but due to a limitation of PlantUML, the lifeline continues till the end of diagram.
</box>


How the `Logic` component works:

1. When `Logic` is called upon to execute a command, the command is passed to a `RecruitDexParser` object, which in turn creates a parser that matches the command (e.g., `DeleteCommandParser`) and uses it to parse the command.
1. This results in a `Command` object (more precisely, an object of one of its subclasses e.g., `DeleteCommand`) which is executed by the `LogicManager`.
1. The command can communicate with the `Model` when it is executed (e.g. to delete an applicant).<br>
   Note that although this is shown as a single step in the diagram above for simplicity, the code can require several interactions between the command object and the `Model` to complete the operation.
1. The result of the command execution is encapsulated as a `CommandResult` object which is returned from `Logic`.

Here are the other classes in `Logic` (omitted from the class diagram above) that are used for parsing a user command:

<puml src="diagrams/ParserClasses.puml" width="600"/>

How the parsing works:
* When called upon to parse a user command, the `RecruitDexParser` class creates an `XYZCommandParser` (`XYZ` is a placeholder for the specific command name, e.g., `AddCommandParser`). The parser uses the other classes shown above to parse the user command and create an `XYZCommand` object (e.g., `AddCommand`). The `RecruitDexParser` returns that object as a `Command` object.
* All `XYZCommandParser` classes, such as `AddCommandParser` and `DeleteCommandParser`, implement the `Parser` interface so they can be treated similarly where appropriate, for example during testing.

### Model component
**API** : [`Model.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/model/Model.java)

<puml src="diagrams/ModelClassDiagram.puml" width="650" />


The `Model` component,

* stores applicant records in `RecruitDexData` through `ApplicantRegistry`, accessed through `Model.getApplicantRegistry()`. The registry is shared by commands, UI views, and JSON storage.
* holds pending comparison state in `RecruitDexData` and a shared runtime `RankingEngine` in `ModelManager`.
* stores the `Applicant` objects selected by the current filter, such as search results, in a separate _filtered_ list. It exposes this list as an unmodifiable `ObservableList<Applicant>` that the UI can observe and bind to, so the UI updates when the list changes.
* stores GUI preferences in `UserPrefs`, accessed through `Model.getUserPrefs()` and saved separately.
* does not depend on any of the other three components (as the `Model` represents data entities of the domain, they should make sense on their own without depending on other components)

<box type="info" seamless>

**Note:** The alternative, arguably more object-oriented, design below keeps a unique list of tags in `AddressBook`, and each `Applicant` references tags from that list. This lets `AddressBook` maintain one `Tag` object per unique tag instead of each `Applicant` holding its own `Tag` objects.<br>

<puml src="diagrams/BetterModelClassDiagram.puml" width="450" />
</box>


### Storage component

**API** : [`Storage.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/storage/Storage.java)

<puml src="diagrams/StorageClassDiagram.puml" width="550" />

The `Storage` component,
* saves applicant records and user preferences in JSON format. Comparison membership, decisions, pending pairs, and rankings are not persisted yet.
* is implemented by `StorageManager`, which delegates JSON file access to `JsonRecruitDexDataStorage` and `JsonUserPrefsStorage` (one class per data file).
* depends on some classes in the `Model` component (because the `Storage` component's job is to save/retrieve objects that belong to the `Model`)

### Common classes

Classes used by multiple components are in the `seedu.address.commons` package.

--------------------------------------------------------------------------------------------------------------------

## **Implementation**

This section describes some noteworthy details on how certain features are implemented.

### Applicant ranking

`RankingEntry` is an immutable result containing an applicant ID, a positive position, an optional finite score, and a non-negative comparison count. Equality and hashing use all four fields.

`RankingEngine.recalculate(activeMembers, decisions)` uses a regularized Bradley-Terry model with Gamma priors of shape 2 and rate 1.

* All active applicants appear. Unassessed applicants have strength 1 and score 0.
* Decisions contribute when both participants are active. Repeated decisions with distinct IDs have equal weight.
* Scores are natural logarithms of strengths, rounded to six decimal places. Equal scores share positions such as `1, 1, 3`; applicant IDs order tied rows.
* The engine publishes completed results through a live, unmodifiable observable list. Invalid input or fitting failure preserves the previous ranking.

`ModelManager` owns the shared engine. `Logic.getRanking()` exposes its view to `RankingPanel`, which refreshes names from the full applicant list independently of search filtering. UI-bound observable updates must run on the JavaFX application thread.

Comparison-list and decision commands update the running app. Membership, decisions, and the pending pair are persisted. Ranking scores, posterior uncertainty, and readiness are rebuilt from that data. `ComparisonStatisticsIndex` lets `ComparisonList` refresh rankings from aggregated decisions without scanning the whole history after each choice. `PairSelector` uses coverage and expected information gain; the UI displays an evidence-readiness message without stopping the recruiter from making more comparisons.

### \[Proposed\] Undo/redo feature

#### Proposed Implementation

The proposed undo/redo mechanism is facilitated by `VersionedAddressBook`. It extends `AddressBook` with an undo/redo history, stored internally as an `addressBookStateList` and `currentStatePointer`. Additionally, it implements the following operations:

* `VersionedAddressBook#commit()` -- Saves the current address book state in its history.
* `VersionedAddressBook#undo()` -- Restores the previous address book state from its history.
* `VersionedAddressBook#redo()` -- Restores a previously undone address book state from its history.

These operations are exposed in the `Model` interface as `Model#commitAddressBook()`, `Model#undoAddressBook()` and `Model#redoAddressBook()` respectively.

Given below is an example usage scenario and how the undo/redo mechanism behaves at each step.

Step 1. The user launches the application for the first time. The `VersionedAddressBook` will be initialized with the initial address book state, and the `currentStatePointer` pointing to that single address book state.

<puml src="diagrams/UndoRedoState0.puml" alt="UndoRedoState0" />

Step 2. The user executes `delete 5` command to delete the 5th applicant in the address book. The `delete` command calls `Model#commitAddressBook()`, causing the modified state of the address book after the `delete 5` command executes to be saved in the `addressBookStateList`, and the `currentStatePointer` is shifted to the newly inserted address book state.

<puml src="diagrams/UndoRedoState1.puml" alt="UndoRedoState1" />

Step 3. The user executes `add n/David …​` to add a new applicant. The `add` command also calls `Model#commitAddressBook()`, causing another modified address book state to be saved into the `addressBookStateList`.

<puml src="diagrams/UndoRedoState2.puml" alt="UndoRedoState2" />

<box type="info" seamless>

**Note:** If a command fails its execution, it will not call `Model#commitAddressBook()`, so the address book state will not be saved into the `addressBookStateList`.
</box>

Step 4. The user now decides that adding the applicant was a mistake, and decides to undo that action by executing the `undo` command. The `undo` command will call `Model#undoAddressBook()`, which will shift the `currentStatePointer` once to the left, pointing it to the previous address book state, and restores the address book to that state.

<puml src="diagrams/UndoRedoState3.puml" alt="UndoRedoState3" />


<box type="info" seamless>

**Note:** If the `currentStatePointer` is at index 0, pointing to the initial AddressBook state, then there are no previous AddressBook states to restore. The `undo` command uses `Model#canUndoAddressBook()` to check if this is the case. If so, it will return an error to the user rather
than attempting to perform the undo.
</box>

The following sequence diagram shows how an undo operation goes through the `Logic` component:

<puml src="diagrams/UndoSequenceDiagram-Logic.puml" alt="UndoSequenceDiagram-Logic" />

<box type="info" seamless>

**Note:** The lifeline for `UndoCommand` should end at the destroy marker (X), but due to a limitation of PlantUML, it continues to the end of the diagram.
</box>

Similarly, how an undo operation goes through the `Model` component is shown below:

<puml src="diagrams/UndoSequenceDiagram-Model.puml" alt="UndoSequenceDiagram-Model" />

The `redo` command does the opposite — it calls `Model#redoAddressBook()`, which shifts the `currentStatePointer` once to the right, pointing to the previously undone state, and restores the address book to that state.

<box type="info" seamless>

**Note:** If the `currentStatePointer` is at index `addressBookStateList.size() - 1`, pointing to the latest address book state, then there are no undone AddressBook states to restore. The `redo` command uses `Model#canRedoAddressBook()` to check if this is the case. If so, it will return an error to the user rather than attempting to perform the redo.
</box>

Step 5. The user then decides to execute the command `list`. Commands that do not modify the address book, such as `list`, will usually not call `Model#commitAddressBook()`, `Model#undoAddressBook()` or `Model#redoAddressBook()`. Thus, the `addressBookStateList` remains unchanged.

<puml src="diagrams/UndoRedoState4.puml" alt="UndoRedoState4" />

Step 6. The user executes `clear`, which calls `Model#commitAddressBook()`. Since the `currentStatePointer` is not pointing at the end of the `addressBookStateList`, all address book states after the `currentStatePointer` will be purged. Reason: It no longer makes sense to redo the `add n/David …` command. This is the behavior that most modern desktop applications follow.

<puml src="diagrams/UndoRedoState5.puml" alt="UndoRedoState5" />

The following activity diagram summarizes what happens when a user executes a new command:

<puml src="diagrams/CommitActivityDiagram.puml" width="250" />

#### Design considerations:

**Aspect: How undo & redo execute:**

* **Alternative 1 (current choice):** Saves the entire address book.
  * Pros: Easy to implement.
  * Cons: May have performance issues in terms of memory usage.

* **Alternative 2:** Individual command knows how to undo/redo by
  itself.
  * Pros: Will use less memory (e.g. for `delete`, just save the applicant being deleted).
  * Cons: We must ensure that the implementation of each individual command is correct.

_{more aspects and alternatives to be added}_

### \[Proposed\] Data archiving

_{Explain here how the data archiving feature will be implemented}_


--------------------------------------------------------------------------------------------------------------------

## **Documentation, logging, testing, dev-ops**

* [Documentation guide](Documentation.md)
* [Testing guide](Testing.md)
* [Logging guide](Logging.md)
* [DevOps guide](DevOps.md)

--------------------------------------------------------------------------------------------------------------------

## **Appendix: Requirements**

### Product scope

**Target user profile**:

* recruits for software engineering roles and manages many applicants
* needs to compare applicants and explain how a shortlist was reached
* prefers desktop apps over other types of applications
* can type fast
* prefers typing to mouse interactions
* is reasonably comfortable using CLI apps

**Value proposition**:

RecruitDex helps recruiters make more systematic, reviewable hiring decisions by building a ranking from individual applicant comparisons.

* **Easier decisions:** Choosing between two applicants at a time is simpler than ordering the entire applicant pool in one sitting. Recruiters can make progress over multiple sessions and see the ranking evolve.
* **Less impact from a mistaken judgment:** One comparison need not permanently fix an applicant's position; further comparisons can provide more evidence and change the ranking.
* **Clearer oversight:** Recruitment managers can inspect the comparisons behind a ranking, require a minimum number of comparisons, and compare recruiters' decisions to identify possible anomalies.


### User stories

Priorities: High (must have) - `* * *`, Medium (nice to have) - `* *`, Low (unlikely to have) - `*`.

These stories describe the requirements backlog, including ideas considered for future development; they do not indicate which features are currently implemented. High-priority stories support the core applicant-management and comparison workflow. Medium-priority stories improve convenience or reviewability, while low-priority stories are optional extensions.

An applicant record stores an applicant's details. The active comparison list is the set of applicants currently being ranked; removing an applicant from this list does not delete their record.

| Priority | As a …                                          | I can …                                                                                      | So that I can …                                                                     |
|----------|-------------------------------------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `* * *`  | new recruiter using RecruitDex                  | view command usage instructions                                                              | learn the commands and refer to them when I forget the syntax                       |
| `* * *`  | recruiter                                       | add an applicant record with their name and contact information                              | keep the applicant's details available throughout recruitment                       |
| `* * *`  | recruiter                                       | list all applicant records                                                                   | review the applicants stored in RecruitDex                                          |
| `* * *`  | recruiter                                       | view an applicant's details                                                                  | review the information I need to assess or contact them                             |
| `* * *`  | recruiter                                       | edit an applicant's details                                                                  | keep their record accurate as new information becomes available                     |
| `* * *`  | recruiter                                       | record and update interview notes for an applicant                                           | retain evidence to inform later comparisons                                         |
| `* * *`  | recruiter                                       | delete an applicant record                                                                   | remove records that should no longer be retained                                    |
| `* * *`  | recruiter with many applicants                  | find an applicant by name                                                                    | retrieve their record without scanning the entire list                              |
| `* * *`  | recruiter                                       | add an applicant with an existing record to the active comparison list                       | include them in the ranking when I am ready to assess them                          |
| `* * *`  | recruiter                                       | view the active comparison list                                                              | check which applicants are included in the ranking                                  |
| `* * *`  | recruiter                                       | remove an applicant from the active comparison list while retaining their record             | exclude them from the current ranking and keep their details for future reference   |
| `* * *`  | recruiter                                       | view the two applicants presented for comparison side by side                                | assess their relative strengths without switching between records                   |
| `* * *`  | recruiter                                       | choose the stronger applicant in a presented pair                                            | contribute a decision to the applicant ranking                                      |
| `* * *`  | recruiter                                       | see the updated ranking immediately after each comparison                                    | understand how my latest decision affects the applicants' positions                 |
| `* * *`  | recruiter                                       | view the full ranking of applicants in the active comparison list                            | review their relative positions to inform hiring decisions                          |
| `* * *`  | recruiter                                       | retain applicant records, comparison-list membership, and ranking progress between sessions  | continue recruitment without re-entering data or repeating completed work           |
| `* *`    | new recruiter using RecruitDex                  | view sample applicant records                                                                | understand how applicant information is organised before entering real data         |
| `* *`    | new recruiter using RecruitDex                  | clear the sample data                                                                        | begin recruitment with my own applicant records                                     |
| `* *`    | recruiter                                       | be warned when adding a duplicate applicant record                                           | avoid accidentally recording the same applicant more than once                      |
| `* *`    | recruiter                                       | record an applicant's skills                                                                 | keep relevant qualifications available when assessing them                          |
| `* *`    | recruiter with many applicants                  | filter applicants by their recorded skills                                                   | focus on applicants with skills relevant to the role                                |
| `* *`    | recruiter                                       | undo an accidental deletion of an applicant record                                           | recover information that I still need                                               |
| `* *`    | recruiter                                       | export the ranked applicant list                                                             | share the ranking with hiring managers for review                                   |
| `* *`    | recruiter                                       | record each applicant's recruitment source, such as LinkedIn or the company website          | track which channels supplied the applicants                                        |
| `* *`    | recruiter                                       | record and sort applicants by years of experience                                            | identify applicants whose experience matches the role's seniority                   |
| `* *`    | recruiter                                       | copy an applicant's contact information                                                      | use it quickly in another communication tool                                        |
| `* *`    | recruiter                                       | hide applicants' private contact details from the display                                    | reduce accidental exposure when showing the ranking to others                       |
| `* *`    | recruitment manager                             | review the recorded applicant comparisons behind a ranking                                   | understand the decisions that led to a shortlist                                    |
| `* *`    | recruitment manager                             | view how many comparisons each applicant has participated in                                 | identify applicants whose positions need more supporting evidence                   |
| `* *`    | recruitment manager                             | specify a minimum number of comparisons per applicant and see which applicants fall below it | request further assessment before relying on the shortlist                          |
| `*`      | recruiter hiring through other platforms        | import applicant records from a file                                                         | reduce manual entry when transferring existing applicant information                |
| `*`      | recruiter with many applicants                  | sort applicant records alphabetically by name                                                | locate a record while browsing the applicant list                                   |
| `*`      | recruiter                                       | save my preferred skills as reusable filter criteria                                         | focus on relevant applicants without re-entering the same criteria each session     |
| `*`      | recruiter hiring for software engineering roles | view a summary of an applicant's GitHub activity                                             | consider their coding activity alongside interview notes without leaving RecruitDex |
| `*`      | recruitment manager                             | compare decisions made by different recruiters about the same applicants                     | identify disagreements or possible anomalies that need further review               |

### Use cases

For all use cases below, the **System** is RecruitDex and the **Actor** is a recruiter. **MSS** means Main Success Scenario. These use cases describe intended product behaviour rather than the current implementation. Requests are entered as commands.

**UC01: Add an applicant record**

**MSS**

1. Recruiter requests to add an applicant, providing their name and contact information.
2. RecruitDex validates the supplied details.
3. RecruitDex stores the applicant record and displays a success message with the added details.

   Use case ends. The applicant is available for inclusion in the active comparison list through UC02.

**Extensions**

* 2a. Required details are missing or a supplied value is invalid.
    * 2a1. RecruitDex explains the error without adding a record.

      Use case resumes at step 1.

* 2b. The supplied details match an existing applicant record.
    * 2b1. RecruitDex warns the recruiter about the duplicate without adding another record.

      Use case ends.

**UC02: Add an existing applicant to the active comparison list**

**MSS**

1. Recruiter requests to list applicant records.
2. RecruitDex displays the applicant records.
3. Recruiter requests to add a selected applicant to the active comparison list.
4. RecruitDex adds the applicant and displays the updated comparison list.

   Use case ends.

**Extensions**

* 2a. No applicant records exist.
    * 2a1. RecruitDex indicates that there are no applicant records to display.

      Use case ends. The recruiter can create a record through UC01.

* 3a. The selection does not identify an existing applicant record.
    * 3a1. RecruitDex explains the error without changing the comparison list.

      Use case resumes at step 3.

* 3b. The selected applicant is already in the active comparison list.
    * 3b1. RecruitDex informs the recruiter that the applicant is already included, leaving the list unchanged.

      Use case resumes at step 3.

**UC03: Remove an applicant from the active comparison list**

**MSS**

1. Recruiter requests to view the active comparison list.
2. RecruitDex displays the applicants in the list.
3. Recruiter requests to remove a selected applicant from the list.
4. RecruitDex removes the applicant from the active comparison list and displays the ranking of the remaining applicants.

   Use case ends. The applicant's record remains available.

**Extensions**

* 2a. The active comparison list is empty.
    * 2a1. RecruitDex indicates that there are no applicants in the comparison list.

      Use case ends.

* 3a. The selection does not identify an applicant in the active comparison list.
    * 3a1. RecruitDex explains the error without changing the list or applicant records.

      Use case resumes at step 3.

**UC04: Compare two applicants and update the ranking**

**MSS**

1. Recruiter requests to open the comparison view.
2. RecruitDex presents two applicants from the active comparison list side by side, including their details and interview notes.
3. Recruiter reviews the applicants and submits a choice of the stronger applicant.
4. RecruitDex records the decision, updates the ranking, and immediately displays the updated ranking.

   Use case ends. The recruiter can repeat this use case to make further comparisons.

**Extensions**

* 2a. Fewer than two applicants are in the active comparison list.
    * 2a1. RecruitDex explains that at least two applicants are required for a comparison.

      Use case ends. The recruiter can add applicants to the list through UC02.

* 3a. The submitted choice does not identify either applicant in the presented pair.
    * 3a1. RecruitDex explains the error without recording a decision or changing the ranking.

      Use case resumes at step 3 with the same pair.

**UC05: Review the full applicant ranking**

**MSS**

1. Recruiter requests to view the applicant ranking.
2. RecruitDex displays all applicants in the active comparison list in ranking order, starting with the highest-ranked applicant.
3. Recruiter selects an applicant from the displayed ranking to view their details.
4. RecruitDex displays the selected applicant's details and interview notes.

   Use case ends.

**Extensions**

* 2a. The active comparison list is empty.
    * 2a1. RecruitDex indicates that there are no applicants to rank.

      Use case ends.

* 3a. The selection does not identify an applicant in the displayed ranking.
    * 3a1. RecruitDex explains the error and retains the displayed ranking.

      Use case resumes at step 3.

**UC06: Delete an applicant record**

**MSS**

1. Recruiter requests to list applicant records.
2. RecruitDex displays the applicant records.
3. Recruiter requests to delete a selected applicant record.
4. RecruitDex deletes the record and displays a success message identifying the deleted applicant.

   Use case ends.

**Extensions**

* 2a. No applicant records exist.
    * 2a1. RecruitDex indicates that there are no applicant records to display.

      Use case ends.

* 3a. The selection does not identify an existing applicant record.
    * 3a1. RecruitDex explains the error without deleting any records.

      Use case resumes at step 3.

* 4a. The applicant is in the active comparison list.
    * 4a1. RecruitDex also removes the applicant from the comparison list and updates the ranking to include only the remaining applicants.

      Use case ends. Unlike UC03, the applicant's record is deleted.

### Non-Functional Requirements

**Performance**
1. CRUD operations such as adding, deleting and editing should be finished within 2 seconds, for up to 200 candidates.
2. After all candidate comparisons have been completed the application should finish ranking the candidates within 10 seconds.
3. RecruitDex should remain responsive while containing up to 1000 candidates.

**Reliability**
1. The application should not terminate and should show a clear error message if an invalid input is received, after which it should continue working normally.

**Portability**
1. The application should run on Linux, MacOS and Windows with Java 25 installed.
2. The application should work without requiring an installer.

**Persistence**
1. All data stored in the address book, including rankings and attribute values should be stored in a file so that data persists between sessions.

**Usability**
1. A recruiter new to the app should be able to learn to perform the core functions of the app, such as adding and comparing applicants with minimal guidance.
2. If the user changes candidate data by mistake, undoing the mistake should be quick, within 10 seconds, and easy.

**Availability**
1. All features of RecruitDex should be fully usable without an internet connection.
2. All features of RecruitDex should be fully usable without requiring multiple users.

*{More to be added}*

### Glossary

- **Active Comparison List:** The set of applicant records currently included in the ranking process. Removing an applicant from this list does not delete their applicant record.

- **Applicant Record:** The stored information associated with an applicant, including their name, contact information, and interview notes.

- **API (Application Programming Interface):** An interface that defines how other components can interact with a component without needing to know its internal implementation.

- **Command:** An operation requested by the user through the command-line interface, such as adding, deleting, or comparing applicants.

- **Command Parser:** A component that interprets a user's command input and converts it into the corresponding Command object.

- **Command-Line Interface (CLI):** The text-based interface through which users enter commands to interact with RecruitDex.

- **Command Result:** An object representing the outcome of executing a command. It is returned by the Logic component after a command has been processed.

- **Comparison:** A decision made by a recruiter between two applicants presented by RecruitDex. The selected applicant is considered stronger for that comparison.

- **Comparison View:** The part of the user interface that presents two applicants side by side for the recruiter to compare.

- **Component:** A major architectural part of RecruitDex. The main components are UI, Logic, Model, and Storage, with Commons containing classes shared across components.

- **Filtered List:** A list maintained by the Model containing applicants that satisfy the current filtering criteria. The UI observes this list to update the displayed applicants.

- **JavaFX:** The Java framework used by RecruitDex to implement its graphical user interface.

- **JSON (JavaScript Object Notation):** A text-based data format used by RecruitDex to store applicant data and user preferences.

- **Logic:** The component responsible for interpreting and executing user commands.

- **Mainstream OS:** Windows, Linux, Unix, or macOS

- **Main Success Scenario (MSS):** The normal sequence of steps in a use case where the user achieves the intended outcome without encountering an error or exception.

- **Model:** The component responsible for maintaining RecruitDex's application data in memory, including applicant records and user preferences.

- **Ranking:** The ordered list of applicants in the active comparison list, based on the outcomes of comparisons.

- **RecruitDex:** The application that helps recruiters manage applicants and produce a ranking through pairwise comparisons.

- **Storage:** The component responsible for saving RecruitDex's data to persistent storage and loading it when the application starts.

- **Use Case (UC):** A description of how a user interacts with RecruitDex to accomplish a particular goal, including the normal sequence of actions and possible extensions or errors.

- **User Interface (UI):** The component responsible for displaying RecruitDex's graphical interface and receiving user commands.

- **User Preferences:** Settings associated with the user, such as GUI-related settings, that are stored separately from applicant data.

- **Versioned Address Book:** The mechanism described in the proposed undo/redo implementation for maintaining previous states of the application's data so that earlier states can be restored.

- **Undo/Redo:** Functionality that allows a user to reverse a previous change or restore a change that was previously undone.

--------------------------------------------------------------------------------------------------------------------

## **Appendix: Instructions for manual testing**

Given below are instructions to test the app manually.

<box type="info" seamless>

**Note:** These instructions only provide a starting point for testers to work on;
testers are expected to do more *exploratory* testing.
</box>

### Launch and shutdown

1. Initial launch

   1. Download the JAR file and copy it into an empty folder.

   1. Double-click the JAR file.<br>
      Expected: The GUI opens with sample applicants and an empty ranking panel showing **No applicants in the comparison list.** The window size may not be optimal.

1. Saving window preferences

   1. Resize the window to an optimal size. Move the window to a different location. Close the window.

   1. Relaunch the app by double-clicking the JAR file.<br>
       Expected: The most recent window size and location are retained.

1. _{ more test cases … }_

### Deleting an applicant

1. Deleting an applicant while all applicants are being shown

   1. Prerequisites: List all applicants using the `list` command, with multiple applicants in the list.

   1. Test case: `delete 1`<br>
      Expected: The first contact is deleted from the list. The status message shows the deleted contact's details.

   1. Test case: `delete 0`<br>
      Expected: No applicant is deleted. The status message shows error details.

   1. Other incorrect delete commands to try: `delete`, `delete x`, `...` (where x is larger than the list size)<br>
      Expected: Similar to previous.

1. _{ more test cases … }_

### Saving data

1. Dealing with missing/corrupted data files

   1. _{Explain how to simulate missing or corrupted data files and state the expected behavior.}_

1. _{ more test cases … }_
