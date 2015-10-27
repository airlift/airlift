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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.ConfigSecuritySensitive;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestConfigurationResource
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingAdminHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new JmxHttpModule(),
                        new TestingDiscoveryModule(),
                        binder -> {
                            bindConfig(binder).to(TestingConfig.class);
                        }
                )
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "testing.duration", "3m",
                        "testing.password", "password1"
                ))
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
    public void testGetConfiguration()
            throws Exception
    {
        Map<String, Map<String, String>> response = client.execute(
                prepareGet().setUri(uriFor("/admin/configuration")).build(),
                createJsonResponseHandler(jsonCodec(new TypeToken<Map<String, Map<String, String>>>()
                {
                })));

        assertMapContains(response, "testing.duration", "null", "3.00m", null);
        assertMapContains(response, "testing.password", "[REDACTED]", "[REDACTED]", "Testing Description");
        assertNoExtraneousFields(response);
    }

    private static void assertMapContains(
            Map<String, Map<String, String>> response,
            String expectedPropertyName,
            String expectedDefaultValue,
            String expectedCurrentValue,
            @Nullable String expectedDescription)
    {
        Map<String, String> info = response.get(expectedPropertyName);
        assertNotNull(info, expectedPropertyName + "is present in response");

        assertEquals(info.get("defaultValue"), expectedDefaultValue, "default value of " + expectedPropertyName);
        assertEquals(info.get("currentValue"), expectedCurrentValue, "current value of " + expectedPropertyName);
        assertEquals(info.get("description"), expectedDescription, "description of " + expectedPropertyName);
    }

    private static void assertNoExtraneousFields(Map<String, Map<String, String>> response)
    {
        for (Entry<String, Map<String, String>> entry : response.entrySet()) {
            HashSet<String> extraKeys = newHashSet(entry.getValue().keySet());
            extraKeys.removeAll(ImmutableSet.of("defaultValue", "currentValue", "description"));
            assertEquals(extraKeys, ImmutableSet.of(), "keys in map entry " + entry.getKey());
        }
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    private static class TestingConfig
    {
        private Duration duration;
        private String password;

        Duration getDuration()
        {
            return duration;
        }

        @Config("testing.duration")
        void setDuration(Duration duration)
        {
            this.duration = duration;
        }

        String getPassword()
        {
            return password;
        }

        @Config("testing.password")
        @ConfigSecuritySensitive
        @ConfigDescription("Testing Description")
        void setPassword(String password)
        {
            this.password = password;
        }
    }
}
