/*
 * Copyright 2013 Proofpoint, Inc.
 *
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
package com.proofpoint.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.json.ObjectMapperProvider;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static com.google.common.base.Throwables.propagate;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestReportClient
{
    private static final int TEST_TIME = 1234567890;
    private NodeInfo nodeInfo;
    private Table<ObjectName, String, Number> collectedData;
    private HttpClient httpClient;
    private List<Map<String, Object>> sentJson;
    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    @BeforeMethod
    public void setup()
            throws MalformedObjectNameException
    {
        nodeInfo = new NodeInfo("test-application", new NodeConfig()
                .setEnvironment("test_environment")
                .setNodeInternalHostname("test.hostname")
                .setPool("test_pool")
        );

        collectedData = HashBasedTable.create();
        collectedData.put(ObjectName.getInstance("com.example:name=Foo"), "Size", 1.1);
        collectedData.put(ObjectName.getInstance("com.example:type=Foo,name=\"ba:r\",tag1=\"B\\\\a\\\"z\""), "Size", 1.2);

        httpClient = new TestingHttpClient(new TestingResponseFunction());
        sentJson = null;
    }

    @Test
    public void testReportingDisabled()
    {
        httpClient = new TestingHttpClient(new Function<Request, Response>()
        {
            @Override
            public Response apply(Request input)
            {
                throw new UnsupportedOperationException();
            }
        });
        ReportClient client = new ReportClient(nodeInfo, httpClient, new ReportClientConfig().setEnabled(false), objectMapper);
        client.report(System.currentTimeMillis(), collectedData);
    }

    @Test
    public void testReportData()
    {

        ReportClient client = new ReportClient(nodeInfo, httpClient, new ReportClientConfig(), objectMapper);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentJson.size(), 2);

        for (Map<String, Object> map : sentJson) {
            assertEquals(map.keySet(), ImmutableSet.of("name", "timestamp", "value", "tags"));
            assertEquals(map.get("timestamp"), TEST_TIME);
            Map<String, String> tags = (Map<String, String>) map.get("tags");
            assertEquals(tags.get("application"), "test-application");
            assertEquals(tags.get("host"), "test.hostname");
            assertEquals(tags.get("environment"), "test_environment");
            assertEquals(tags.get("pool"), "test_pool");
        }
        assertEquals(sentJson.get(0).get("name"), "Foo.Ba_r.Size");
        assertEquals(sentJson.get(1).get("name"), "Foo.Size");
        assertEquals(sentJson.get(0).get("value"), 1.2);
        assertEquals(sentJson.get(1).get("value"), 1.1);
        Map<String, String> tags = (Map<String, String>) sentJson.get(0).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "tag1"));
        assertEquals(tags.get("tag1"), "B_a_z"); // "B\\a\"z");
        tags = (Map<String, String>) sentJson.get(1).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool"));
    }

    @Test
    public void testConfiguredTags()
    {

        ReportClient client = new ReportClient(nodeInfo, httpClient,
                new ReportClientConfig()
                        .setTags(ImmutableMap.of("foo", "ba:r", "baz", "quux")), objectMapper);
        client.report(TEST_TIME, collectedData);
        assertEquals(sentJson.size(), 2);

        for (Map<String, Object> map : sentJson) {
            assertEquals(map.keySet(), ImmutableSet.of("name", "timestamp", "value", "tags"));
            Map<String, String> tags = (Map<String, String>) map.get("tags");
            assertEquals(tags.get("foo"), "ba:r");
            assertEquals(tags.get("baz"), "quux");
        }
        Map<String, String> tags = (Map<String, String>) sentJson.get(0).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "foo", "baz", "tag1"));
        tags = (Map<String, String>) sentJson.get(1).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "foo", "baz"));
    }

    private class TestingResponseFunction
            implements Function<Request, Response>
    {
        @Override
        public Response apply(Request input)
        {
            assertNull(sentJson);
            assertEquals(input.getMethod(), "POST");
            assertEquals(input.getUri().toString(), "api/v1/datapoints");
            assertEquals(input.getHeader("Content-Type"), "application/gzip");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                input.getBodyGenerator().write(outputStream);
                GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(outputStream.toByteArray()));

                sentJson = new ObjectMapper().readValue(inputStream, new TypeReference<List<Map<String, Object>>>()
                {
                });
                sentJson = Lists.newArrayList(sentJson);
                Collections.sort(sentJson, new Comparator<Map<String, Object>>()
                {
                    @Override
                    public int compare(Map<String, Object> o1, Map<String, Object> o2)
                    {
                        return ((String) o1.get("name")).compareTo((String) o2.get("name"));
                    }
                });
            }
            catch (Exception e) {
                throw propagate(e);
            }

            return new Response()
            {
                @Override
                public int getStatusCode()
                {
                    return 204;
                }

                @Override
                public String getStatusMessage()
                {
                    return "No content";
                }

                @Override
                public String getHeader(String name)
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ListMultimap<String, String> getHeaders()
                {
                    return ImmutableListMultimap.of();
                }

                @Override
                public long getBytesRead()
                {
                    return 100;
                }

                @Override
                public InputStream getInputStream()
                        throws IOException
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
