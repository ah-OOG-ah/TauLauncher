package org.taumc.launcher.core.util;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetUtils {
    public record SetDiff<T>(Set<T> added, Set<T> removed) {}

    public static <T> SetDiff<T> diffSets(Set<T> oldSet, Set<T> newSet) {
        Set<T> added = new HashSet<>(newSet);
        added.removeAll(oldSet); // new - old

        Set<T> removed = new HashSet<>(oldSet);
        removed.removeAll(newSet); // old - new

        return new SetDiff<>(added, removed);
    }

    public static <T> List<String> formatGroupedDiff(Set<T> oldFiles, Set<T> newFiles) {
        SetDiff<T> diff = diffSets(oldFiles, newFiles);
        List<T> added = new ArrayList<>(diff.added());
        List<T> removed = new ArrayList<>(diff.removed());
        List<String> result = new ArrayList<>();

        LevenshteinDistance distance = new LevenshteinDistance();

        Set<T> matchedAdded = new HashSet<>();
        Set<T> matchedRemoved = new HashSet<>();

        for (T rem : removed) {
            T bestMatch = null;
            int bestScore = Integer.MAX_VALUE;

            for (T add : added) {
                int score = distance.apply(rem.toString(), add.toString());
                if (score < bestScore) {
                    bestScore = score;
                    bestMatch = add;
                }
            }

            if (bestMatch != null && bestScore < 10) { // threshold
                result.add("- " + rem);
                result.add("+ " + bestMatch);
                matchedRemoved.add(rem);
                matchedAdded.add(bestMatch);
            }
        }

        added.removeAll(matchedAdded);
        removed.removeAll(matchedRemoved);

        if (!added.isEmpty()) {
            result.add("\nADDED FILES:");
            added.forEach(p -> result.add("+ " + p));
        }

        if (!removed.isEmpty()) {
            result.add("\nREMOVED FILES:");
            removed.forEach(p -> result.add("- " + p));
        }

        return result;
    }
}
