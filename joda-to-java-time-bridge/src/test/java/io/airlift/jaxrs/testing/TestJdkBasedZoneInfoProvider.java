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

package io.airlift.jaxrs.testing;

import io.airlift.jaxrs.jodabridge.JdkBasedZoneInfoProvider;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalInstantException;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestJdkBasedZoneInfoProvider
{
    @BeforeClass
    protected void validateJodaZoneInfoProvider()
    {
        try {
            JdkBasedZoneInfoProvider.registerAsJodaZoneInfoProvider();
        }
        catch (RuntimeException e) {
            throw new RuntimeException("Set the following system property to JVM running the test: -Dorg.joda.time.DateTimeZone.Provider=com.facebook.presto.tz.JdkBasedZoneInfoProvider");
        }
    }

    @Test
    public void test()
    {
        // This test puts extra focus on zone names because of how JdkBasedDateTimeZone.getNameKey is implemented.

        DateTime dateTimePST = new DateTime(2018, 1, 1, 2, 30, DateTimeZone.forID("America/Los_Angeles"));
        assertEquals(dateTimePST.toString(), "2018-01-01T02:30:00.000-08:00");
        assertEquals(DateTimeFormat.fullDateTime().print(dateTimePST), "Monday, January 1, 2018 2:30:00 AM PST");
        assertEquals(DateTimeFormat.forPattern("z',' zzzz',' Z',' ZZ',' ZZZ").print(dateTimePST), "PST, Pacific Standard Time, -0800, -08:00, America/Los_Angeles");
        assertEquals(dateTimePST.plusMonths(6).toString(), "2018-07-01T02:30:00.000-07:00");

        DateTime dateTimeUTC = new DateTime(2018, 1, 1, 2, 30, DateTimeZone.forID("UTC"));
        assertEquals(dateTimeUTC.toString(), "2018-01-01T02:30:00.000Z");
        assertEquals(DateTimeFormat.fullDateTime().print(dateTimeUTC), "Monday, January 1, 2018 2:30:00 AM UTC");
        assertEquals(DateTimeFormat.forPattern("z',' zzzz',' Z',' ZZ',' ZZZ").print(dateTimeUTC), "UTC, Coordinated Universal Time, +0000, +00:00, UTC");
        assertEquals(dateTimeUTC.plusMonths(6).toString(), "2018-07-01T02:30:00.000Z");

        DateTime dateTimeOffset = new DateTime(2018, 1, 1, 2, 30, DateTimeZone.forOffsetHours(-8));
        assertEquals(dateTimeOffset.toString(), "2018-01-01T02:30:00.000-08:00");
        assertEquals(DateTimeFormat.fullDateTime().print(dateTimeOffset), "Monday, January 1, 2018 2:30:00 AM -08:00");
        assertEquals(DateTimeFormat.forPattern("z',' zzzz',' Z',' ZZ',' ZZZ").print(dateTimeOffset), "-08:00, -08:00, -0800, -08:00, -08:00");
        assertEquals(dateTimeOffset.plusMonths(6).toString(), "2018-07-01T02:30:00.000-08:00");

        DateTime dateTimeWeird = new DateTime(2018, 1, 1, 2, 30, DateTimeZone.forID("+07:09"));
        assertEquals(dateTimeWeird.toString(), "2018-01-01T02:30:00.000+07:09");
        assertEquals(DateTimeFormat.fullDateTime().print(dateTimeWeird), "Monday, January 1, 2018 2:30:00 AM +07:09");
        assertEquals(DateTimeFormat.forPattern("z',' zzzz',' Z',' ZZ',' ZZZ").print(dateTimeWeird), "+07:09, +07:09, +0709, +07:09, +07:09");
        assertEquals(dateTimeWeird.plusMonths(6).toString(), "2018-07-01T02:30:00.000+07:09");
    }

    @Test
    public void testEarlierInstantForLiteralInOverlaps()
    {
        // DateTime constructor behavior for overlaps is not documented in joda.
        // In java.time, the corresponding method is documented to return the earlier instant.
        // Nevertheless, the implemented behavior matches java.time.
        // In addition, if the behavior changes, even though it is NOT a violation of contract in itself,
        // a method that this constructor delegates likely violated its contract.

        // overlap in Los_Angeles: 2013-11-3 1:00:00 to 1:59:59 repeats
        DateTime dateTimeLosAngeles = new DateTime(2013, 11, 3, 1, 30, DateTimeZone.forID("America/Los_Angeles"));
        assertEquals(dateTimeLosAngeles.minusHours(1).getHourOfDay(), 0);
        assertEquals(dateTimeLosAngeles.plusHours(1).getHourOfDay(), 1);

        // overlap in Berlin: 2013-10-27 2:00:00 to 2:59:59 repeats
        DateTime dateTimeBerlin = new DateTime(2013, 10, 27, 2, 30, DateTimeZone.forID("Europe/Berlin"));
        assertEquals(dateTimeBerlin.minusHours(1).getHourOfDay(), 1);
        assertEquals(dateTimeBerlin.plusHours(1).getHourOfDay(), 2);
    }

    @Test
    public void testFailureForLiteralInGaps()
    {
        // DateTime constructor behavior for gaps is not documented in joda.
        // Nevertheless, the implemented behavior is throw.

        try {
            // gap in Los_Angeles: 2013-03-10 2:00:00 to 2:59:59 doesn't exist
            new DateTime(2013, 3, 10, 2, 30, DateTimeZone.forID("America/Los_Angeles"));
            fail("Expect IllegalInstantException");
        }
        catch (IllegalInstantException e) {
            // do nothing
        }

        try {
            // gap in Berlin: 2013-3-31 2:00:00 to 2:59:59 doesn't exist
            new DateTime(2013, 3, 31, 2, 30, DateTimeZone.forID("Europe/Berlin"));
            fail("Expect IllegalInstantException");
        }
        catch (IllegalInstantException e) {
            // do nothing
        }
    }

    @Test
    public void testPrevNextTransition()
    {
        // This method tests previous/nextTransition directly because it's otherwise hard to catch off-by-1 errors.

        // The exact behavior is not documented, but we must match behavior of joda implementation here.
        // Otherwise testGetOffsetFromLocal fails.

        // In this test:
        // * Use java.time to calculate all the function inputs and expect results.
        // * Use ofStrict to explicitly choose the earlier of latter one whenever in overlap.

        DateTimeZone zoneLosAngeles = DateTimeZone.forID("America/Los_Angeles");
        DateTimeZone zoneBerlin = DateTimeZone.forID("Europe/Berlin");

        // gap in Los_Angeles: 2013-03-10 2:00:00 to 2:59:59 doesn't exist
        assertEquals(
                zoneLosAngeles.previousTransition(ZonedDateTime.of(2013, 3, 10, 1, 59, 59, 999_000_000, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2012, 11, 4, 1, 59, 59, 999_000_000), ZoneOffset.ofHours(-7), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.previousTransition(ZonedDateTime.of(2013, 3, 10, 3, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 10, 1, 59, 59, 999_000_000, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.nextTransition(ZonedDateTime.of(2013, 3, 10, 1, 59, 59, 999_000_000, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 10, 3, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.nextTransition(ZonedDateTime.of(2013, 3, 10, 3, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 0, 0, 0), ZoneOffset.ofHours(-8), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());

        // overlap in Los_Angeles: 2013-11-3 1:00:00 to 1:59:59 repeats
        assertEquals(
                zoneLosAngeles.previousTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 59, 59, 999_000_000), ZoneOffset.ofHours(-7), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 10, 1, 59, 59, 999_000_000, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.previousTransition(ZonedDateTime.of(2013, 11, 3, 2, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 59, 59, 999_000_000), ZoneOffset.ofHours(-7), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.nextTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 59, 59, 999_000_000), ZoneOffset.ofHours(-7), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 0, 0, 0), ZoneOffset.ofHours(-8), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());
        assertEquals(
                zoneLosAngeles.nextTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 11, 3, 1, 0, 0, 0), ZoneOffset.ofHours(-8), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()),
                ZonedDateTime.of(java.time.LocalDateTime.of(2014, 3, 9, 3, 0, 0, 0), ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli());

        // gap in Berlin: 2013-3-31 2:00:00 to 2:59:59 doesn't exist
        assertEquals(
                zoneBerlin.previousTransition(ZonedDateTime.of(2013, 3, 31, 1, 59, 59, 999_000_000, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2012, 10, 28, 2, 59, 59, 999_000_000), ZoneOffset.ofHours(2), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.previousTransition(ZonedDateTime.of(2013, 3, 31, 3, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 31, 1, 59, 59, 999_000_000, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.nextTransition(ZonedDateTime.of(2013, 3, 31, 1, 59, 59, 999_000_000, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 31, 3, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.nextTransition(ZonedDateTime.of(2013, 3, 31, 3, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 0, 0, 0), ZoneOffset.ofHours(1), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());

        // overlap in Berlin: 2013-10-27 2:00:00 to 2:59:59 repeats
        assertEquals(
                zoneBerlin.previousTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 59, 59, 999_000_000), ZoneOffset.ofHours(2), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2013, 3, 31, 1, 59, 59, 999_000_000, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.previousTransition(ZonedDateTime.of(2013, 10, 27, 3, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 59, 59, 999_000_000), ZoneOffset.ofHours(2), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.nextTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 59, 59, 999_000_000), ZoneOffset.ofHours(2), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 0, 0, 0), ZoneOffset.ofHours(1), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
        assertEquals(
                zoneBerlin.nextTransition(ZonedDateTime.ofStrict(java.time.LocalDateTime.of(2013, 10, 27, 2, 0, 0, 0), ZoneOffset.ofHours(1), ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()),
                ZonedDateTime.of(2014, 3, 30, 3, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli());
    }

    @Test
    public void testGetOffsetFromLocal()
    {
        // getOffsetFromLocal is documented to return
        // * the "summer" offset for overlaps
        // * the "winter" offset for gaps

        DateTimeZone zoneLosAngeles = DateTimeZone.forID("America/Los_Angeles");
        // gap in Los_Angeles: 2013-03-10 2:00:00 to 2:59:59 doesn't exist
        assertEquals(zoneLosAngeles.getOffsetFromLocal(new LocalDateTime(2013, 3, 10, 2, 30).toDateTime(DateTimeZone.UTC).toInstant().getMillis()), -8 * 3_600_000);
        // overlap in Los_Angeles: 2013-11-3 1:00:00 to 1:59:59 repeats
        assertEquals(zoneLosAngeles.getOffsetFromLocal(new LocalDateTime(2013, 11, 3, 1, 30).toDateTime(DateTimeZone.UTC).toInstant().getMillis()), -7 * 3_600_000);

        DateTimeZone zoneBerlin = DateTimeZone.forID("Europe/Berlin");
        // gap in Berlin: 2013-3-31 2:00:00 to 2:59:59 doesn't exist
        assertEquals(zoneBerlin.getOffsetFromLocal(new LocalDateTime(2013, 3, 31, 2, 30).toDateTime(DateTimeZone.UTC).toInstant().getMillis()), 3_600_000);
        // overlap in Berlin: 2013-10-27 2:00:00 to 2:59:59 repeats
        assertEquals(zoneBerlin.getOffsetFromLocal(new LocalDateTime(2013, 10, 27, 2, 30).toDateTime(DateTimeZone.UTC).toInstant().getMillis()), 2 * 3_600_000);
    }

    @Test
    public void testInstantiationOfAllZones()
    {
        DateTimeZone.forID("-13:00");

        for (String zoneId : ZoneId.getAvailableZoneIds()) {
            if (zoneId.startsWith("Etc/") || zoneId.startsWith("GMT") || zoneId.startsWith("SystemV/")) {
                continue;
            }

            if (zoneId.equals("Canada/East-Saskatchewan")) {
                // Removed from tzdata since 2017c.
                // Java updated to 2017c since 8u161, 9.0.4.
                // All Java 10+ are on later versions
                continue;
            }

            DateTimeZone.forID(zoneId);
        }

        for (int offsetHours = -13; offsetHours < 14; offsetHours++) {
            for (int offsetMinutes = 0; offsetMinutes < 60; offsetMinutes++) {
                DateTimeZone.forOffsetHoursMinutes(offsetHours, offsetMinutes);
            }
        }
    }
}
