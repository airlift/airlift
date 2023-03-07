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
import io.airlift.log.RollingFileMessageOutput.CompressionType;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;
import org.assertj.core.util.Files;
import org.testng.annotations.Test;

import java.io.File;
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
                .setMaxTotalSize(new DataSize(1, Unit.GIGABYTE))
                .setCompression(CompressionType.GZIP)
                .setLevelsFile(null)
                .setFormat(Format.TEXT)
                .setLogAnnotationFile(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        File annotationFile = Files.newTemporaryFile();

        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("log.enable-console", "false")
                .put("log.path", "/tmp/log.log")
                .put("log.max-size", "1kB")
                .put("log.max-total-size", "33kB")
                .put("log.compression", "NONE")
                .put("log.levels-file", "/tmp/levels.txt")
                .put("log.format", "json")
                .put("log.annotation-file", annotationFile.getAbsolutePath())
                .build();

        LoggingConfiguration expected = new LoggingConfiguration()
                .setConsoleEnabled(false)
                .setLogPath("/tmp/log.log")
                .setMaxSize(new DataSize(1, KILOBYTE))
                .setMaxTotalSize(new DataSize(33, KILOBYTE))
                .setCompression(CompressionType.NONE)
                .setLevelsFile("/tmp/levels.txt")
                .setFormat(Format.JSON)
                .setLogAnnotationFile(annotationFile.getAbsolutePath());

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
