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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

public class TestReportCollector
{
    @Test
    public void testReportingModule()
    {
        Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new TestingMBeanModule(),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new JsonModule(),
                new ReportingModule(),
                new ReportingClientModule());
    }
}
