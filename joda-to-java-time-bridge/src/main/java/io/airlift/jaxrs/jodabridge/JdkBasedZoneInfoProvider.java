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

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTimeZone;
import org.joda.time.tz.CachedDateTimeZone;
import org.joda.time.tz.Provider;

import java.time.zone.ZoneRulesProvider;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Verify.verify;

public class JdkBasedZoneInfoProvider
        implements Provider
{
    private static final Map<String, DateTimeZone> zones;

    static {
        ImmutableMap.Builder<String, DateTimeZone> zonesBuilder = ImmutableMap.builder();

        for (String zoneId : ZoneRulesProvider.getAvailableZoneIds()) {
            DateTimeZone zone;
            if (zoneId.equals("UTC")) {
                // Joda requires that this particular zone implementation be used for UTC.
                zone = DateTimeZone.UTC;
                verify(zone.isFixed());
            }
            else {
                zone = new JdkBasedDateTimeZone(zoneId);
            }
            if (!zone.isFixed()) {
                // Joda's default Provider implementation, ZoneInfoProvider, caches a zone
                // most of the time if the zone is not fixed. The exception being zones that
                // isn't worthwhile from a performance perspective. The actual check is in
                // `DateTimeZoneBuilder.isCachable` check (a zone would fail if on average
                // it has a transition less than every 25 days).
                // This implementation caches everything for simplicity.
                zone = CachedDateTimeZone.forZone(zone);
            }
            zonesBuilder.put(zoneId, zone);
        }

        zones = zonesBuilder.build();
    }

    @Override
    public DateTimeZone getZone(String id)
    {
        if (id.startsWith("+") || id.startsWith("-")) {
            // DateTimeZone.forID takes care of this case after this method returns null
            return null;
        }
        return zones.get(id);
    }

    @Override
    public Set<String> getAvailableIDs()
    {
        return ZoneRulesProvider.getAvailableZoneIds();
    }

    public static void registerAsJodaZoneInfoProvider()
    {
        // An alternative way of registering this Provider is by calling `DateTimeZone.setProvider`.
        // However, that way, it won't be possible to tell whether any one has used DateTimeZone before this method is invoked.
        // Setting it through the system property allows one to validate that.
        System.setProperty("org.joda.time.DateTimeZone.Provider", JdkBasedZoneInfoProvider.class.getName());
        if (!(DateTimeZone.getProvider() instanceof JdkBasedZoneInfoProvider)) {
            throw new RuntimeException("This method must be invoked before any use of Joda");
        }
    }
}
