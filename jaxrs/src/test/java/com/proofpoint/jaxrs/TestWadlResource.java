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
package com.proofpoint.jaxrs;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.servlet.Servlet;
import java.net.URI;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.http.server.testing.TestingAdminHttpServerModule.initializesMainServletTestingAdminHttpServerModule;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;

public class TestWadlResource
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {

        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        initializesMainServletTestingAdminHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new TestingDiscoveryModule(),
                        binder -> jaxrsBinder(binder).bind(TestingResource.class)
                )
                .quiet()
                .initialize();

        injector.getInstance(Key.get(Servlet.class, TheServlet.class));
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testGetWadl()
            throws Exception
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/admin/wadl")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), 200);
        assertContains(response.getBody(), "<resource path=\"/\">");
        assertContains(response.getBody(), "<method id=\"put\" name=\"PUT\"/>");
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
