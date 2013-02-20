/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.rack;

import com.google.common.io.Resources;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.http.client.ApacheHttpClient;
import io.airlift.http.client.HttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static org.testng.Assert.assertEquals;

public class TestRackModuleRaw
{
    private HttpClient client;
    private TestingHttpServer server;
    private LifeCycleManager lifeCycleManager;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new TestingHttpServerModule(),
                new RackModule(),
                new TestingNodeModule(),
                new TestingDiscoveryModule());

        Injector injector = app
                .strictConfig()
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("rackserver.rack-config-path", Resources.getResource("test/raw/config.ru").getFile())
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        client = new ApacheHttpClient();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testBasicGet()
            throws Throwable
    {
        Map<String, Object> response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve("/foo")).build(),
                createJsonResponseHandler(mapJsonCodec(String.class, Object.class)));

        assertEquals(response.get("REQUEST_METHOD"), "GET");
        assertEquals(response.get("SCRIPT_NAME"), "");
        assertEquals(response.get("PATH_INFO"), "/foo");
        assertEquals(response.get("QUERY_STRING"), "");
    }

    @Test
    public void testGetWithEscaping()
            throws Throwable
    {
        String path = "/hello%20world+bye";

        Map<String, Object> response = client.execute(
                prepareGet().setUri(server.getBaseUrl().resolve(path + "?foo%2Fbar=123%20999")).build(),
                createJsonResponseHandler(mapJsonCodec(String.class, Object.class)));

        assertEquals(response.get("REQUEST_METHOD"), "GET");
        assertEquals(response.get("SCRIPT_NAME"), "");
        assertEquals(response.get("PATH_INFO"), "/hello%20world+bye");
        assertEquals(response.get("QUERY_STRING"), "foo%2Fbar=123%20999");
    }
}
