package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationFactoryTest.AnnotatedSetter;
import com.proofpoint.configuration.ConfigurationFactoryTest.LegacyMapConfigPresent;
import com.proofpoint.configuration.ConfigurationFactoryTest.LegacyMapValueConfigPresent;
import com.proofpoint.configuration.ConfigurationInspector.ConfigAttribute;
import com.proofpoint.configuration.ConfigurationInspector.ConfigRecord;
import com.proofpoint.configuration.ConfigurationMetadataTest.SetterSensitiveClass;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfigurationInspector
{
    private static final ConfigurationDefaultingModule TEST_DEFAULTING_MODULE = new ConfigurationDefaultingModule()
    {
        @Override
        public Map<String, String> getConfigurationDefaults()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void configure(Binder binder)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "testing module";
        }
    };

    private static final String PACKAGE_NAME = "com.proofpoint.configuration.";

    private static InspectionVerifier inspect(Map<String, String> properties, Map<String, String> applicationDefaults, Map<String, String> moduleDefaults, Map<String, ConfigurationDefaultingModule> moduleDefaultSource, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(
                properties,
                firstNonNull(applicationDefaults, ImmutableMap.<String, String>of()),
                firstNonNull(moduleDefaults, ImmutableMap.<String, String>of()),
                firstNonNull(moduleDefaultSource, ImmutableMap.<String, ConfigurationDefaultingModule>of()),
                properties.keySet(),
                ImmutableList.<String>of(),
                Problems.NULL_MONITOR
        );
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
        return new InspectionVerifier(new ConfigurationInspector().inspect(configurationFactory));
    }

    @Test
    public void testSimpleConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        inspect(properties, null, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "false", "true", "")
                .value("StringValue", "string-value", "null", "some value", "")
                .end();
    }

    @Test
    public void testSimpleConfigWithModuleDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "boolean-value", TEST_DEFAULTING_MODULE
        );
        inspect(properties, null, moduleDefaults, moduleDefaultSource, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "true", "false", "")
                .value("StringValue", "string-value", "some default value", "some value", "")
                .end();
    }

    @Test
    public void testSimpleConfigWithApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        inspect(properties, applicationDefaults, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "true", "false", "")
                .value("StringValue", "string-value", "some default value", "some value", "")
                .end();
    }
    @Test
    public void testSimpleConfigWithModuleAndApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "string-value", "some value",
                "boolean-value", "false"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "string-value", "some default value",
                "boolean-value", "true"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "string-value", "some module default value",
                "boolean-value", "true"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "string-value", TEST_DEFAULTING_MODULE,
                "boolean-value", TEST_DEFAULTING_MODULE
        );
        inspect(properties, applicationDefaults, moduleDefaults, moduleDefaultSource, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "true", "false", "")
                .value("StringValue", "string-value", "some default value", "some value", "")
                .end();
    }

    @Test
    public void testSecuritySensitiveConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("value", "some value");
        inspect(properties, null, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(SetterSensitiveClass.class);
            }
        })
                .component("ConfigurationMetadataTest$SetterSensitiveClass")
                .value("Value", "value", "[REDACTED]", "[REDACTED]", "description")
                .end();
    }

    @Test
    public void testSecuritySensitiveConfigWithModuleDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "value", "some value"
        );
        Map<String, String> moduleDefaults = ImmutableMap.of(
                "value", "some default value"
        );
        Map<String, ConfigurationDefaultingModule> moduleDefaultSource = ImmutableMap.of(
                "value", TEST_DEFAULTING_MODULE
        );
        inspect(properties, null, moduleDefaults, moduleDefaultSource, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(SetterSensitiveClass.class);
            }
        })
                .component("ConfigurationMetadataTest$SetterSensitiveClass")
                .value("Value", "value", "[REDACTED]", "[REDACTED]", "description")
                .end();
    }

    @Test
    public void testSecuritySensitiveConfigWithApplicationDefaults()
    {
        Map<String, String> properties = ImmutableMap.of(
                "value", "some value"
        );
        Map<String, String> applicationDefaults = ImmutableMap.of(
                "value", "some default value"
        );
        inspect(properties, applicationDefaults, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(SetterSensitiveClass.class);
            }
        })
                .component("ConfigurationMetadataTest$SetterSensitiveClass")
                .value("Value", "value", "[REDACTED]", "[REDACTED]", "description")
                .end();
    }

    @Test
    public void testSimpleMapConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.a", "this is a");
        properties.put("map-a.b", "this is b");
        inspect(properties, null, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(LegacyMapConfigPresent.class);
            }
        })
                .component("ConfigurationFactoryTest$LegacyMapConfigPresent")
                .value("MapA[a]", "map-a.a", "-- n/a --", "this is a", "")
                .value("MapA[b]", "map-a.b", "-- n/a --", "this is b", "")
                .value("MapB", "map-b", "-- n/a --", "-- empty --", "")
                .end();
    }

    @Test
    public void testConfigMapValueConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("map-a.k1.string-value", "this is a");
        properties.put("map-a.k1.string-b", "this is b");
        properties.put("map-a.k2.string-value", "this is k2 a");
        properties.put("map-a.k2.string-b", "this is k2 b");
        inspect(properties, null, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                bindConfig(binder).to(LegacyMapValueConfigPresent.class);
            }
        })
                .component("ConfigurationFactoryTest$LegacyMapValueConfigPresent")
                .value("MapA[k1]StringA", "map-a.k1.string-a", "defaultA", "this is a", "")
                .value("MapA[k1]StringB", "map-a.k1.string-b", "defaultB", "this is b", "")
                .value("MapA[k2]StringA", "map-a.k2.string-a", "defaultA", "this is k2 a", "")
                .value("MapA[k2]StringB", "map-a.k2.string-b", "defaultB", "this is k2 b", "")
                .end();
    }

    @Test
    public void testPrivateBinderConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        inspect(properties, null, null, null, new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                PrivateBinder privateBinder = binder.newPrivateBinder();
                bindConfig(privateBinder).to(AnnotatedSetter.class);
            }
        })
                .component("ConfigurationFactoryTest$AnnotatedSetter")
                .value("BooleanValue", "boolean-value", "false", "true", "")
                .value("StringValue", "string-value", "null", "some value", "")
                .end();
    }

    private static class InspectionVerifier
    {
        private final Iterator<ConfigRecord<?>> recordIterator;
        private Iterator<ConfigAttribute> attributeIterator = null;

        public InspectionVerifier(SortedSet<ConfigRecord<?>> inspect)
        {
            recordIterator = inspect.iterator();
        }

        public InspectionVerifier component(String expectedName)
        {
            if (attributeIterator != null && attributeIterator.hasNext()) {
                fail("Extra attributes: " + Iterators.toString(attributeIterator));
            }
            ConfigRecord<?> record = recordIterator.next();
            assertEquals(record.getComponentName(), PACKAGE_NAME + expectedName);
            attributeIterator = record.getAttributes().iterator();
            return this;
        }

        public InspectionVerifier value(String attributeName, String propertyName, String defaultValue, String currentValue, String description)
        {
            final ConfigAttribute attribute = attributeIterator.next();
            assertEquals(attribute.getAttributeName(), attributeName, "Attribute name");
            assertEquals(attribute.getPropertyName(), propertyName, "Property name");
            assertEquals(attribute.getDefaultValue(), defaultValue, "Default value");
            assertEquals(attribute.getCurrentValue(), currentValue, "Current value");
            assertEquals(attribute.getDescription(), description, "Description");
            return this;
        }

        public void end()
        {
            if (attributeIterator != null && attributeIterator.hasNext()) {
                fail("Extra attributes: " + Iterators.toString(attributeIterator));
            }
            if (recordIterator != null && recordIterator.hasNext()) {
                fail("Extra components: " + Iterators.toString(recordIterator));
            }
        }
    }
}
