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
package com.proofpoint.jmx;

import com.beust.jcommander.internal.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.testng.annotations.Test;

import java.util.Map;

public class TestJmxModule
{
    @Test
    public void testCanConstruct()
    {
        Map<String, String> properties = Maps.newHashMap();
        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new JmxModule(), new ConfigurationModule(configFactory));
        injector.getInstance(JmxAgent.class);
    }
}
