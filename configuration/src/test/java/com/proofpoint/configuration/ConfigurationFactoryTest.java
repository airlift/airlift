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
package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Objects.firstNonNull;

public class ConfigurationFactoryTest
{

    @Test
    public void testAnnotatedGettersThrows()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(AnnotatedGetter.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            Assertions.assertContainsAllOf(e.getMessage(), "not a valid setter", "getStringValue") ;
            Assertions.assertContainsAllOf(e.getMessage(), "not a valid setter", "isBooleanValue") ;
        }
    }

    @Test
    public void testAnnotatedSetters()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        });
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(annotatedSetter);
        Assert.assertEquals(annotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testApplicationDefaults()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "true"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        });
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(annotatedSetter);
        Assert.assertEquals(annotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testPropertiesOverrideApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, applicationDefaults, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        });
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(annotatedSetter);
        Assert.assertEquals(annotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedSetter.isBooleanValue(), false);
    }

    @Test
    public void testConfigurationDespiteLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfigOverridesApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "this is a",
                "string-b", "this is b"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-a", "some default value",
                "string-b", "some other default value"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, applicationDefaults, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("deprecated-string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            monitor.assertMatchingWarningRecorded("deprecated-string-value", "replaced", "Use 'string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "deprecated-string-value");
        }
    }

    @Test
    public void testConfigurationWithConflictingLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is the old value");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a") ;
        }
    }

    @Test
    public void testLegacyApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "string-b", "some other default value"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(),"Application default property", "string-value", "has been replaced", "string-a") ;
        }
    }

    @Test
    public void testConfigurationDespiteDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "defaultA");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-a", "deprecated and should not be used");
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testApplicationDefaultsThroughDeprecatedConfig()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-a", "this is a",
                "string-b", "this is b"
        );
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testDefunctPropertyInConfigThrows()
    {
        Map<String, String> properties = Maps.newTreeMap();
        properties.put("string-value", "this is a");
        properties.put("defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(DefunctConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of defunct config");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot be configured");
        }
    }

    @Test
    public void testDefunctPropertyInApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "this is a",
                "defunct-value", "this shouldn't work"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(DefunctConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of defunct config");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot have an application default");
        }
    }

    @Test
    public void testSuccessfulBeanValidation()
    {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("string-value", "has a value");
        properties.put("int-value", "50");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(BeanValidationClass.class);
            }
        });
        BeanValidationClass beanValidationClass = injector.getInstance(BeanValidationClass.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(beanValidationClass);
        Assert.assertEquals(beanValidationClass.getStringValue(), "has a value");
        Assert.assertEquals(beanValidationClass.getIntValue(), 50);
    }

    @Test
    public void testFailedBeanValidationThrows()
    {
        Map<String, String> properties = ImmutableMap.of(
                // string-value left at invalid default
                "int-value", "5000"  // out of range
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(BeanValidationClass.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to bean validation failure");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Constraint violation", "'int-value'", "must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Constraint violation", "'string-value'", "may not be null", "BeanValidationClass");
        }
    }

    @Test
    public void testMapApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
            "map-a.k3", "this is a"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(MapConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(), "Cannot have application default property", "map-a.k3", "for a configuration map");
        }
    }

    @Test
    public void testMapValueApplicationDefaultsThrows()
    {
        Map<String, String> applicationDefaults = ImmutableMap.of(
            "map-b.k3.string-value", "this is a"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.<String, String>of(), applicationDefaults, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(MapConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of application default for map config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(), "Cannot have application default property", "map-b.k3.string-value", "for a configuration map");
        }
    }

    @Test
    public void testConfigurationThroughLegacyMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-value.k", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyMapConfigPresent.class);
            }
        });
        LegacyMapConfigPresent legacyMapConfigPresent = injector.getInstance(LegacyMapConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
        Assert.assertNotNull(legacyMapConfigPresent);
        Assert.assertEquals(legacyMapConfigPresent.getMapA(), ImmutableMap.<String,String>of("k", "this is a"));
        Assert.assertEquals(legacyMapConfigPresent.getMapB(), ImmutableMap.<String,String>of("k2", "this is b"));
    }

    @Test
    public void testConfigurationWithRedundantLegacyMapConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-value.k", "this is a");
        properties.put("map-a.k3", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyMapConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "map-value", "conflicts with map property prefix", "map-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedMapConfigThrows()
    {
        Map<String, String> properties = ImmutableMap.of(
                "map-value.k", "this is a",
                "deprecated-map-value.k3", "this is a",
                "map-b.k2", "this is b"
        );
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyMapConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("map-value", "replaced", "Use 'map-a'");
            monitor.assertMatchingWarningRecorded("deprecated-map-value", "replaced", "Use 'map-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "map-value", "conflicts with map property prefix", "deprecated-map-value");
        }
    }


    @Test
    public void testConfigurationThroughDeprecatedMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k1", "this is a");
        properties.put("map-b.k2", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedMapConfigPresent.class);
            }
        });
        DeprecatedMapConfigPresent deprecatedMapConfigPresent = injector.getInstance(DeprecatedMapConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a", "deprecated and should not be used");
        Assert.assertNotNull(deprecatedMapConfigPresent);
        Assert.assertEquals(deprecatedMapConfigPresent.getMapA(), ImmutableMap.<String,String>of("k1", "this is a"));
        Assert.assertEquals(deprecatedMapConfigPresent.getMapB(), ImmutableMap.<String,String>of("k2", "this is b"));
    }


    @Test
    public void testConfigurationThroughLegacyMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyMapValueConfigPresent.class);
            }
        });
        LegacyMapValueConfigPresent legacyMapValueConfigPresent = injector.getInstance(LegacyMapValueConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
        Assert.assertNotNull(legacyMapValueConfigPresent);
        Assert.assertEquals(legacyMapValueConfigPresent.getMapA().get("k").getStringA(), "this is a");
        Assert.assertEquals(legacyMapValueConfigPresent.getMapA().get("k").getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyMapValueConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.string-a", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyMapValueConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "map-a.k.string-value", "conflicts with property", "map-a.k.string-a");
        }
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedMapValueConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.deprecated-string-value", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyMapValueConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(2);
            monitor.assertMatchingWarningRecorded("map-a.k.string-value", "replaced", "Use 'map-a.k.string-a'");
            monitor.assertMatchingWarningRecorded("map-a.k.deprecated-string-value", "replaced", "Use 'map-a.k.string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "map-a.k.string-value", "conflicts with property", "map-a.k.deprecated-string-value");
        }
    }

    @Test
    public void testConfigurationThroughDeprecatedMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("map-a.k.string-a", "this is a");
        properties.put("map-a.k.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedMapValueConfigPresent.class);
            }
        });
        DeprecatedMapValueConfigPresent deprecatedMapValueConfigPresent = injector.getInstance(DeprecatedMapValueConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("map-a.k.string-a", "deprecated and should not be used");
        Assert.assertNotNull(deprecatedMapValueConfigPresent);
        Assert.assertEquals(deprecatedMapValueConfigPresent.getMapA().get("k").getStringA(), "this is a");
        Assert.assertEquals(deprecatedMapValueConfigPresent.getMapA().get("k").getStringB(), "this is b");
    }


    @Test
    public void testDefunctPropertyInMapValueConfigThrows()
    {
        Map<String, String> properties = Maps.newTreeMap();
        properties.put("map-a.k.string-value", "this is a");
        properties.put("map-a.k.defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(DefunctMapValueConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of defunct config");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'map-a.k.defunct-value", "cannot be configured");
        }
    }

    @Test
    public void testSuccessfulMapValueBeanValidation()
    {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("map-a.k.string-value", "has a value");
        properties.put("map-a.k.int-value", "50");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, null, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(MapValueBeanValidationClass.class);
            }
        });
        MapValueBeanValidationClass mapValueBeanValidationClass = injector.getInstance(MapValueBeanValidationClass.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(mapValueBeanValidationClass);
        Assert.assertEquals(mapValueBeanValidationClass.getMapA().get("k").getStringValue(), "has a value");
        Assert.assertEquals(mapValueBeanValidationClass.getMapA().get("k").getIntValue(), 50);
    }

    @Test
    public void testFailedMapValueBeanValidation()
    {
        Map<String, String> properties = Maps.newHashMap();
        // string-value left at invalid default
        properties.put("map-a.k.int-value", "5000");  // out of range
        TestMonitor monitor = new TestMonitor();
        try {
            Injector injector = createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(MapValueBeanValidationClass.class);
                }
            });
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Constraint violation", "'map-a.k.int-value'", "must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Constraint violation", "'map-a.k.string-value'", "may not be null", "BeanValidationClass");
        }
    }

    @Test
    public void testConfigurationWithDifferentRepresentationOfSameMapKeyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.01337.string-a", "this is a");
        properties.put("map-a.1337.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(IntegerLegacyMapConfig.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(),"Configuration property prefixes", "'map-a.1337'", "'map-a.01337'", "convert to the same map key", "setMapA");
        }
    }

    @Test
    public void testConfigurationOfSimpleMapValueWithComplexPropertyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.1337.string-a", "this is a");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(IntegerStringMapConfig.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of invalid configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(), "Configuration map has non-configuration value class java.lang.String, so key '1337' cannot be followed by '.'",
                    "property 'map-a.1337.string-a'", "setMapA");
        }
    }

    @Test
    public void testConfigurationWithInvalidMapKeyThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k.string-a", "this is a");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, null, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(IntegerLegacyMapConfig.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to use of invalid configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            Assertions.assertContainsAllOf(e.getMessage(),"Could not coerce map key 'k' to java.lang.Integer", "property prefix 'map-a.k'", "setMapA");
        }
    }

    private Injector createInjector(Map<String, String> properties, Map<String, String> applicationDefaults, TestMonitor monitor, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties, firstNonNull(applicationDefaults, ImmutableMap.<String, String>of()), Collections.<String>emptySet(), ImmutableList.<String>of(), monitor);
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }


    public static class AnnotatedGetter {
        private String stringValue;
        private boolean booleanValue;

        @Config("string-value")
        public String getStringValue()
        {
            return stringValue;
        }

        public void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        @Config("boolean-value")
        public boolean isBooleanValue()
        {
            return booleanValue;
        }

        public void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }
    
    static class AnnotatedSetter {
        private String stringValue;
        private boolean booleanValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        private boolean isBooleanValue()
        {
            return booleanValue;
        }

        @Config("boolean-value")
        private void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }

    private static class LegacyConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        String getStringA()
        {
            return stringA;
        }

        @Config("string-a")
        @LegacyConfig("string-value")
        private void setStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        @Deprecated
        @LegacyConfig(value = "deprecated-string-value", replacedBy = "string-a")
        private void setDeprecatedStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        private String getStringB()
        {
            return stringB;
        }

        @Config("string-b")
        public void setStringB(String stringValue)
        {
            this.stringB = stringValue;
        }
    }

    static class DeprecatedConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        @Deprecated
        String getStringA()
        {
            return stringA;
        }

        @Deprecated
        @Config("string-a")
        void setStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        private String getStringB()
        {
            return stringB;
        }

        @Config("string-b")
        private void setStringB(String stringValue)
        {
            this.stringB = stringValue;
        }
    }

    @DefunctConfig("defunct-value")
    private static class DefunctConfigPresent
    {
        private String stringValue;
        private boolean booleanValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        private void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }
    }

    private static class BeanValidationClass
    {
        @NotNull
        private String stringValue = null;

        private int myIntValue;

        String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        private void setStringValue(String value)
        {
            this.stringValue = value;
        }

        @Min(1)
        @Max(100)
        private int getIntValue()
        {
            return myIntValue;
        }

        @Config("int-value")
        void setIntValue(int value)
        {
            this.myIntValue = value;
        }
    }

    static class MapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, Config1> mapB = new HashMap<>();

        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, Config1> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        @ConfigMap(Config1.class)
        public void setMapB(Map<String, Config1> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    static class LegacyMapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, String> mapB = new HashMap<>();

        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @LegacyConfig("map-value")
        @ConfigMap
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        @Deprecated
        @LegacyConfig(value = "deprecated-map-value", replacedBy = "map-a")
        @ConfigMap
        private void setDeprecatedMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, String> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        @ConfigMap
        public void setMapB(Map<String, String> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class DeprecatedMapConfigPresent
    {
        private Map<String, String> mapA = new HashMap<>();
        private Map<String, String> mapB = new HashMap<>();

        @Deprecated
        Map<String, String> getMapA()
        {
            return mapA;
        }

        @Deprecated
        @Config("map-a")
        @ConfigMap
        void setMapA(Map<String, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }

        private Map<String, String> getMapB()
        {
            return mapB;
        }

        @Config("map-b")
        @ConfigMap
        private void setMapB(Map<String, String> mapValue)
        {
            this.mapB = ImmutableMap.copyOf(mapValue);
        }
    }

    static class LegacyMapValueConfigPresent
    {
        private Map<String, LegacyConfigPresent> mapA = new HashMap<>();

        private Map<String, LegacyConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(LegacyConfigPresent.class)
        void setMapA(Map<String, LegacyConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class DeprecatedMapValueConfigPresent
    {
        private Map<String, DeprecatedConfigPresent> mapA = new HashMap<>();

        Map<String, DeprecatedConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(DeprecatedConfigPresent.class)
        private void setMapA(Map<String, DeprecatedConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    static class DefunctMapValueConfigPresent
    {
        private Map<String, DefunctConfigPresent> mapA = new HashMap<>();

        private Map<String, DefunctConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(DefunctConfigPresent.class)
        void setMapA(Map<String, DefunctConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class MapValueBeanValidationClass
    {
        private Map<String, BeanValidationClass> mapA = new HashMap<>();

        Map<String, BeanValidationClass> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(BeanValidationClass.class)
        private void setMapA(Map<String, BeanValidationClass> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class IntegerLegacyMapConfig
    {
        private Map<Integer, LegacyConfigPresent> mapA = new HashMap<>();

        private Map<Integer, LegacyConfigPresent> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(key = Integer.class, value = LegacyConfigPresent.class)
        private void setMapA(Map<Integer, LegacyConfigPresent> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }

    private static class IntegerStringMapConfig
    {
        private Map<Integer, String> mapA = new HashMap<>();

        Map<Integer, String> getMapA()
        {
            return mapA;
        }

        @Config("map-a")
        @ConfigMap(key = Integer.class)
        void setMapA(Map<Integer, String> mapValue)
        {
            this.mapA = ImmutableMap.copyOf(mapValue);
        }
    }
}
