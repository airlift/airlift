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
package com.proofpoint.configuration.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.Config1;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.configuration.testing.ConfigAssertions.$$RecordedConfigData;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.fail;

public class TestConfigAssertions
{
    @Test
    public void testDefaults()
    {
        Map<String, Object> expectedAttributeValues = new HashMap<>();
        expectedAttributeValues.put("Name", "Dain");
        expectedAttributeValues.put("Email", "dain@proofpoint.com");
        expectedAttributeValues.put("Phone", null);
        expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
        assertDefaults(expectedAttributeValues, PersonConfig.class);
    }

    @Test
    public void testDefaultsFailNotDefault()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", "42");
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "Phone");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailNotDefaultWithNullAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://example.com"));
            assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUnsupportedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            expectedAttributeValues.put("UnsupportedAttribute", "value");
            assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "UnsupportedAttribute");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUntestedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailDeprecatedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePageUrl", URI.create("http://iq80.com"));
            assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "HomePageUrl");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappings()
    {
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

        assertFullMapping(properties, expected);
    }

    @Test
    public void testExplicitPropertyMappingsFailUnsupportedProperty()
    {
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
            assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "unsupported-property");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailUntestedProperty()
    {
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
            assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "home-page");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailHasDefaultProperty()
    {
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
            assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "Name");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsFailNotEquivalent()
    {
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
            assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testNoLegacyProperties()
    {
        assertLegacyEquivalence(NoLegacyConfig.class, ImmutableMap.<String, String>of());
    }

    @Test
    public void testLegacyProperties()
    {
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

        assertLegacyEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
    }

    @Test
    public void testLegacyPropertiesFailUnsupportedProperties()
    {
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
            assertLegacyEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "unsupported-property");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testLegacyPropertiesFailUntestedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .build();

        boolean pass = true;
        try {
            assertLegacyEquivalence(PersonConfig.class, currentProperties, oldProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "notes-id");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testLegacyPropertiesFailLegacyCurrentProperties()
    {
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
            assertLegacyEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "notes-id");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testRecordDefaults()
            throws Exception
    {
        PersonConfig config = ConfigAssertions.recordDefaults(PersonConfig.class)
                .setName("Alice Apple")
                .setEmail("alice@example.com")
                .setPhone("1-976-alice")
                .setHomePage(URI.create("http://alice.example.com"));

        $$RecordedConfigData<PersonConfig> data = ConfigAssertions.getRecordedConfig(config);

        PersonConfig instance = data.getInstance();
        assertNotSame(instance, config);

        assertEquals(data.getInvokedMethods(), ImmutableSet.of(
                PersonConfig.class.getDeclaredMethod("setName", String.class),
                PersonConfig.class.getDeclaredMethod("setEmail", String.class),
                PersonConfig.class.getDeclaredMethod("setPhone", String.class),
                PersonConfig.class.getDeclaredMethod("setHomePage", URI.class)));

        assertEquals(instance.getName(), "Alice Apple");
        assertEquals(instance.getEmail(), "alice@example.com");
        assertEquals(instance.getPhone(), "1-976-alice");
        assertEquals(instance.getHomePage(), URI.create("http://alice.example.com"));

        assertEquals(config.getName(), "Alice Apple");
        assertEquals(config.getEmail(), "alice@example.com");
        assertEquals(config.getPhone(), "1-976-alice");
        assertEquals(config.getHomePage(), URI.create("http://alice.example.com"));
    }

    @Test
    public void testRecordedDefaults()
            throws Exception
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(PersonConfig.class)
                .setName("Dain")
                .setEmail("dain@proofpoint.com")
                .setPhone(null)
                .setHomePage(URI.create("http://iq80.com")));
    }

    @Test
    public void testRecordedDefaultsOneOfEverything()
            throws Exception
    {
        assertRecordedDefaults(ConfigAssertions.recordDefaults(Config1.class)
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
                .setShortOption((short) 0)
                .setStringOption(null)
                .setValueClassOption(null)

        );
    }

    @Test
    public void testRecordedDefaultsFailInvokedDeprecatedSetter()
            throws MalformedURLException
    {
        boolean pass = true;
        try {
            assertRecordedDefaults(ConfigAssertions.recordDefaults(PersonConfig.class)
                    .setName("Dain")
                    .setEmail("dain@proofpoint.com")
                    .setPhone(null)
                    .setHomePageUrl(new URL("http://iq80.com")));
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "HomePageUrl");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testRecordedDefaultsFailInvokedExtraMethod()
    {
        boolean pass = true;
        try {
            PersonConfig config = ConfigAssertions.recordDefaults(PersonConfig.class)
                    .setName("Dain")
                    .setEmail("dain@proofpoint.com")
                    .setPhone(null)
                    .setHomePage(URI.create("http://iq80.com"));

            // extra non setter method invoked
            config.hashCode();

            assertRecordedDefaults(config);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "hashCode()");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testExplicitPropertyMappingsWithMap()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .put("simple.email", "jenny@compuserve.com")
                .put("sub.a.phone", "867-5309")
                .build();

        MapConfig expected = new MapConfig()
                .setName("Jenny")
                .setSimpleMap(ImmutableMap.of("email", "jenny@compuserve.com"))
                .setSubMap(ImmutableMap.of("a", new SubConfig().setPhone("867-5309")));

        assertFullMapping(properties, expected);
    }

    @Test
    public void testExplicitPropertyMappingsWithMapFailUntestedProperty()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("name", "Jenny")
                .build();

        MapConfig expected = new MapConfig()
                .setName("Jenny");

        boolean pass = true;
        try {
            assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            assertContains(e.getMessage(), "simple");
            assertContains(e.getMessage(), "sub");
        }

        if (pass) {
            fail("Expected AssertionError");
        }
    }

    @Test
    public void testLegacyPropertiesWithMap()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("simple.email", "jenny@compuserve.com")
                .put("sub.a.phone", "867-5309")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("simple-legacy.email", "jenny@compuserve.com")
                .put("sub.a.phone", "867-5309")
                .build();

        Map<String, String> olderProperties = new ImmutableMap.Builder<String, String>()
                .put("simple.email", "jenny@compuserve.com")
                .put("sub-legacy.a.phone", "867-5309")
                .build();

        assertLegacyEquivalence(MapConfig.class, currentProperties, oldProperties, olderProperties);
    }

    static class PersonConfig
    {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;
        private URI homePage = URI.create("http://iq80.com");

        String getName()
        {
            return name;
        }

        @Config("name")
        PersonConfig setName(String name)
        {
            this.name = name;
            return this;
        }

        String getEmail()
        {
            return email;
        }

        @Config("email")
        @LegacyConfig({"exchange-id", "notes-id"})
        PersonConfig setEmail(String email)
        {
            this.email = email;
            return this;
        }

        String getPhone()
        {
            return phone;
        }

        @Config("phone")
        PersonConfig setPhone(String phone)
        {
            this.phone = phone;
            return this;
        }

        URI getHomePage()
        {
            return homePage;
        }

        @Config("home-page")
        PersonConfig setHomePage(URI homePage)
        {
            this.homePage = homePage;
            return this;
        }

        @LegacyConfig(value = "home-page-url", replacedBy = "home-page")
        PersonConfig setHomePageUrl(URL homePage)
        {
            try {
                this.homePage = homePage.toURI();
                return this;
            }
            catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    static class NoLegacyConfig
    {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;
        private URI homePage = URI.create("http://iq80.com");

        String getName()
        {
            return name;
        }

        @Config("name")
        NoLegacyConfig setName(String name)
        {
            this.name = name;
            return this;
        }

        String getEmail()
        {
            return email;
        }

        @Config("email")
        NoLegacyConfig setEmail(String email)
        {
            this.email = email;
            return this;
        }

        String getPhone()
        {
            return phone;
        }

        @Config("phone")
        NoLegacyConfig setPhone(String phone)
        {
            this.phone = phone;
            return this;
        }

        URI getHomePage()
        {
            return homePage;
        }

        @Config("home-page")
        NoLegacyConfig setHomePage(URI homePage)
        {
            this.homePage = homePage;
            return this;
        }
    }

    public static class SubConfig
    {
        private String phone = null;

        public String getPhone()
        {
            return phone;
        }

        @Config("phone")
        public SubConfig setPhone(String phone)
        {
            this.phone = phone;
            return this;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(phone);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final SubConfig other = (SubConfig) obj;
            return Objects.equals(this.phone, other.phone);
        }
    }

    public static class MapConfig
    {
        private String name = "Dain";
        private Map<String, String> simpleMap = null;
        private Map<String, SubConfig> subMap = null;

        public String getName()
        {
            return name;
        }

        @Config("name")
        public MapConfig setName(String name)
        {
            this.name = name;
            return this;
        }

        public Map<String, String> getSimpleMap()
        {
            return simpleMap;
        }

        @Config("simple")
        @LegacyConfig("simple-legacy")
        public MapConfig setSimpleMap(Map<String, String> simpleMap)
        {
            this.simpleMap = simpleMap;
            return this;
        }

        public Map<String, SubConfig> getSubMap()
        {
            return subMap;
        }

        @Config("sub")
        @LegacyConfig("sub-legacy")
        public MapConfig setSubMap(Map<String, SubConfig> subMap)
        {
            this.subMap = subMap;
            return this;
        }
    }

}
