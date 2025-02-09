package io.airlift.bootstrap;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;

class FuzzyMatcher
{
    private FuzzyMatcher() {}

    public static List<String> findSimilar(String key, Set<String> candidates, int count)
    {
        if (candidates.contains(key)) {
            throw new IllegalArgumentException("Key '%s' is already present in the %s candidates".formatted(key, candidates));
        }

        return candidates.stream()
                .map(candidate -> new Match(candidate, FuzzySearch.ratio(candidate, key)))
                .filter(match -> match.ratio() > 75)
                .sorted(comparingInt(Match::ratio).reversed())
                .limit(count)
                .map(Match::key)
                .collect(toImmutableList());
    }

    private record Match(String key, int ratio)
    {
        public Match
        {
            requireNonNull(key, "key is null");
            verify(ratio >= 0 && ratio < 100, "ratio must be in the [0, 100) range");
        }
    }
}
