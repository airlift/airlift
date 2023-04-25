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

import io.airlift.http.server.RequestStats;
import io.airlift.units.Duration;
import org.testng.annotations.Test;
import org.weakref.jmx.MBeanExporter;

import javax.management.MBeanServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;

public class TestMetricsResource
{
    @Test
    public void testMetrics()
            throws IOException
    {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        MBeanExporter mbeanExporter = new MBeanExporter(mbeanServer);

        RequestStats firstBean = new RequestStats();
        firstBean.record(123_000_000L, 123_000_000L, Duration.valueOf("123s"));
        firstBean.record(543_000_000L, 678_000_000L, Duration.valueOf("456s"));
        mbeanExporter.export("io.airlift.http_client:type=HttpClient,name=ForFirstService", firstBean);

        RequestStats secondBean = new RequestStats();
        secondBean.record(1, 2, Duration.valueOf("3s"));
        mbeanExporter.export("io.airlift.http_client:type=HttpClient,name=ForSecondService", secondBean);

        MetricsConfig metricsConfig = new MetricsConfig();
        MetricsResource resource = new MetricsResource(mbeanServer, mbeanExporter, metricsConfig);

        String actual = resource.getMetrics(List.of());

        String expected = new String(requireNonNull(getClass().getResourceAsStream("/expected-metrics.txt")).readAllBytes());
        assertEquals(actual, expected);
    }
}
