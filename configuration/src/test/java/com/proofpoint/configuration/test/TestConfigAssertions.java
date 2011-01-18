package com.proofpoint.configuration.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DeprecatedConfig;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestConfigAssertions
{
    @Test
    public void testNoDefaults() {
        ConfigAssertions.assertNoDefaults(new PersonConfig()
                .setName("Jenny")
                .setEmail("jenny@compuserve.com")
                .setPhone("867-5309"));

        boolean success = false;
        try {
            ConfigAssertions.assertNoDefaults(new PersonConfig()
                    .setName("Jenny")
                    .setEmail("jenny@compuserve.com"));
            success = true;
        }
        catch (AssertionError e) {
            // expected
            Assertions.assertContains(e.getMessage().toLowerCase(), "phone");
        }

        if (success) {
            Assert.fail("Expected AssertionError");
        }

        success = false;
        try {
            ConfigAssertions.assertNoDefaults(new PersonConfig()
                    .setEmail("jenny@compuserve.com")
                    .setPhone("867-5309"));
            success = true;
        }
        catch (AssertionError e) {
            // expected
            Assertions.assertContains(e.getMessage().toLowerCase(), "name");
        }

        if (success) {
            Assert.fail("Expected AssertionError");
        }

    }

    @Test
    public void testPropertiesSupported() {
        ConfigAssertions.assertPropertiesSupported(ImmutableSet.of("name", "email", "exchange-id", "notes-id", "phone"), PersonConfig.class);

        boolean success = false;
        try {
            ConfigAssertions.assertPropertiesSupported(ImmutableSet.of("name", "email", "phone", "cell"), PersonConfig.class);
        }
        catch (AssertionError e) {
            // expected
            Assertions.assertContains(e.getMessage().toLowerCase(), "cell");
        }
        if (success) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testAttributesEqualsObject() {
        ConfigAssertions.assertAttributesEqual(
                new PersonConfig().setName("Martin"),
                new PersonConfig().setName("Martin"));

        ConfigAssertions.assertAttributesEqual(
                new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"),
                new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"));

        boolean success = false;
        try {
            ConfigAssertions.assertAttributesEqual(
                    new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"),
                    new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("can and string"));
        }
        catch (AssertionError e) {
            // expected
            Assertions.assertContains(e.getMessage().toLowerCase(), "phone");
        }
        if (success) {
            Assert.fail("Expected AssertionError");
        }
    }

    @Test
    public void testAttributesEqualsProperties() {
        ConfigAssertions.assertAttributesEqual(
                ImmutableMap.of("name", "Martin"),
                new PersonConfig().setName("Martin"));

        ConfigAssertions.assertAttributesEqual(
                ImmutableMap.of(
                        "name", "Martin",
                        "email", "martin@proofpoint.com",
                        "phone", "iPhone 4"),
                new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"));

        ConfigAssertions.assertAttributesEqual(
                ImmutableMap.of(
                        "name", "Martin",
                        "exchange-id", "martin@proofpoint.com",
                        "phone", "iPhone 4"),
                new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"));

        ConfigAssertions.assertAttributesEqual(
                ImmutableMap.of(
                        "name", "Martin",
                        "notes-id", "martin@proofpoint.com",
                        "phone", "iPhone 4"),
                new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("iPhone 4"));

        boolean success = false;
        try {
            ConfigAssertions.assertAttributesEqual(
                    ImmutableMap.of(
                            "name", "Martin",
                            "email", "martin@proofpoint.com",
                            "phone", "iPhone 4"),
                    new PersonConfig().setName("Martin").setEmail("martin@proofpoint.com").setPhone("tin can and string"));
        }
        catch (AssertionError e) {
            // expected
            Assertions.assertContains(e.getMessage().toLowerCase(), "phone");
        }
        if (success) {
            Assert.fail("Expected AssertionError");
        }
    }

    public static class PersonConfig {
        private String name = "Dain";
        private String email = "dain@proofpoint.com";
        private String phone;

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
    }
}
