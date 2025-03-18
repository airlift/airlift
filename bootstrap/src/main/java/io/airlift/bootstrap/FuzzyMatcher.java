package io.airlift.bootstrap;

import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparingDouble;
import static java.util.Objects.requireNonNull;

class FuzzyMatcher
{
    private static final StringSimilarity SIMILARITY = new JaroWinkler();

    private FuzzyMatcher() {}

    public static List<String> findSimilar(String key, Set<String> candidates, int count)
    {
        if (candidates.contains(key)) {
            throw new IllegalArgumentException("Key '%s' is already present in the %s candidates".formatted(key, candidates));
        }

        return candidates.stream()
                .map(candidate -> new Match(candidate, SIMILARITY.similarity(candidate, key)))
                .filter(match -> match.ratio() > 0.85)
                .sorted(comparingDouble(Match::ratio).reversed())
                .limit(count)
                .map(Match::key)
                .collect(toImmutableList());
    }

    private record Match(String key, double ratio)
    {
        public Match
        {
            requireNonNull(key, "key is null");
            verify(ratio >= 0.0 && ratio <= 1.0, "ratio must be in the [0, 1.0] range");
        }
    }
}
