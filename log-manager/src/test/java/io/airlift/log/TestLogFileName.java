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
package io.airlift.log;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.collect.Comparators.isInOrder;
import static com.google.common.collect.Comparators.isInStrictOrder;
import static java.util.Comparator.naturalOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestLogFileName
{
    private static final String BASE_NAME = "server.log";

    @Test
    public void testNew()
    {
        assertLogFile(
                "20201122.010203",
                LocalDateTime.of(2020, 11, 22, 1, 2, 3),
                OptionalInt.of(0),
                OptionalInt.empty(),
                false);
        assertLogFile(
                "20201122.010203-4",
                LocalDateTime.of(2020, 11, 22, 1, 2, 3),
                OptionalInt.of(4),
                OptionalInt.empty(),
                false);
        assertLogFile(
                "20201122.010203.gz",
                LocalDateTime.of(2020, 11, 22, 1, 2, 3),
                OptionalInt.of(0),
                OptionalInt.empty(),
                true);
        assertLogFile(
                "20201122.010203-4.gz",
                LocalDateTime.of(2020, 11, 22, 1, 2, 3),
                OptionalInt.of(4),
                OptionalInt.empty(),
                true);
    }

    @Test
    public void testLegacy()
    {
        assertLogFile(
                "2020-11-22.4.log",
                LocalDateTime.of(2020, 11, 22, 0, 0),
                OptionalInt.empty(),
                OptionalInt.of(4),
                false);
        assertLogFile(
                "2020-11-22.4.log.gz",
                LocalDateTime.of(2020, 11, 22, 0, 0),
                OptionalInt.empty(),
                OptionalInt.of(4),
                true);
    }

    @Test
    public void testComparisonNew()
    {
        // different time stamps
        assertOrdering(createLogFile("20201122.010203"), createLogFile("20201122.010204"));

        // different index
        assertOrdering(createLogFile("20201122.010203"), createLogFile("20201122.010203-1"));
        assertOrdering(createLogFile("20201122.010203-1"), createLogFile("20201122.010203-2"));

        // different slugs
        assertOrdering(createLogFile("20201122.010203-apple"), createLogFile("20201122.010203-2"));
        assertOrdering(createLogFile("20201122.010203"), createLogFile("20201122.010203-apple"));
        assertOrdering(createLogFile("20201122.010203-apple"), createLogFile("20201122.010203-banana"));

        // compression doesn't matter
        assertEqualOrdering(createLogFile("20201122.010203.gz"), createLogFile("20201122.010203"));
    }

    @Test
    public void testComparisonLegacy()
    {
        // different time stamps
        assertOrdering(createLogFile("2020-11-22.0.log"), createLogFile("2020-11-23.0.log"));

        // different index
        assertOrdering(createLogFile("2020-11-22.2.log"), createLogFile("2020-11-22.1.log"));

        // compression doesn't matter
        assertEqualOrdering(createLogFile("2020-11-22.1.log.gz"), createLogFile("2020-11-22.1.log"));
    }

    @Test
    public void testComparisonNewAndLegacy()
    {
        // different timestamps
        assertOrdering(createLogFile("20201122.010203"), createLogFile("2020-11-23.0.log"));
        assertOrdering(createLogFile("2020-11-22.0.log"), createLogFile("20201122.010203"));

        // exact same second (new before legacy as legacy is more likely created after midnight)
        assertOrdering(createLogFile("20201122.000000"), createLogFile("2020-11-22.0.log"));

        // index does not break ties between two formats
        assertOrdering(createLogFile("20201122.000000-100"), createLogFile("2020-11-22.0.log"));
        assertOrdering(createLogFile("20201122.000000-apple"), createLogFile("2020-11-22.0.log"));

        // compression doesn't matter
        assertOrdering(createLogFile("20201122.000000.gz"), createLogFile("2020-11-22.0.log"));
        assertOrdering(createLogFile("20201122.000000"), createLogFile("2020-11-22.0.log.gz"));
    }

    @Test
    public void testGenerateNextLogFileName()
    {
        // note: no actual files are created here
        LogFileName logFileName = LogFileName.generateNextLogFileName(Paths.get(BASE_NAME), Optional.empty());
        assertEquals(logFileName.getIndex(), OptionalInt.of(0));
        assertEquals(logFileName.getLegacyIndex(), OptionalInt.empty());

        // verify the name round trips
        assertEqualOrdering(LogFileName.parseHistoryLogFileName(BASE_NAME, logFileName.getFileName()).orElseThrow(AssertionError::new), logFileName);
    }

    private static LogFileName createLogFile(String suffix)
    {
        return LogFileName.parseHistoryLogFileName(BASE_NAME, BASE_NAME + "-" + suffix).orElseThrow(AssertionError::new);
    }

    private static void assertLogFile(String suffix, LocalDateTime dateTime, OptionalInt index, OptionalInt legacyIndex, boolean compressed)
    {
        Path path = Paths.get(BASE_NAME + "-" + suffix);
        Optional<LogFileName> logFile = LogFileName.parseHistoryLogFileName(BASE_NAME, path.getFileName().toString());
        assertTrue(logFile.isPresent());
        assertEquals(logFile.get().getDateTime(), dateTime);
        assertEquals(logFile.get().getIndex(), index);
        assertEquals(logFile.get().getLegacyIndex(), legacyIndex);
        assertEquals(logFile.get().getSlug(), Optional.empty());
        assertEquals(logFile.get().isCompressed(), compressed);
    }

    private static void assertOrdering(LogFileName... logFileNames)
    {
        assertTrue(isInStrictOrder(ImmutableList.copyOf(logFileNames), naturalOrder()));
    }

    private static void assertEqualOrdering(LogFileName... logFileNames)
    {
        assertTrue(isInOrder(ImmutableList.copyOf(logFileNames), naturalOrder()));
        assertFalse(isInStrictOrder(ImmutableList.copyOf(logFileNames), naturalOrder()));
    }
}
