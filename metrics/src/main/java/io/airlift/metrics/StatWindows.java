/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.metrics;

import com.google.common.collect.ImmutableList;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;

import java.util.List;

public final class StatWindows
{
    private StatWindows() {}

    public record Window<T>(String name, T value) {}

    public static List<Window<TimeDistribution>> windows(TimeStat timeStat)
    {
        return windows(timeStat.getOneMinute(), timeStat.getFiveMinutes(), timeStat.getFifteenMinutes(), timeStat.getAllTime());
    }

    public static List<Window<Distribution>> windows(DistributionStat distributionStat)
    {
        return windows(distributionStat.getOneMinute(), distributionStat.getFiveMinutes(), distributionStat.getFifteenMinutes(), distributionStat.getAllTime());
    }

    private static <T> List<Window<T>> windows(T oneMinute, T fiveMinutes, T fifteenMinutes, T allTime)
    {
        ImmutableList.Builder<Window<T>> windows = ImmutableList.builder();
        if (oneMinute != null) {
            windows.add(new Window<>("OneMinute", oneMinute));
        }
        if (fiveMinutes != null) {
            windows.add(new Window<>("FiveMinutes", fiveMinutes));
        }
        if (fifteenMinutes != null) {
            windows.add(new Window<>("FifteenMinutes", fifteenMinutes));
        }
        windows.add(new Window<>("AllTime", allTime));
        return windows.build();
    }
}
