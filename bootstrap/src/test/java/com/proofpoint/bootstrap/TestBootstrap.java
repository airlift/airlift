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
package com.proofpoint.bootstrap;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigurationDefaultingModule;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestBootstrap
{
    @BeforeMethod
    public void setup()
    {
        System.clearProperty("config");
        System.clearProperty("property");
    }

    @Test
    public void testRequiresExplicitBindings()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules()
                .quiet();
        try {
            bootstrap.initialize().getInstance(Instance.class);
            fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Explicit bindings are required");
        }
    }

    @Test
    public void testDisableRequiresExplicitBindings()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules()
                .quiet()
                .requireExplicitBindings(false);
        bootstrap.initialize().getInstance(Instance.class);
    }

    @Test
    public void testDoesNotAllowCircularDependencies()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(InstanceA.class);
                        binder.bind(InstanceB.class);
                    }
                })
                .quiet();

        try {
            bootstrap.initialize().getInstance(InstanceA.class);
            fail("should not allow circular dependencies");
        }
        catch (ProvisionException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "circular proxies are disabled");
        }
    }

    @Test
    public void testConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                });

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
    }

    @Test
    public void testConfigSystemProperties()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("empty-config.properties").getFile());
        System.setProperty("property", "value");
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                });

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
    }

    @Test
    public void testSystemPropertiesOverrideConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("property", "system property value");
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                });

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "system property value");
    }

    @Test
    public void testRequiredConfig()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                })
                .setRequiredConfigurationProperty("property", "required value");

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "required value");
    }

    @Test
    public void testMissingRequiredConfig()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                })
                .setRequiredConfigurationProperty("unknown", "required value");

        try {
            bootstrap.initialize();
            fail("should not allow unknown required configuration properties");
        }
        catch (CreationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Configuration property 'unknown' was not used");
        }
    }

    @Test
    public void testRequiredConfigIgnoresSystemPropertiesAndConfigFile()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        System.setProperty("property", "system property value");
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                })
                .setRequiredConfigurationProperty("other-property", "value");

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertNull(simpleConfig.getProperty());
    }

    @Test
    public void testApplicationDefaults()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                })
                .withApplicationDefaults(ImmutableMap.of(
                        "property", "default value",
                        "other-property", "other default value"
                ));

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
        assertEquals(simpleConfig.getOtherProperty(), "other default value");
    }

    @Test
    public void testModuleDefaults()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("simple-config.properties").getFile());
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                }, new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of(
                                "property", "default value",
                                "other-property", "other default value"
                        );
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }
                });

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "value");
        assertEquals(simpleConfig.getOtherProperty(), "other default value");
    }

    @Test
    public void testConflictingModuleDefaults()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                }, new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }

                    @Override
                    public String toString()
                    {
                        return "first test module";
                    }
                }, new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }

                    @Override
                    public String toString()
                    {
                        return "second test module";
                    }
                })
                .setRequiredConfigurationProperty("other-property", "other value");

        try {
            bootstrap.initialize();
            fail("should not allow duplicate module defaults");
        }
        catch (CreationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Configuration default for \"property\" set by both first test module and second test module");
        }
    }

    @Test
    public void testApplicationDefaultsOverrideBoundDefaults()
            throws Exception
    {
        System.setProperty("config", Resources.getResource("empty-config.properties").getFile());
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        bindConfig(binder).to(SimpleConfig.class);
                    }
                }, new ConfigurationDefaultingModule()
                {
                    @Override
                    public Map<String, String> getConfigurationDefaults()
                    {
                        return ImmutableMap.of("property", "bound default value");
                    }

                    @Override
                    public void configure(Binder binder)
                    {
                    }
                })
                .withApplicationDefaults(ImmutableMap.of(
                        "property", "application default value"
                ));

        SimpleConfig simpleConfig = bootstrap.initialize().getInstance(SimpleConfig.class);
        assertEquals(simpleConfig.getProperty(), "application default value");
    }

    @Test
    public void testConfigAwareModule()
            throws Exception
    {
        final AtomicReference<SimpleConfig> simpleConfig = new AtomicReference<>();
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new AbstractConfigurationAwareModule()
                {
                    @Override
                    public void setup(Binder binder)
                    {
                         simpleConfig.set(buildConfigObject(SimpleConfig.class));
                    }
                })
                .setRequiredConfigurationProperty("property", "required value");

        bootstrap.initialize();
        assertEquals(simpleConfig.get().getProperty(), "required value");
    }

    @Test
    public void testPostConstructCalled()
            throws Exception
    {
        Bootstrap bootstrap = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(LifecycleInstance.class);
                    }
                })
                .quiet()
                .setRequiredConfigurationProperties(ImmutableMap.<String, String>of());

        LifecycleInstance lifecycleInstance = bootstrap.initialize().getInstance(LifecycleInstance.class);
        assertTrue(lifecycleInstance.isInitialized());
    }

    public static class Instance
    {
    }

    public static class InstanceA
    {
        @Inject
        @SuppressWarnings("unused")
        public InstanceA(InstanceB b)
        {
        }
    }

    public static class InstanceB
    {
        @Inject
        @SuppressWarnings("unused")
        public InstanceB(InstanceA a)
        {
        }
    }

    private static class SimpleConfig
    {
        private String property = null;
        private String otherProperty = null;

        public String getProperty()
        {
            return property;
        }

        @Config("property")
        public SimpleConfig setProperty(String property)
        {
            this.property = property;
            return this;
        }

        public String getOtherProperty()
        {
            return otherProperty;
        }

        @Config("other-property")
        public SimpleConfig setOtherProperty(String otherProperty)
        {
            this.otherProperty = otherProperty;
            return this;
        }
    }

    private static class LifecycleInstance
    {
        private final AtomicReference<Boolean> initialized = new AtomicReference<>();

        @PostConstruct
        public void start()
        {
            initialized.set(true);
        }

        public boolean isInitialized()
        {
            return initialized.get();
        }
    }
}
