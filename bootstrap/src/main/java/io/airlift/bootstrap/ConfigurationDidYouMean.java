package io.airlift.bootstrap;

import com.google.common.collect.ImmutableList;
import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import static com.google.common.base.Verify.verify;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;

public class ConfigurationDidYouMean
{
    private ConfigurationDidYouMean() {}

    public static List<String> didYouMean(String key, Set<String> allSeenProperties)
    {
        return didYouMean(key, allSeenProperties, 3);
    }

    public static List<String> didYouMean(String key, Set<String> allSeenProperties, int count)
    {
        if (allSeenProperties.contains(key)) {
            throw new IllegalArgumentException("Key '%s' is already present in the %s properties".formatted(key, allSeenProperties));
        }

        PriorityQueue<Match> matchQueue = new PriorityQueue<>(3, comparingInt(Match::ratio).reversed());
        for (String seenProperty : allSeenProperties) {
            int ratio = FuzzySearch.ratio(seenProperty, key);
            if (ratio > 75) { // At least 75% similarity
                matchQueue.add(new Match(seenProperty, ratio));
            }
        }

        int elements = min(count, matchQueue.size());
        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(elements);

        for (int i = 0; i < elements; i++) {
            builder.add(matchQueue.poll().key());
        }

        return builder.build();
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
