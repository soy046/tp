package seedu.address.model.ranking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import seedu.address.model.applicant.ApplicantId;

/** Immutable active comparison aggregates in canonical applicant order. */
public final class ComparisonStatistics {
    /** Aggregated judgments between two active applicants. */
    public record Edge(int first, int second, int firstWins, int secondWins) {
        public int count() {
            return firstWins + secondWins;
        }
    }

    private final List<ApplicantId> ids;
    private final Map<ApplicantId, Integer> indices;
    private final List<Edge> edges;
    private final int[] wins;
    private final int[] counts;
    private final int[] components;
    private final Map<Long, Integer> pairCounts;

    ComparisonStatistics(Set<ApplicantId> activeMembers, List<ComparisonStatisticsIndex.PairTally> tallies) {
        ids = new ArrayList<>(Set.copyOf(activeMembers));
        ids.sort(Comparator.comparing(id -> id.value));
        indices = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            indices.put(ids.get(i), i);
        }
        wins = new int[ids.size()];
        counts = new int[ids.size()];
        components = new int[ids.size()];
        pairCounts = new HashMap<>();
        List<Edge> activeEdges = new ArrayList<>();
        for (ComparisonStatisticsIndex.PairTally tally : tallies) {
            Integer first = indices.get(tally.first());
            Integer second = indices.get(tally.second());
            if (first == null || second == null) {
                continue;
            }
            int firstWins = tally.firstWins();
            int secondWins = tally.secondWins();
            if (first > second) {
                int swap = first;
                first = second;
                second = swap;
                int winsSwap = firstWins;
                firstWins = secondWins;
                secondWins = winsSwap;
            }
            Edge edge = new Edge(first, second, firstWins, secondWins);
            activeEdges.add(edge);
            wins[first] += firstWins;
            wins[second] += secondWins;
            counts[first] += edge.count();
            counts[second] += edge.count();
            pairCounts.put(pairKey(first, second), edge.count());
        }
        activeEdges.sort(Comparator.comparingInt(Edge::first).thenComparingInt(Edge::second));
        edges = List.copyOf(activeEdges);
        for (int i = 0; i < components.length; i++) {
            components[i] = i;
        }
        for (Edge edge : edges) {
            union(edge.first(), edge.second());
        }
        for (int i = 0; i < components.length; i++) {
            components[i] = find(i);
        }
    }

    public int size() {
        return ids.size();
    }

    public List<ApplicantId> ids() {
        return List.copyOf(ids);
    }

    public int indexOf(ApplicantId id) {
        return indices.getOrDefault(id, -1);
    }

    public List<Edge> edges() {
        return edges;
    }

    public int wins(int index) {
        return wins[index];
    }

    public int count(int index) {
        return counts[index];
    }

    public int pairCount(int first, int second) {
        return pairCounts.getOrDefault(pairKey(first, second), 0);
    }

    public int component(int index) {
        return components[index];
    }

    public boolean isConnected() {
        return Arrays.stream(components).distinct().count() <= 1;
    }

    private static long pairKey(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private int find(int index) {
        while (components[index] != index) {
            components[index] = components[components[index]];
            index = components[index];
        }
        return index;
    }

    private void union(int first, int second) {
        components[find(first)] = find(second);
    }
}
