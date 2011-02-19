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
package com.proofpoint.http.server;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestHttpServerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpServerConfig.class)
                .setIp(null)
                .setHttpEnabled(true)
                .setHttpPort(8080)
                .setHttpsEnabled(false)
                .setHttpsPort(8443)
                .setKeystorePath(null)
                .setKeystorePassword(null)
                .setLogPath("var/log/http-request.log")
                .setLogRetentionTime((new Duration(90, TimeUnit.DAYS)))
                .setMinThreads(2)
                .setMaxThreads(200)
                .setThreadMaxIdleTime(new Duration(1, TimeUnit.MINUTES))
                .setNetworkMaxIdleTime(new Duration(200, TimeUnit.SECONDS))
                .setUserAuthFile(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.ip", "1.2.3.4")
                .put("http-server.http.enabled", "false")
                .put("http-server.http.port", "1")
                .put("http-server.https.enabled", "true")
                .put("http-server.https.port", "2")
                .put("http-server.https.keystore.path", "/keystore")
                .put("http-server.https.keystore.password", "keystore password")
                .put("http-server.log.path", "/log")
                .put("http-server.log.retention-time", "1d")
                .put("http-server.threads.min", "100")
                .put("http-server.threads.max", "500")
                .put("http-server.threads.max-idle-time", "10m")
                .put("http-server.net.max-idle-time", "20m")
                .put("http-server.auth.users-file", "/auth")
                .build();

        HttpServerConfig expected = new HttpServerConfig()
                .setIp("1.2.3.4")
                .setHttpEnabled(false)
                .setHttpPort(1)
                .setHttpsEnabled(true)
                .setHttpsPort(2)
                .setKeystorePath("/keystore")
                .setKeystorePassword("keystore password")
                .setLogPath("/log")
                .setLogRetentionTime(new Duration(1, TimeUnit.DAYS))
                .setMinThreads(100)
                .setMaxThreads(500)
                .setThreadMaxIdleTime(new Duration(10, TimeUnit.MINUTES))
                .setNetworkMaxIdleTime(new Duration(20, TimeUnit.MINUTES))
                .setUserAuthFile("/auth");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-server.ip", "1.2.3.4")
                .put("http-server.http.enabled", "false")
                .put("http-server.http.port", "1")
                .put("http-server.https.enabled", "true")
                .put("http-server.https.port", "2")
                .put("http-server.https.keystore.path", "/keystore")
                .put("http-server.https.keystore.password", "keystore password")
                .put("http-server.log.path", "/log")
                .put("http-server.log.retention-time", "1d")
                .put("http-server.threads.min", "100")
                .put("http-server.threads.max", "500")
                .put("http-server.threads.max-idle-time", "10m")
                .put("http-server.net.max-idle-time", "20m")
                .put("http-server.auth.users-file", "/auth")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("jetty.ip", "1.2.3.4")
                .put("jetty.http.enabled", "false")
                .put("jetty.http.port", "1")
                .put("jetty.https.enabled", "true")
                .put("jetty.https.port", "2")
                .put("jetty.https.keystore.path", "/keystore")
                .put("jetty.https.keystore.password", "keystore password")
                .put("jetty.log.path", "/log")
                .put("jetty.log.retain-days", "1")
                .put("jetty.threads.min", "100")
                .put("jetty.threads.max", "500")
                .put("jetty.threads.max-idle-time-ms", "600000")
                .put("jetty.net.max-idle-time-ms", "1200000")
                .put("jetty.auth.users-file", "/auth")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(HttpServerConfig.class, currentProperties, oldProperties);
    }
}
