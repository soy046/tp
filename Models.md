# RecruitDex model structure

This document describes the intended model structure for RecruitDex. It is a design proposal to guide the migration from AddressBook Level 3 (AB3), rather than a description of what is already implemented.

The [Developer Guide overview](docs/DeveloperGuide.md#overview) defines the intended product behavior. The guide's existing [Model component section](docs/DeveloperGuide.md#model-component) describes the legacy AB3 structure.

## How the components fit together

Most of RecruitDex's application behavior belongs in **Model**. It owns the applicant data, comparison membership, decisions, and rules that keep them consistent.

- **UI** is the user-facing outlet: it accepts input and displays applicants, comparisons, and rankings.
- **Logic** interprets and executes commands between UI and Model. Its name is misleading: it handles commands, while the recruitment rules belong in Model.
- **Storage** is the persistent file storage outlet: it loads and saves model data.

Model should work without depending on UI, Logic, or Storage. Commands ask Model to perform operations; Model enforces the rules regardless of which command requested them.

## Three things to keep separate

1. **Applicant records:** everyone the recruiter has added, including people who are not currently being ranked.
2. **Comparison-list membership:** the applicants currently included in the ranking.
3. **Comparison decisions:** the recruiter's choices between pairs of applicants.

For example, adding Alice creates her record. Adding Alice to the comparison list makes her eligible for comparisons. Choosing Alice over Bob records a decision and updates the ranking. These are separate operations, even if a command offers to perform more than one together.

## Structure at a glance

```text
ModelManager
|-- RecruitDexData                  application data, saved in one file
|   |-- ApplicantRegistry
|   |   `-- Applicant records
|   `-- ComparisonList
|       |-- Member ApplicantIds
|       |-- ComparisonDecision history
|       |-- ComparisonState
|       |   `-- Optional ComparisonPair
|       `-- PairSelector             runtime collaborator, not saved
|-- RankingEngine                   calculates the ranking
|-- BayesianRankingModel            runtime uncertainty model
|-- RankingReadinessEvaluator       runtime evidence certificate
|   `-- RankingEntry results         derived runtime values, not saved
|-- filteredApplicants              displayed subset, runtime state
`-- UserPrefs                       preferences, saved separately
```

`Model` remains the interface used by commands. `ModelManager` implements that interface, groups the objects above, and maintains the applicant filter. For the other objects, it exposes getters rather than forwarding every method. Recruitment rules and coordination between domain objects belong in the relevant model classes.

`RecruitDexData` groups the application data saved and restored together: applicant records, comparison membership, decision history, and the pending comparison. `ModelManager` holds this data object alongside runtime collaborators and separately persisted `UserPrefs`.

The two classes serve different purposes: `RecruitDexData` is the application's saved state, while `ModelManager` provides access to the model objects and owns the displayed applicant subset. Ranking calculations and search filters are not part of the saved data.

## ModelManager access and applicant filtering

Expose the contained objects through getters such as `getApplicantRegistry()`, `getComparisonList()`, `getRankingEngine()`, and `getUserPrefs()`. Getters for domain objects can access them through `RecruitDexData`; they should return the live objects rather than copies. The `Model` interface declares these accessors rather than duplicating each object's operations. `ComparisonList` owns access to `ComparisonState` and coordinates `PairSelector`; neither is exposed as a separate component by `RecruitDexData` or `Model`. Access the pending state through `model.getComparisonList().getComparisonState()`.

For example, a command calls `model.getApplicantRegistry().delete(id)` instead of `model.deleteApplicant(id)`. Similarly, comparison commands use `model.getComparisonList()` to perform comparison operations. Each object protects its internal state and provides complete operations, including collaboration with other models when needed. Deleting an applicant must also remove comparison references and invalidate an affected pair; commands should not have to perform those steps themselves. Exposing an object does not mean exposing mutable collections that bypass its rules.

For now, `ModelManager` continues to own `filteredApplicants`, a `FilteredList<Applicant>` backed by the registry's observable applicant list. It provides `getFilteredApplicantList()` and `updateFilteredApplicantList(predicate)` directly. The UI observes a read-only view of this filtered list, and registry updates automatically flow into it.

Search commands update this shared filter. Commands using displayed indices, such as `delete 2`, resolve the applicant from the same filtered list that the UI displays before invoking the registry operation. The filter affects display only, not comparison membership, and is not persisted. If loading replaces the registry object, `ModelManager` must reconnect the filtered view to the new registry's observable list.

## Applicant records

Keep `applicant` as the folder and package name for applicant-related classes: `src/main/java/seedu/address/model/applicant/` (`seedu.address.model.applicant`). It contains `ApplicantRegistry`, `Applicant`, `ApplicantId`, and other related types. These classes share this folder rather than introducing an `applicantregistry` folder or a nested folder for `Applicant`.

```text
model/applicant/
|-- ApplicantRegistry.java
|-- Applicant.java
|-- ApplicantId.java
`-- Other applicant-related files
```

### ApplicantId

A stable identifier for an applicant, such as a UUID. It remains unchanged when the recruiter edits the applicant's name or contact details, and is preserved when saving and loading.

Names are not identifiers: two applicants can share a name. Comparisons and membership refer to applicants by ID, so edits do not break those relationships. Duplicate-record detection, if needed, is a separate rule from identity.

### Applicant

An applicant's ID, name, phone number, email, interview notes, and optional tags. Existing AB3 value classes such as `Name`, `Phone`, `Email`, and `Tag` can be reused where their validation fits RecruitDex.

Keep applicant records immutable, following the existing `Person` approach. An edit replaces the record while preserving its ID. Rank and ranking score do not belong here: they depend on comparison decisions and the active pool.

### ApplicantRegistry

The single source of applicant records. It supports adding, editing, deleting, and looking up applicants by ID, and prevents duplicate IDs. Membership and decisions reference this registry instead of storing their own copies of applicant details.

## Comparison membership and decisions

Keep comparison-related classes together in `src/main/java/seedu/address/model/comparison/` (`seedu.address.model.comparison`), under the responsibility of `ComparisonList`:

```text
model/comparison/
|-- ComparisonList.java
|-- ReadOnlyComparisonList.java
|-- ComparisonState.java
|-- ComparisonPair.java
|-- ComparisonDecision.java
|-- PairSelector.java
`-- exceptions/
    `-- DuplicateComparisonMemberException.java
```

### ComparisonList

Owns a unique set of applicant IDs representing the active pool, together with an ordered history of comparison decisions and the pending `ComparisonState`. It coordinates the runtime `PairSelector` and ranking refreshes. Start with one comparison list; the overview does not require multiple recruitment campaigns or independent rankings.

`getComparisonState()` provides access to pending-pair observation. Pair changes must still enforce active membership through `ComparisonList`; exposing state must not provide a way to bypass that rule.

An applicant must exist in the registry before joining. Adding a record does not automatically add membership, and removing membership does not delete the record. A search filter only changes what is displayed; it does not change this pool.

### ComparisonDecision

An immutable record of one completed judgment: a decision ID, the two applicant IDs, the winner's ID, and a timestamp. Store decisions in execution order rather than relying on timestamps alone for ordering.

Each repeated comparison produces a new decision. Keeping the decisions, rather than only a final score, allows the recruiter to inspect the evidence behind a ranking and allows the ranking to be recalculated.

### ComparisonPair and ComparisonState

`ComparisonPair` holds two distinct applicant IDs presented for comparison. `ComparisonState` holds the current pair, if one is available. An unanswered pair is not a decision and has no effect on ranking.

Persist `ComparisonState` so the user can resume an unanswered comparison after reopening the app. Preserve the order of the pair's applicant IDs so the applicants appear on the same sides. Selecting or replacing a pair must be saved even when no decision has been recorded.

With fewer than two active applicants, no pair is available. Changes to membership or records must not leave the UI displaying an invalid pair. Applicant details are resolved from the registry so edits appear in the comparison view.

## Ranking and pair selection

Keep ranking-related classes together in `src/main/java/seedu/address/model/ranking/` (`seedu.address.model.ranking`), under the responsibility of `RankingEngine`:

```text
model/ranking/
|-- RankingEngine.java
`-- RankingEntry.java
```

### RankingEngine and RankingEntry

`RankingEngine` derives the full ranking from the active membership and relevant decisions. It produces `RankingEntry` values containing an applicant ID, position, optional score, and comparison count. These are display results, not another collection of applicant records.

All active applicants should appear in the ranking, including applicants with no decisions yet. The algorithm must define their initial standing and how ties are displayed; ordering tied applicants for display should not imply evidence that one is stronger.

The current ranking uses a regularized Bradley-Terry fit with Gamma(2, 1) priors. Keep its implementation behind `RankingEngine`: repeated comparisons and reversals each count once, unassessed applicants start at a neutral score, and rounded-score ties share a displayed position.

### PairSelector

Chooses distinct active applicants, or no pair when fewer than two members are active. It first covers under-assessed applicants and connects separate comparison groups, then uses expected information gain under a Laplace approximation to the Bradley-Terry posterior. The candidate shortlist is bounded at 64 pairs; repeated comparisons remain valid, but an immediate rematch is avoided when another pair is available. `ComparisonList` selects an initial pair when membership reaches two and subsequent pairs after decisions or when membership changes invalidate the pending pair. Adding another member preserves a valid pending pair. Loading restores the saved pair without selecting a replacement.

`ComparisonStatisticsIndex` retains per-pair wins, losses, and unique decision IDs for all stored judgments, including decisions involving inactive applicants. Active snapshots are derived in canonical applicant order. The ranking engine and posterior warm-start from completed results; uncertainty uses a sparse precision operator and bounded conjugate-gradient difference solves. The full decision history remains the persistent source of truth.

For `n` active applicants, `e` retained distinct comparison pairs, `I` fit iterations, at most `K = 64` candidate pairs, and at most `T = 500` solver iterations, an ordinary decision refresh is `O(I(n + e) + KT(n + e))` time and `O(n + e)` additional workspace, apart from the retained `O(h)` decision history and ID index. Candidate generation and readiness checks are linear in `n` for the fixed maximum close-group span. Permanent deletion rebuilds aggregates from history once.

`RankingReadinessEvaluator` reports insufficient data until every active applicant has five comparisons and the active comparison graph is connected. It then checks simultaneous 95% confidence bounds for adjacent ranks. Small genuinely close groups may remain uncertain in a ready ranking, with a maximum span of two positions. The status advises the recruiter, who chooses when to stop; pair selection continues after readiness. Thresholds are stored in `UserPrefs` as `ComparisonSettings`.

## Changes and consistency rules

Model must enforce the following rules:

- Every member and every decision participant has an applicant record.
- Every presented pair contains two distinct active members.
- A submitted winner belongs to the current pair, whose members are still active.
- Editing applicant details preserves identity and comparison history.
- Recording a decision and refreshing the ranking form one successful model operation. A rejected choice changes neither.
- Membership changes refresh the ranking and clear or replace any invalid current pair.

The recommended initial removal policies are:

| Operation | Effect |
| --- | --- |
| Remove from comparison list | Keep the record and history, but exclude decisions involving inactive applicants when recalculating the current ranking. |
| Add back to comparison list | Make retained decisions involving that applicant eligible again when both participants are active. |
| Permanently delete applicant | Remove the record, membership, and decisions involving that applicant, then recalculate the ranking. |

These policies are proposed design choices, not requirements stated in the overview. They should be settled before implementing removal and deletion, particularly if permanent audit history is later required.

## Persistence and model access

`RecruitDexData` is the root object for application data persisted in one file. It contains `ApplicantRegistry` and `ComparisonList`; `ComparisonList` contains `ComparisonState`, including the pending pair. A read-only interface, such as `ReadOnlyRecruitDexData`, gives Storage access without allowing it to mutate live data. Persist comparison membership, decisions, and state, but rebuild runtime collaborators such as `PairSelector` and `RankingEngine`. `UserPrefs` is persisted in a separate file.

`ReadOnlyRecruitDexData.getComparisonList()` returns a `ReadOnlyComparisonList` view of membership, ordered decisions, and the pending pair. The mutable data object returns its live `ComparisonList`. Resetting application data restores the existing registry and comparison list in place, retaining their observable views and the current-pair property. `ModelManager` supplies the runtime ranking engine to the data object's comparison list so restoration also refreshes the ranking observed by the UI.

Save and restore records, membership, decisions, and the pending pair together. Loading must validate relationships as well as individual field values, including that a restored pair contains two distinct active applicants who exist in the registry.

The search filter and calculated ranking are runtime state. Rebuild the ranking after loading and restore a valid saved pair without recording a decision or choosing a replacement. Restoring a pair does not require persisting which UI view was open.

Through `Model` getters, commands access the objects responsible for applicant records, membership, comparisons, and preferences, then call their operations directly. `ModelManager` retains the applicant-filter methods. Expose read-only observable views of applicants, active members, decisions, the current pair, and ranking so the UI can react to changes without modifying domain collections directly.

## Migration from AB3

`Applicant` takes over the role of `Person`, `ApplicantRegistry` takes over the record-management role of `UniquePersonList`, and `RecruitDexData` replaces `AddressBook` as the root for application data saved in one file. Keep the existing `Model` / `ModelManager` boundary, separately persisted preferences, and observable-view approach where useful.

Replace method-by-method forwarding in `Model` and `ModelManager` with object getters, while retaining the shared applicant-filter methods in `ModelManager`. Update commands to use those getters and invoke operations on the responsible objects.

The main additions are stable applicant identity, independent comparison membership, decision history, pair selection, and derived rankings. Implement these as model behavior, with commands and file storage calling into that structure.
