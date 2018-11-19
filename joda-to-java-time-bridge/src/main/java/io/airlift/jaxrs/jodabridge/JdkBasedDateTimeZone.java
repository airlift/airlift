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
package io.airlift.jaxrs.jodabridge;

import org.joda.time.DateTimeZone;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Objects;

public class JdkBasedDateTimeZone
        extends DateTimeZone
{
    private final ZoneRules zoneRules;

    public JdkBasedDateTimeZone(String zoneId)
    {
        super(zoneId);
        this.zoneRules = ZoneId.of(zoneId).getRules();
    }

    @Override
    public String getNameKey(long instant)
    {
        // It is ok to return a dummy value here.
        // getNameKey is used in 6 instances in Joda
        // * `DateTimeZone.getShortName`. Assuming DefaultNameProvider is being used,
        //   it then invokes `DefaultNameProvider.getShortName`, which in turn invokes
        //   `DefaultNameProvider.getNameSet`, which ignores the passed in nameKey,
        //   other than validating that it's not null.
        // * `DateTimeZone.getName`. Same as above.
        // * `CachedDateTimeZone.Info.getNameKey`. It is used to implement getNameKey.
        // * `DateTimeZoneBuilder.PrecalculatedZone.getNameKey`. Same as above.
        // * `DateTimeZoneBuilder.writeTo`. It only uses getNameKey when the object
        //   is an instance of `FixedDateTimeZone`.
        // * `ZoneInfoCompiler.test`. It's only invoked with built-in implementations
        //   of `DateTimeZone`.

        // Use an obviously incorrect and easily searchable value here
        return "presto-name-key-not-provided";
    }

    @Override
    public int getOffset(long instant)
    {
        return zoneRules.getOffset(Instant.ofEpochMilli(instant)).getTotalSeconds() * 1000;
    }

    @Override
    public int getStandardOffset(long instant)
    {
        return zoneRules.getStandardOffset(Instant.ofEpochMilli(instant)).getTotalSeconds() * 1000;
    }

    @Override
    public boolean isFixed()
    {
        return zoneRules.isFixedOffset();
    }

    @Override
    public long nextTransition(long instant)
    {
        ZoneOffsetTransition nextTransition = zoneRules.nextTransition(Instant.ofEpochMilli(instant));
        if (nextTransition == null) {
            // this is after the last transition
            return instant;
        }
        return nextTransition.toEpochSecond() * 1000;
    }

    @Override
    public long previousTransition(long instant)
    {
        // +1 and -1 is necessary here because java.time API expects/returns ......00000 for previousTransition,
        // whereas joda API expects/returns ......99999 for previousTransition.
        // This adjustment is only needed for previousTransition because both expects/returns ......00000 for nextTransitions.
        ZoneOffsetTransition previousTransition = zoneRules.previousTransition(Instant.ofEpochMilli(instant + 1));
        if (previousTransition == null) {
            // this is before the first transition
            return instant;
        }
        return previousTransition.toEpochSecond() * 1000 - 1;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JdkBasedDateTimeZone that = (JdkBasedDateTimeZone) o;
        return Objects.equals(getID(), that.getID());
    }

    @Override
    public int hashCode()
    {
        // Same as super.hashCode. Overridden here explicitly for code readability, and in case of code changes in super.
        return 57 + getID().hashCode();
    }
}
