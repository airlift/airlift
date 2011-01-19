package com.proofpoint.configuration.test;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DeprecatedConfig;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class TestConfigAssertions
{
    @Test
    public void testDefaults()
    {
        Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
        expectedAttributeValues.put("Name", "Dain");
        expectedAttributeValues.put("Email", "dain@proofpoint.com");
        expectedAttributeValues.put("Phone", null);
        expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
        ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
    }

    @Test
    public void testDefaultsFailNotDefault()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", "42");
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "Phone");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailNotDefaultWithNullAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://example.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUnsupportedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePage", URI.create("http://iq80.com"));
            expectedAttributeValues.put("UnsupportedAttribute", "value");
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "UnsupportedAttribute");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailUntestedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDefaultsFailDeprecatedAttribute()
    {
        boolean pass = true;
        try {
            Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
            expectedAttributeValues.put("Name", "Dain");
            expectedAttributeValues.put("Email", "dain@proofpoint.com");
            expectedAttributeValues.put("Phone", null);
            expectedAttributeValues.put("HomePageUrl", URI.create("http://iq80.com"));
            ConfigAssertions.assertDefaults(expectedAttributeValues, PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "HomePageUrl");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
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

        ConfigAssertions.assertFullMapping(properties, expected);
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
            ConfigAssertions.assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "unsupported-property");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
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
            ConfigAssertions.assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "home-page");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
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
            ConfigAssertions.assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "Name");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
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
            ConfigAssertions.assertFullMapping(properties, expected);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "HomePage");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDeprecatedProperties()
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

        ConfigAssertions.assertDeprecatedEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
    }

    @Test
    public void testDeprecatedPropertiesFailUnsupportedProperties()
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
            ConfigAssertions.assertDeprecatedEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "unsupported-property");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDeprecatedPropertiesFailUntestedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("email", "alice@example.com")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("exchange-id", "alice@example.com")
                .build();

        boolean pass = true;
        try {
            ConfigAssertions.assertDeprecatedEquivalence(PersonConfig.class, currentProperties, oldProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "notes-id");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testDeprecatedPropertiesFailDeprecatedCurrentProperties()
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
            ConfigAssertions.assertDeprecatedEquivalence(PersonConfig.class, currentProperties, oldProperties, olderProperties);
        }
        catch (AssertionError e) {
            // expected
            pass = false;
            Assertions.assertContains(e.getMessage(), "notes-id");
        }

        if (pass) {
            Assert.fail("Expected AssertionError");
        }
    }

    public static class PersonConfig
    {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;
        private URI homePage = URI.create("http://iq80.com");

        @Config("name")
        public String getName()
        {
            return name;
        }

        public PersonConfig setName(String name)
        {
            this.name = name;
            return this;
        }

        @Config("email")
        @DeprecatedConfig({"exchange-id", "notes-id"})
        public String getEmail()
        {
            return email;
        }

        public PersonConfig setEmail(String email)
        {
            this.email = email;
            return this;
        }

        @Config("phone")
        public String getPhone()
        {
            return phone;
        }

        public PersonConfig setPhone(String phone)
        {
            this.phone = phone;
            return this;
        }

        @Config("home-page")
        public URI getHomePage()
        {
            return homePage;
        }

        public PersonConfig setHomePage(URI homePage)
        {
            this.homePage = homePage;
            return this;
        }

        @DeprecatedConfig("home-page-url")
        public PersonConfig setHomePageUrl(URL homePage)
                throws URISyntaxException
        {
            this.homePage = homePage.toURI();
            return this;
        }
    }
}
