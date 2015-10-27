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
package com.proofpoint.jmx;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.ws.rs.WebApplicationException;
import java.net.URI;

import static com.google.inject.util.Modules.override;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestStopAnnouncingResource
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;
    private Announcer announcer;
    private AdminServerCredentialVerifier verifier;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        announcer = mock(Announcer.class);
        verifier = mock(AdminServerCredentialVerifier.class);
        StopAnnouncingResource resource = new StopAnnouncingResource(announcer, verifier);

        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingAdminHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        override(new TestingDiscoveryModule())
                                .with(binder -> binder.bind(Announcer.class).toInstance(announcer)),
                        override(new JmxHttpModule())
                                .with(binder -> {
                                    binder.bind(AdminServerCredentialVerifier.class).toInstance(verifier);
                                    binder.bind(StopAnnouncingResource.class).toInstance(resource);
                                })
                )
                .quiet();

        Injector injector = app
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
    }

    @AfterMethod
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
    public void testStopAnnouncing()
            throws Exception
    {
        StatusResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/admin/stop-announcing"))
                        .addHeader("Authorization", "authHeader")
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        verify(verifier).authenticate("authHeader");
        verifyNoMoreInteractions(verifier);
        verify(announcer).destroy();
        verifyNoMoreInteractions(announcer);

    }

    @Test
    public void testFailAuthentication()
    {
        doThrow(new WebApplicationException(FORBIDDEN.getStatusCode())).when(verifier).authenticate(anyString());

        StatusResponse response = client.execute(
                preparePut()
                        .setUri(uriFor("/admin/stop-announcing"))
                        .addHeader("Authorization", "authHeader")
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), FORBIDDEN.getStatusCode());

        verify(verifier).authenticate("authHeader");
        verifyNoMoreInteractions(verifier);
        verifyNoMoreInteractions(announcer);
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
