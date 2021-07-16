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

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.DataSize;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.units.DataSize.Unit.KILOBYTE;

@SuppressWarnings("deprecation")
public class TestLoggingConfiguration
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(LoggingConfiguration.class)
                .setConsoleEnabled(true)
                .setLogPath(null)
                .setMaxSize(new DataSize(100, DataSize.Unit.MEGABYTE))
                .setMaxSizeInBytes(new DataSize(100, DataSize.Unit.MEGABYTE).toBytes())
                .setMaxHistory(30)
                .setLevelsFile(null)
                .setFormat(Format.TEXT));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("log.enable-console", "false")
                .put("log.path", "/tmp/log.log")
                .put("log.max-size", "1kB")
                .put("log.max-size-in-bytes", "1024")
                .put("log.max-history", "3")
                .put("log.levels-file", "/tmp/levels.txt")
                .put("log.format", "json")
                .build();

        LoggingConfiguration expected = new LoggingConfiguration()
                .setConsoleEnabled(false)
                .setLogPath("/tmp/log.log")
                .setMaxSize(new DataSize(1, KILOBYTE))
                .setMaxSizeInBytes(1024)
                .setMaxHistory(3)
                .setLevelsFile("/tmp/levels.txt")
                .setFormat(Format.JSON);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
