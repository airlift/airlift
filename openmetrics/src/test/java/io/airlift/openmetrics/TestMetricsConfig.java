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
package io.airlift.openmetrics;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

public class TestMetricsConfig {
    @Test
    public void testDefaults() {
        assertRecordedDefaults(recordDefaults(MetricsConfig.class).setJmxObjectNames(""));
    }

    @Test
    public void testExplicitPropertyMappings() throws Exception {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("openmetrics.jmx-object-names", "foo.bar:name=baz,type=qux|baz.bar:*")
                .build();

        MetricsConfig expected = new MetricsConfig()
                .setJmxObjectNames(
                        ImmutableList.of(new ObjectName("foo.bar:name=baz,type=qux"), new ObjectName("baz.bar:*")));

        assertFullMapping(properties, expected);
    }
}
