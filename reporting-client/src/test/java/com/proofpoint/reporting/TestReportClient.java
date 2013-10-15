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
import com.google.common.collect.Table;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestReportClient
{
    private static final String TEST_URI = "http://example.com:8080";
    private static final int TEST_TIME = 1234567890;
    private NodeInfo nodeInfo;
    private Table<ObjectName, String, Number> collectedData;
    private HttpClient httpClient;
    private List<Map<String, Object>> sentJson;

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
        collectedData.put(ObjectName.getInstance("com.example:type=Foo,name=\"B\\\\a\\\"r\""), "Size", 1.2);

        httpClient = new TestingHttpClient(new TestingResponseFunction());
        sentJson = null;
    }

    @Test
    public void testNullReportUri()
    {
        httpClient = new TestingHttpClient(new Function<Request, Response>()
        {
            @Override
            public Response apply(Request input)
            {
                throw new UnsupportedOperationException();
            }
        });
        ReportClient client = new ReportClient(nodeInfo, httpClient, new ReportClientConfig().setUri(null));
        client.report(System.currentTimeMillis(), collectedData);
    }

    @Test
    public void testReportData()
    {

        ReportClient client = new ReportClient(nodeInfo, httpClient, new ReportClientConfig().setUri(URI.create(TEST_URI)));
        client.report(TEST_TIME, collectedData);
        assertEquals(sentJson.size(), 2);

        for (Map<String, Object> map : sentJson) {
            assertEquals(map.keySet(), ImmutableSet.of("name", "timestamp", "value", "tags"));
            assertEquals(map.get("name"), "Size");
            assertEquals(map.get("timestamp"), TEST_TIME);
            Map<String, String> tags = (Map<String, String>) map.get("tags");
            assertEquals(tags.get("application"), "test-application");
            assertEquals(tags.get("host"), "test.hostname");
            assertEquals(tags.get("environment"), "test_environment");
            assertEquals(tags.get("pool"), "test_pool");
        }
        assertEquals(sentJson.get(0).get("value"), 1.2);
        assertEquals(sentJson.get(1).get("value"), 1.1);
        Map<String, String> tags = (Map<String, String>) sentJson.get(0).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "package", "type", "name"));
        assertEquals(tags.get("package"), "com.example");
        assertEquals(tags.get("type"), "Foo");
        assertEquals(tags.get("name"), "B\\a\"r");
        tags = (Map<String, String>) sentJson.get(1).get("tags");
        assertEquals(tags.get("package"), "com.example");
        assertEquals(tags.get("name"), "Foo");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "package", "name"));
    }

    @Test
    public void testConfiguredTags()
    {

        ReportClient client = new ReportClient(nodeInfo, httpClient,
                new ReportClientConfig()
                        .setUri(URI.create(TEST_URI))
                        .setTags(ImmutableMap.of("foo", "bar", "baz", "quux")));
        client.report(TEST_TIME, collectedData);
        assertEquals(sentJson.size(), 2);

        for (Map<String, Object> map : sentJson) {
            assertEquals(map.keySet(), ImmutableSet.of("name", "timestamp", "value", "tags"));
            Map<String, String> tags = (Map<String, String>) map.get("tags");
            assertEquals(tags.get("package"), "com.example");
            assertEquals(tags.get("foo"), "bar");
            assertEquals(tags.get("baz"), "quux");
        }
        Map<String, String> tags = (Map<String, String>) sentJson.get(0).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "foo", "baz", "package", "type", "name"));
        tags = (Map<String, String>) sentJson.get(1).get("tags");
        assertEquals(tags.keySet(), ImmutableSet.of("application", "host", "environment", "pool", "foo", "baz", "package", "name"));
    }

    private class TestingResponseFunction
            implements Function<Request, Response>
    {
        @Override
        public Response apply(Request input)
        {
            assertNull(sentJson);
            assertEquals(input.getMethod(), "POST");
            assertEquals(input.getUri().toString(), TEST_URI + "/api/v1/datapoints");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                input.getBodyGenerator().write(outputStream);

                String json = new String(outputStream.toByteArray(), "UTF-8");
                sentJson = new ObjectMapper().readValue(json, new TypeReference<List<Map<String, Object>>>()
                {
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
