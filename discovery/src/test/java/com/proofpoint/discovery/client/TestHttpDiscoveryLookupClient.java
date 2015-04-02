/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.http.client.testing.TestingHttpClient.Processor;
import com.proofpoint.http.client.testing.TestingResponse;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.HttpHeaders.ETAG;
import static com.proofpoint.discovery.client.ServiceState.RUNNING;
import static com.proofpoint.discovery.client.ServiceState.STOPPED;
import static com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient.DEFAULT_DELAY;
import static com.proofpoint.http.client.HttpStatus.NOT_MODIFIED;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestHttpDiscoveryLookupClient
{

    public static final UUID UUID_ID_1 = UUID.randomUUID();
    public static final UUID UUID_ID_2 = UUID.randomUUID();
    public static final UUID UUID_NODEID_1 = UUID.randomUUID();
    public static final UUID UUID_NODEID_2 = UUID.randomUUID();
    public static final ImmutableMap<String, String> TEST_PROPERTIES_1 = ImmutableMap.of(
            "property1", "value1",
            "property2", "value2"
    );
    public static final ImmutableMap<String, String> TEST_PROPERTIES_2 = ImmutableMap.of(
            "property3", "value3",
            "property4", "value4"
    );
    public static final ImmutableList<ServiceDescriptor> EXPECTED_SERVICE_DESCRIPTOR_LIST = ImmutableList.of(
            new ServiceDescriptor(UUID_ID_1, UUID_NODEID_1.toString(), "testService", "testPool", "testLocation", RUNNING, TEST_PROPERTIES_1),
            new ServiceDescriptor(UUID_ID_2, UUID_NODEID_2.toString(), "testService", "testPool", null, STOPPED, TEST_PROPERTIES_2)
    );

    private NodeInfo nodeInfo;

    @Test
    public void testGetServicesPool()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(createProcessor(null, null, "testingenvironment"))
                .getServices("testService", "testPool")
                .get();
        assertDescriptorsExpected(descriptors, null, DEFAULT_DELAY);
    }

    @Test
    public void testReturnedEtag()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(createProcessor(ImmutableListMultimap.of("ETag", "testEtagValue"), null, "testingenvironment"))
                .getServices("testService", "testPool")
                .get();
        assertDescriptorsExpected(descriptors, "testEtagValue", DEFAULT_DELAY);
    }

    @Test
    public void testReturnedMaxAge()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(createProcessor(ImmutableListMultimap.of("Cache-Control", "max-age=671"), null, "testingenvironment"))
                .getServices("testService", "testPool")
                .get();
        assertDescriptorsExpected(descriptors, null, new Duration(671, SECONDS));
    }

    @Test
    public void testRefreshServices()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(createProcessor(null, null, "testingenvironment"))
                .refreshServices(new ServiceDescriptors("testService", "testPool", ImmutableList.<ServiceDescriptor>of(), new Duration(1, HOURS), null))
                .get();
        assertDescriptorsExpected(descriptors, null, DEFAULT_DELAY);
    }

    @Test
    public void testRefreshServicesEtag()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(createProcessor(null, "oldEtag", "testingenvironment"))
                .refreshServices(new ServiceDescriptors("testService", "testPool", ImmutableList.<ServiceDescriptor>of(), new Duration(1, HOURS), "oldEtag"))
                .get();
        assertDescriptorsExpected(descriptors, null, DEFAULT_DELAY);
    }

    @Test
    public void testRefreshServicesNotModified()
            throws Exception
    {
        ServiceDescriptors descriptors = createClient(
                new Processor()
                {
                    @Override
                    public Response handle(Request request)
                            throws Exception
                    {
                        return new TestingResponse(NOT_MODIFIED, ImmutableListMultimap.of(
                                "ETag", "newEtag",
                                "Cache-Control", "max-age=123"
                        ), new byte[0]);
                    }
                })
                .refreshServices(new ServiceDescriptors("testService", "testPool", EXPECTED_SERVICE_DESCRIPTOR_LIST, new Duration(1, HOURS), "oldEtag"))
                .get();
        assertDescriptorsExpected(descriptors, "newEtag", new Duration(123, SECONDS));
    }

    @Test
    public void testGetServicesNotModified()
            throws Exception
    {
        ListenableFuture<ServiceDescriptors> future = createClient(
                new Processor()
                {
                    @Override
                    public Response handle(Request request)
                            throws Exception
                    {
                        return new TestingResponse(NOT_MODIFIED, ImmutableListMultimap.of(
                                "ETag", "newEtag",
                                "Cache-Control", "max-age=123"
                        ), new byte[0]);
                    }
                })
                .getServices("testService", "testPool");

        try {
            future.get();
            fail("Expected ExecutionException");
        }
        catch (InterruptedException e) {
            throw propagate(e);
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertNull(e.getCause().getCause());
            assertEquals(e.getCause().getMessage(), "Lookup of testService failed with status code 304");
        }
    }

    @Test
    public void testGetServicesInputStreamException()
            throws Exception
    {
        final IOException testingException = new IOException("testing exception");
        ListenableFuture<ServiceDescriptors> future = createClient(
                new Processor()
                {
                    @Override
                    public Response handle(Request request)
                            throws Exception
                    {
                        return new TestingResponse(OK, ImmutableListMultimap.of(
                                "Content-Type", "application/json"
                        ), new InputStream()
                        {
                            @Override
                            public int read()
                                    throws IOException
                            {
                                throw testingException;
                            }
                        });
                    }
                })
                .getServices("testService", "testPool");

        try {
            future.get();
            fail("Expected ExecutionException");
        }
        catch (InterruptedException e) {
            throw propagate(e);
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertSame(e.getCause().getCause(), testingException);
            assertEquals(e.getCause().getMessage(), "Lookup of testService failed");
        }
    }

    @Test
    public void testGetServicesWrongEnvironment()
            throws Exception
    {
        ListenableFuture<ServiceDescriptors> future = createClient(createProcessor(null, null, "wrongenvironment"))
                .getServices("testService", "testPool");

        try {
            future.get();
            fail("Expected ExecutionException");
        }
        catch (InterruptedException e) {
            throw propagate(e);
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertNull(e.getCause().getCause());
            assertEquals(e.getCause().getMessage(), "Expected environment to be testingenvironment, but was wrongenvironment");
        }
    }

    @Test
    public void testGetServicesHttpException()
            throws Exception
    {
        final Exception testingException = new Exception("testing exception");
        ListenableFuture<ServiceDescriptors> future = createClient(
                new Processor()
                {
                    @Override
                    public Response handle(Request request)
                            throws Exception
                    {
                        throw testingException;
                    }
                })
                .getServices("testService", "testPool");

        try {
            future.get();
            fail("Expected ExecutionException");
        }
        catch (InterruptedException e) {
            throw propagate(e);
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertEquals(e.getCause().getCause(), testingException);
            assertEquals(e.getCause().getMessage(), "Lookup of testService failed");
        }
    }

    private static void assertDescriptorsExpected(ServiceDescriptors descriptors, String etag, Duration maxAge)
    {
        assertEquals(descriptors.getETag(), etag);
        assertEquals(descriptors.getMaxAge(), maxAge);
        assertEquals(descriptors.getType(), "testService");
        assertEquals(descriptors.getPool(), "testPool");
        assertEquals(descriptors.getServiceDescriptors(), EXPECTED_SERVICE_DESCRIPTOR_LIST);
    }

    private HttpDiscoveryLookupClient createClient(Processor processor)
    {
        nodeInfo = new NodeInfo("testingenvironment");
        return new HttpDiscoveryLookupClient(
                nodeInfo,
                jsonCodec(ServiceDescriptorsRepresentation.class),
                new TestingHttpClient(processor),
                mock(ServiceInventory.class)
        );
    }

    private Processor createProcessor(final ListMultimap<String, String> headers, final String etag, final String environment)
    {
        return new Processor()
        {
            @Override
            public Response handle(Request request)
                    throws Exception
            {
                Builder requestBuilder = prepareGet()
                        .setUri(URI.create("v1/service/testService/testPool"))
                        .setHeader("User-Agent", nodeInfo.getNodeId());
                if (etag != null) {
                    requestBuilder = requestBuilder.addHeader(ETAG, etag);
                }
                assertEquals(request, requestBuilder
                                .build()
                );
                ListMultimap<String, String> combinedHeaders = ArrayListMultimap.create(TestingResponse.contentType(MediaType.JSON_UTF_8));
                if (headers != null) {
                    combinedHeaders.putAll(headers);
                }
                return new TestingResponse(OK, combinedHeaders, jsonCodec(Object.class).toJson(ImmutableMap.of(
                                "environment", environment,
                                "services", ImmutableList.of(
                                        ImmutableMap.builder()
                                                .put("id", UUID_ID_1.toString())
                                                .put("nodeId", UUID_NODEID_1.toString())
                                                .put("type", "testService")
                                                .put("pool", "testPool")
                                                .put("location", "testLocation")
                                                .put("state", "RUNNING")
                                                .put("properties", TEST_PROPERTIES_1)
                                                .build(),
                                        ImmutableMap.builder()
                                                .put("id", UUID_ID_2.toString())
                                                .put("nodeId", UUID_NODEID_2.toString())
                                                .put("type", "testService")
                                                .put("pool", "testPool")
                                                .put("state", "STOPPED")
                                                .put("properties", TEST_PROPERTIES_2)
                                                .build()
                                )
                        )
                ).getBytes(Charsets.UTF_8));
            }
        };
    }
}
