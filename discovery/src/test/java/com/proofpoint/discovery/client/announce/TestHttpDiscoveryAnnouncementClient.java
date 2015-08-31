/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.announce;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.http.client.testing.TestingHttpClient;
import com.proofpoint.http.client.testing.TestingResponse;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.http.client.HttpStatus.ACCEPTED;
import static com.proofpoint.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.proofpoint.http.client.HttpStatus.NOT_FOUND;
import static com.proofpoint.http.client.testing.TestingResponse.mockResponse;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.testing.Assertions.assertContains;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestHttpDiscoveryAnnouncementClient
{
    private final NodeInfo nodeInfo = new NodeInfo("test");
    private HttpDiscoveryAnnouncementClient client;
    private Set<ServiceAnnouncement> announcements;
    private TestingHttpClient httpClient;

    @BeforeMethod
    public void setup()
    {
        httpClient = new TestingHttpClient();
        client = new HttpDiscoveryAnnouncementClient(nodeInfo, jsonCodec(Announcement.class), httpClient);
        announcements = ImmutableSet.of(
                serviceAnnouncement("foo").addProperty("bar", "baz").build(),
                serviceAnnouncement("quux").addProperty("a", "b").build()
        );
    }

    @Test
    public void testAnnounce()
            throws Exception
    {
        httpClient.setProcessor((request) -> {
            assertEquals(request.getMethod(), "PUT");
            assertEquals(request.getUri(), URI.create("v1/announcement/" + nodeInfo.getNodeId()));
            assertEquals(request.getHeader("User-Agent"), nodeInfo.getNodeId());
            assertEquals(request.getHeader("Content-Type"), "application/json");
            // TODO test request body
            return mockResponse(ACCEPTED);
        });
        Duration duration = client.announce(announcements).get();

        assertEquals(duration, new Duration(10, TimeUnit.SECONDS));
        assertEquals(httpClient.getRequestCount(), 1);
    }

    @Test
    public void testAnnounceCacheControl()
            throws Exception
    {
        httpClient.setProcessor((request -> new TestingResponse(ACCEPTED, ImmutableListMultimap.of("Cache-Control", "max-age=75"), new byte[0])));
        Duration duration = client.announce(announcements).get();

        assertEquals(duration, new Duration(75, TimeUnit.SECONDS));
    }

    @Test
    public void testAnnounceBadStatus()
            throws Exception
    {
        httpClient.setProcessor((request -> mockResponse(NOT_FOUND)));
        try {
            client.announce(announcements).get();
            fail("expected ExecutionException");
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertContains(e.getCause().getMessage(), "Announcement failed with status code 404: ");
        }
    }

    @Test
    public void testAnnounceConnectException()
            throws Exception
    {
        httpClient.setProcessor((request -> {
            throw new ConnectException("test exception");
        }));
        try {
            client.announce(announcements).get();
            fail("expected ExecutionException");
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertEquals(e.getCause().getMessage(), "Announcement failed");
        }
    }

    @Test
    public void testAnnounceZeroAnnouncements()
            throws Exception
    {
        httpClient.setProcessor((request -> {
            assertEquals(request.getMethod(), "DELETE");
            assertEquals(request.getUri(), URI.create("v1/announcement/" + nodeInfo.getNodeId()));
            assertEquals(request.getHeader("User-Agent"), nodeInfo.getNodeId());

            return mockResponse(NOT_FOUND);
        }));
        Duration duration = client.announce(ImmutableSet.<ServiceAnnouncement>of()).get();

        assertEquals(duration, new Duration(10, TimeUnit.SECONDS));
        assertEquals(httpClient.getRequestCount(), 1);
    }


    @Test
    public void testAnnounceZeroAnnouncementsBadStatus()
            throws Exception
    {
        httpClient.setProcessor((request -> mockResponse(INTERNAL_SERVER_ERROR)));
        try {
            client.announce(ImmutableSet.<ServiceAnnouncement>of()).get();
            fail("expected ExecutionException");
        }
        catch (ExecutionException e) {
            assertInstanceOf(e.getCause(), DiscoveryException.class);
            assertContains(e.getCause().getMessage(), "Announcement failed with status code 500: ");
        }
    }

    @Test
    public void testUnannounce()
            throws Exception
    {
        httpClient.setProcessor((request -> {
            assertEquals(request.getMethod(), "DELETE");
            assertEquals(request.getUri(), URI.create("v1/announcement/" + nodeInfo.getNodeId()));
            assertEquals(request.getHeader("User-Agent"), nodeInfo.getNodeId());

            return mockResponse(NOT_FOUND);
        }));
        client.unannounce().get();

        assertEquals(httpClient.getRequestCount(), 1);;
    }
}
