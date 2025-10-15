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
package io.airlift.configuration.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;
import io.airlift.configuration.testing.ConfigAssertions.$$RecordedConfigData;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class TestConfigAssertions {
    @Test
    public void testDefaults() {
        Map<String, Object> expectedAttributeValues = new HashMap<>();
        expectedAttributeValues.put("Name", "Dain");
        expectedAttributeValues.put("Email", "dain@proofpoint.com");
        expectedAttributeValues.put("Phone", null);
        expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
        ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
    }

    @Test
    public void testDefaultsFailNotDefault() {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", "42");
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("Phone");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailNotDefaultWithNullAttribute() {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://example.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUnsupportedAttribute() {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            expectedAttributeValues.put("UnsupportedAttribute", "value");
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("UnsupportedAttribute");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUntestedAttribute() {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailDeprecatedAttribute() {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePageUrl", URI.create("http://iq80.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("HomePageUrl");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappings() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("email", "jenny@compuserve.com")
                .put("phone", "867-5309")
                .put("home-page", "http://example.com")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setPhone("867-5309")
                .setHomePage(URI.create("http://example.com"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testExplicitPropertyMappingsWithSkipped() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("email", "jenny@compuserve.com")
                .put("home-page", "http://example.com")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setHomePage(URI.create("http://example.com"));

        ConfigAssertions.assertFullMapping(properties, expected, ImmutableSet.of("phone"));
    }

    @Test
    public void testExplicitPropertyMappingsFailUnsupportedProperty() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("email", "jenny@compuserve.com")
                .put("phone", "867-5309")
                .put("home-page", "http://example.com")
                .put("unsupported-property", "value")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setPhone("867-5309")
                .setHomePage(URI.create("http://example.com"));

        boolean pass = true;
        try {
            ConfigAssertions.assertFullMapping(properties, expected);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("unsupported-property");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailUntestedProperty() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("email", "jenny@compuserve.com")
                .put("phone", "867-5309")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setPhone("867-5309");

        boolean pass = true;
        try {
            ConfigAssertions.assertFullMapping(properties, expected);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("home-page");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailHasDefaultProperty() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Dain")
                .put("email", "jenny@compuserve.com")
                .put("phone", "867-5309")
                .put("home-page", "http://example.com")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setHomePage(URI.create("http://example.com"))
                .setPhone("867-5309");

        boolean pass = true;
        try {
            ConfigAssertions.assertFullMapping(properties, expected);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("Name");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailNotEquivalent() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("email", "jenny@compuserve.com")
                .put("phone", "867-5309")
                .put("home-page", "http://example.com")
                .build();

        PersonConfig expected = new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setHomePage(URI.create("http://yahoo.com"))
                .setPhone("867-5309");

        boolean pass = true;
        try {
            ConfigAssertions.assertFullMapping(properties, expected);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testNoDeprecatedProperties() {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .put("home-page", "http://example.com")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(NoDeprecatedConfig.class, currentProperties);
    }

    @Test
    public void testDeprecatedProperties() {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .put("home-page", "http://example.com")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .put("home-page", "http://example.com")
                .build();

        Map<String, String> olderProperties = new ImmutableMap.Builder<String, String>()
                .put("notes-id", "alice@example.com")
                .put("home-page-url", "http://example.com")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(
                PersonConfig.class, currentProperties, oldProperties, olderProperties);
    }

    @Test
    public void testDeprecatedPropertiesFailUnsupportedProperties() {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .put("unsupported-property", "value")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .build();

        Map<String, String> olderProperties = new ImmutableMap.Builder<String, String>()
                .put("notes-id", "alice@example.com")
                .build();

        boolean pass = true;
        try {
            ConfigAssertions.assertDeprecatedEquivalence(
                    PersonConfig.class, currentProperties, oldProperties, olderProperties);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("unsupported-property");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDeprecatedPropertiesFailUntestedProperties() {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .build();

        boolean pass = true;
        try {
            ConfigAssertions.assertDeprecatedEquivalence(PersonConfig.class, currentProperties, oldProperties);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("notes-id");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDeprecatedPropertiesFailDeprecatedCurrentProperties() {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("notes-id", "alice@example.com")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .build();

        Map<String, String> olderProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .build();

        boolean pass = true;
        try {
            ConfigAssertions.assertDeprecatedEquivalence(
                    PersonConfig.class, currentProperties, oldProperties, olderProperties);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("notes-id");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testRecordDefaults() throws Exception {
        PersonConfig config = ConfigAssertions.recordDefaults(PersonConfig.class)
                .setName("Alice Apple")
                .setEmail("alice@example.com")
                .setPhone("1-976-alice")
                .setHomePage(URI.create("http://alice.example.com"));

        $$RecordedConfigData<PersonConfig> data = ConfigAssertions.getRecordedConfig(config);

        PersonConfig instance = data.instance();
        assertThat(instance).isNotSameAs(config);

        assertThat(data.invokedMethods())
                .isEqualTo(ImmutableSet.of(
                        PersonConfig.class.getMethod("setName", String.class),
                        PersonConfig.class.getMethod("setEmail", String.class),
                        PersonConfig.class.getMethod("setPhone", String.class),
                        PersonConfig.class.getMethod("setHomePage", URI.class)));

        assertThat(instance.getName()).isEqualTo("Alice Apple");
        assertThat(instance.getEmail()).isEqualTo("alice@example.com");
        assertThat(instance.getPhone()).isEqualTo("1-976-alice");
        assertThat(instance.getHomePage()).isEqualTo(URI.create("http://alice.example.com"));

        assertThat(config.getName()).isEqualTo("Alice Apple");
        assertThat(config.getEmail()).isEqualTo("alice@example.com");
        assertThat(config.getPhone()).isEqualTo("1-976-alice");
        assertThat(config.getHomePage()).isEqualTo(URI.create("http://alice.example.com"));
    }

    @Test
    public void testRecordedDefaults() {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(PersonConfig.class)
                .setName("Dain")
                .setEmail("dain@proofpoint.com")
                .setPhone(null)
                .setHomePage(URI.create("http://iq80.com")));
    }

    @Test
    public void testRecordedDefaultsOneOfEverything() {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(Config1.class)
                .setBooleanOption(false)
                .setBoxedBooleanOption(null)
                .setBoxedByteOption(null)
                .setBoxedDoubleOption(null)
                .setBoxedFloatOption(null)
                .setBoxedIntegerOption(null)
                .setBoxedLongOption(null)
                .setBoxedShortOption(null)
                .setByteOption((byte) 0)
                .setDoubleOption(0.0)
                .setFloatOption(0.0f)
                .setIntegerOption(0)
                .setLongOption(0)
                .setMyEnumOption(null)
                .setMyEnumSecondOption(null)
                .setPathOption(null)
                .setMyEnumList(ImmutableList.of())
                .setMyEnumSet(ImmutableSet.of())
                .setMyIntegerList(ImmutableList.of())
                .setMyPathList(ImmutableList.of())
                .setShortOption((short) 0)
                .setStringOption(null)
                .setValueClassOption(null)
                .setOptionalValueClassOption(Optional.empty())
                .setOptionalPathOption(Optional.empty()));
    }

    @Test
    public void testRecordedDefaultsFailInvokedDeprecatedSetter() throws MalformedURLException {
        boolean pass = true;
        try {
            ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(PersonConfig.class)
                    .setName("Dain")
                    .setEmail("dain@proofpoint.com")
                    .setPhone(null)
                    .setHomePageUrl(URI.create("http://iq80.com").toURL()));
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("HomePageUrl");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testRecordedDefaultsFailInvokedExtraMethod() {
        boolean pass = true;
        try {
            PersonConfig config = ConfigAssertions.recordDefaults(PersonConfig.class)
                    .setName("Dain")
                    .setEmail("dain@proofpoint.com")
                    .setPhone(null)
                    .setHomePage(URI.create("http://iq80.com"));

            // extra non setter method invoked
            config.hashCode();

            ConfigAssertions.assertRecordedDefaults(config);
        } catch (AssertionError e) {
            // expected
            pass = false;
            assertThat(e.getMessage()).contains("hashCode()");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testArrayDefaults() {
        Map<String, Object> expectedAttributeValues = new HashMap<>();
        expectedAttributeValues.put("Fields", null);
        ConfigAssertions.assertDefaults(expectedAttributeValues, PersonFieldsConfig.class);
    }

    @Test
    public void testExplicitArrayPropertyMappings() {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("fields", "name,email,phone,homePage")
                .build();

        PersonFieldsConfig expected = new PersonFieldsConfig().setFields("name,email,phone,homePage");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    public static class PersonFieldsConfig {
        private String[] fields;

        public String[] getFields() {
            return fields;
        }

        @Config("fields")
        public PersonFieldsConfig setFields(String fields) {
            if (Objects.nonNull(fields)) {
                this.fields = fields.split(",");
            }
            return this;
        }
    }

    public static class PersonConfig {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;
        private URI homePage = URI.create("http://iq80.com");

        public String getName() {
            return name;
        }

        @Config("name")
        public PersonConfig setName(String name) {
            this.name = name;
            return this;
        }

        public String getEmail() {
            return email;
        }

        @Config("email")
        @LegacyConfig({"exchange-id", "notes-id"})
        public PersonConfig setEmail(String email) {
            this.email = email;
            return this;
        }

        public String getPhone() {
            return phone;
        }

        @Config("phone")
        public PersonConfig setPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public URI getHomePage() {
            return homePage;
        }

        @Config("home-page")
        public PersonConfig setHomePage(URI homePage) {
            this.homePage = homePage;
            return this;
        }

        @LegacyConfig(value = "home-page-url", replacedBy = "home-page")
        public PersonConfig setHomePageUrl(URL homePage) {
            try {
                this.homePage = homePage.toURI();
                return this;
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public static class NoDeprecatedConfig {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;
        private URI homePage = URI.create("http://iq80.com");

        public String getName() {
            return name;
        }

        @Config("name")
        public NoDeprecatedConfig setName(String name) {
            this.name = name;
            return this;
        }

        public String getEmail() {
            return email;
        }

        @Config("email")
        public NoDeprecatedConfig setEmail(String email) {
            this.email = email;
            return this;
        }

        public String getPhone() {
            return phone;
        }

        @Config("phone")
        public NoDeprecatedConfig setPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public URI getHomePage() {
            return homePage;
        }

        @Config("home-page")
        public NoDeprecatedConfig setHomePage(URI homePage) {
            this.homePage = homePage;
            return this;
        }
    }
}
