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
package io.airlift.configuration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.ConfigurationException;
import io.airlift.configuration.ConfigurationMetadata.AttributeMetadata;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.fail;

public class ConfigurationMetadataTest
{
    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class)
                )
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class)
                )
                .check();

        equivalenceTester()
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class).getAttributes().get("Value")
                )
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value")
                )
                .check();
    }

    @Test
    public void testSetterConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        
        verifyMetaData(metadata, SetterConfigClass.class, "description", expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testSubSetterConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterSubConfigClass.class, "description", expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testSetterInterfaceImpl()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterInterfaceImpl.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterInterfaceImpl.class, "description", expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testSetterNoGetterConfigClassThrows()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        try {
            ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(SetterNoGetterConfigClass.class, monitor);
            fail("Expected ConfigurationException");
        }
        catch (ConfigurationException e)
        {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("No getter");
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNull()
    {
        ConfigurationMetadata.getConfigurationMetadata(null);
    }

    @Test
    public void testAbstractClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(AbstractClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, AbstractClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("abstract", AbstractClass.class.getName());
    }

    @Test
    public void testGetValidAbstractClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        try {
            ConfigurationMetadata.getValidConfigurationMetadata(AbstractClass.class, monitor);
            fail("Expected ConfigurationException");
        }
        catch (ConfigurationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("abstract", AbstractClass.class.getName());
        }
    }

    @Test
    public void testNotPublicClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NotPublicClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("not public", NotPublicClass.class.getName());
    }

    @Test
    public void testNotPublicConstructorClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicConstructorClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NotPublicConstructorClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("not public", metadata.getConfigClass().getName() + "()");
    }

    @Test
    public void testNoNoArgConstructorClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NoNoArgConstructorClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NoNoArgConstructorClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("no-arg constructor", NoNoArgConstructorClass.class.getName());
    }

    @Test
    public void testNoConfigMethodsClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NoConfigMethodsClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, NoConfigMethodsClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("does not have any @Config annotations", NoConfigMethodsClass.class.getName());
    }

    @Test
    public void testGetterAndSetterAnnotatedClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterAndSetterAnnotatedClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, GetterAndSetterAnnotatedClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded(GetterAndSetterAnnotatedClass.class.getName());
    }

    @Test
    public void testEmptyPropertyNameClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyPropertyNameClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, EmptyPropertyNameClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("empty value", "setValue");
    }

    @Test
    public void testNotPublicAttributeClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicAttributeClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, NotPublicAttributeClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("No getter", "unusable", "getValue", "setValue");
    }

    @Test
    public void testStaticAttributeClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(StaticAttributeClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, StaticAttributeClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded(StaticAttributeClass.class.getName(), "setValue", "is static");
    }

    @Test
    public void testGetterWithParameterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterWithParameterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, GetterWithParameterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("No getter", "unusable", "getValue", "setValue");
    }

    @Test
    public void testGetterNoReturnClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoReturnClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, GetterNoReturnClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("No getter", "unusable", "getValue", "setValue");
    }

    @Test
    public void testGetterNoSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoSetterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, GetterNoSetterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("is not a valid setter", GetterNoSetterClass.class.getName(), "getValue");
    }

    @Test
    public void testGetterMultipleSettersClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterMultipleSettersClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, GetterMultipleSettersClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testGetterPrivateSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterPrivateSetterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, GetterPrivateSetterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@Config method", "setValue", "is not public");
    }


    @Test
    public void testIsMethodWithParameterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodWithParameterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, IsMethodWithParameterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("No getter", "unusable", "isValue", "setValue");
    }

    @Test
    public void testIsMethodNoReturnClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoReturnClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, IsMethodNoReturnClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("No getter", "setValue");
    }

    @Test
    public void testIsMethodNoSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoSetterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, IsMethodNoSetterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded(IsMethodNoSetterClass.class.getName(), "isValue", "is not a valid setter");
    }

    @Test
    public void testIsMethodMultipleSettersClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodMultipleSettersClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, IsMethodMultipleSettersClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testIsMethodPrivateSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodPrivateSetterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, IsMethodPrivateSetterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@Config method", IsMethodPrivateSetterClass.class.getName(), "setValue", "is not public");
    }

    @Test
    public void testSetterWithNoParameterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterWithNoParameterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, SetterWithNoParameterClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("does not have exactly one parameter", SetterWithNoParameterClass.class.getName(), "setValue");
    }

    @Test
    public void testNotJavaBeanClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotJavaBeanClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, NotJavaBeanClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("not a valid setter", "putValue");
    }

    @Test
    public void testMultipleAnnotatedSettersClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(MultipleAnnotatedSettersClass.class, monitor);

        // Not validating metadata, since the actual setter it picks is not deterministic

        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("Multiple methods are annotated", "Value");
    }

    @Test
    public void testCurrentAndLegacyConfigOnGetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(CurrentAndLegacyConfigOnGetterClass.class, monitor);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("not a valid setter", "getValue");
    }

    @Test
    public void testCurrentAndLegacyConfigOnSetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(CurrentAndLegacyConfigOnSetterClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, CurrentAndLegacyConfigOnSetterClass.class, "description", expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testCurrentConfigWithReplacedByClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(CurrentConfigWithReplacedByClass.class, monitor);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@Config method", "setValue", "claiming to be replaced by", "'other-name'");
    }

    @Test
    public void testLegacyConfigOnGetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnGetterClass.class, monitor);
        monitor.assertNumberOfErrors(2);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("not a valid setter", "getValue");
        monitor.assertMatchingErrorRecorded("LegacyConfig", "getValue", "not associated with any valid @Config");
    }

    @Test
    public void testLegacyConfigOnSetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnSetterClass.class, monitor);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("LegacyConfig", "setValue", "not associated with any valid @Config");
    }

    @Test
    public void testLegacyConfigOnDeprecatedSetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnDeprecatedSetterClass.class, monitor);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testLegacyConfigOnNonDeprecatedSetterClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnNonDeprecatedSetterClass.class, monitor);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("Replaced @LegacyConfig method", "setValue(int)", "should be @Deprecated");
    }

    @Test
    public void testMultipleLegacyConfigClass()
        throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(MultipleLegacyConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("legacy1", "legacy2", "value"));


        verifyMetaData(metadata, MultipleLegacyConfigClass.class, "description", expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testEmptyStringLegacyConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyStringLegacyConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, EmptyStringLegacyConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig method", EmptyStringLegacyConfigClass.class.getName(), "setValue", "null or empty value");
    }

    @Test
    public void testEmptyArrayLegacyConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyArrayLegacyConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, EmptyArrayLegacyConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig method", EmptyArrayLegacyConfigClass.class.getName(), "setValue", "empty list");
    }

    @Test
    public void testEmptyStringInArrayLegacyConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyStringInArrayLegacyConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, EmptyStringInArrayLegacyConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig method", EmptyStringInArrayLegacyConfigClass.class.getName(), "setValue", "null or empty value");
    }

    @Test
    public void testLegacyConfigDuplicatesConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();

        verifyMetaData(metadata, LegacyConfigDuplicatesConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@Config property", "'value'", "appears in @LegacyConfig", "setValue");
    }

    @Test
    public void testLegacyConfigDuplicatesConfigOnOtherMethodClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigOnOtherMethodClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, LegacyConfigDuplicatesConfigOnOtherMethodClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(2);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig", "'value'", "is replaced by @Config", "same name", "setValue");
        monitor.assertMatchingErrorRecorded("@LegacyConfig", "setValue", "not associated with any valid @Config");
    }

    @Test
    public void testLegacyConfigDuplicatesConfigOnLinkedMethodClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigOnLinkedMethodClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, LegacyConfigDuplicatesConfigOnLinkedMethodClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(2);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig", "'value'", "is replaced by @Config", "same name", "setValue");
        monitor.assertMatchingErrorRecorded("@LegacyConfig", "setIntValue", "not associated with any valid @Config");
    }

    @Test
    public void testDeprecatedConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }


    @Test
    public void testDeprecatedConfigOnSetterOnlyClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnSetterOnlyClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigOnSetterOnlyClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("getDeprecated", "setDeprecated", "must be @Deprecated together");
    }


    @Test
    public void testDeprecatedConfigOnGetterOnlyClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnGetterOnlyClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigOnGetterOnlyClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("getDeprecated", "setDeprecated", "must be @Deprecated together");
    }

    @Test
    public void testDefunctConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testDefunctConfigEmptyArrayClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigEmptyArrayClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigEmptyArrayClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@DefunctConfig", "is empty");
    }

    @Test
    public void testDefunctConfigEmptyStringClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigEmptyStringClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigEmptyStringClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@DefunctConfig", "contains empty values");
    }

    @Test
    public void testDefunctConfigInUseClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInUseClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigInUseClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@Config property", "'value'", "setValue", "is defunct on class");
    }

    @Test
    public void testDefunctConfigInLegacyUseClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInLegacyUseClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, DefunctConfigInLegacyUseClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig property", "'replacedValue'", "setValue", "is defunct on class");
    }

    @Test
    public void testDefunctConfigInLinkedLegacyUseClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInLinkedLegacyUseClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, DefunctConfigInLinkedLegacyUseClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("@LegacyConfig property", "'replacedValue'", "setIntValue", "is defunct on class");
    }

    @Test
    public void testDuplicateDefunctConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DuplicateDefunctConfigClass.class, monitor);
        Map<String, Set<String>> expectedAttributes = Maps.newHashMap();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DuplicateDefunctConfigClass.class, null, expectedAttributes);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("Defunct property", "'defunct'", "listed more than once");
    }

    private void verifyMetaData(ConfigurationMetadata<?> metadata, Class<?> configClass, String description, Map<String, Set<String>> attributeProperties)
            throws Exception
    {
        Assert.assertEquals(metadata.getConfigClass(), configClass);

        if (metadata.getConstructor() != null) {
            Assert.assertEquals(metadata.getConstructor(), configClass.getDeclaredConstructor());
        } else {
            try {
                configClass.getDeclaredConstructor();
                Assert.fail(String.format("Expected configClass [%s] not to have a constructor", configClass.getName()));
            } catch (NoSuchMethodException expected) {

            }
        }

        Assert.assertEquals(metadata.getAttributes().size(), attributeProperties.keySet().size());

        for (String name : attributeProperties.keySet()) {
            AttributeMetadata attribute = metadata.getAttributes().get(name);
            Assert.assertEquals(attribute.getConfigClass(), configClass);
            Set<String> namesToTest = Sets.newHashSet();
            namesToTest.add(attribute.getInjectionPoint().getProperty());
            for (ConfigurationMetadata.InjectionPointMetaData legacyInjectionPoint : attribute.getLegacyInjectionPoints()) {
                namesToTest.add(legacyInjectionPoint.getProperty());
            }
            Assert.assertEquals(namesToTest, attributeProperties.get(name));
            Assert.assertEquals(attribute.getDescription(), description);
        }
    }

    public static class SetterConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @ConfigDescription("description")
        public void setValue(String value)
        {
            this.value = value;
        }

        public void setValue(Object value)
        {
            this.value = String.valueOf(value);
        }
    }

    public static class SetterSubConfigClass extends SetterConfigClass
    {
    }

    public interface SetterInterface
    {
        @Config("value")
        @ConfigDescription("description")
        public void setValue(String value);
    }

    public static class SetterInterfaceImpl implements SetterInterface
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class SetterNoGetterConfigClass
    {
        @Config("value")
        public void setValue(String value)
        {
        }
    }

    public static abstract class AbstractClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    static class NotPublicClass
    {
        private String value;

        public NotPublicClass()
        {
        }

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class NoNoArgConstructorClass
    {
        private String value;

        public NoNoArgConstructorClass(String value)
        {
            this.value = value;
        }

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class NotPublicConstructorClass
    {
        private String value;

        NotPublicConstructorClass()
        {
        }

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class NoConfigMethodsClass
    {
    }

    public static class GetterAndSetterAnnotatedClass
    {
        private String value;

        @Config("value")
        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class EmptyPropertyNameClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class NotPublicAttributeClass
    {
        private String value;

        String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class StaticAttributeClass
    {
        private static String value;

        public static String getValue()
        {
            return value;
        }

        @Config("value")
        public static void setValue(String v)
        {
            value = v;
        }
    }

    public static class GetterWithParameterClass
    {
        private String value;

        public String getValue(String foo)
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class GetterNoReturnClass
    {
        public void getValue()
        {
        }

        @Config("value")
        public void setValue(String value)
        {
        }
    }

    public static class GetterNoSetterClass
    {
        @Config("value")
        public String getValue()
        {
            return null;
        }
    }

    public static class GetterMultipleSettersClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        public void setValue(Object value)
        {
        }
    }

    public static class GetterPrivateSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        private void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class IsMethodWithParameterClass
    {
        private boolean value;

        public boolean isValue(boolean foo)
        {
            return value;
        }

        @Config("value")
        public void setValue(boolean value)
        {
            this.value = value;
        }
    }

    public static class IsMethodNoReturnClass
    {
        public void isValue()
        {
        }

        @Config("value")
        public void setValue(boolean value)
        {
        }
    }

    public static class IsMethodNoSetterClass
    {
        @Config("value")
        public boolean isValue()
        {
            return false;
        }
    }

    public static class IsMethodMultipleSettersClass
    {
        private boolean value;

        public boolean isValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(boolean value)
        {
            this.value = value;
        }

        public void setValue(Object value)
        {
        }
    }

    public static class IsMethodPrivateSetterClass
    {
        private boolean value;

        public boolean isValue()
        {
            return value;
        }

        @Config("value")
        private void setValue(boolean value)
        {
            this.value = value;
        }
    }

    public static class SetterWithNoParameterClass
    {
        public String getValue()
        {
            return null;
        }

        @Config("value")
        public void setValue()
        {
        }
    }

    public static class NotJavaBeanClass
    {
        private String value;

        public String fetchValue()
        {
            return value;
        }

        @Config("value")
        public void putValue(String value)
        {
            this.value = value;
        }
    }

    public static class MultipleAnnotatedSettersClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Config("int-value")
        public void setValue(int value)
        {
            this.value = Integer.toString(value);
        }

    }

    public static class CurrentAndLegacyConfigOnGetterClass
    {
        private String value;

        @Config("value")
        @LegacyConfig("replacedValue")
        @ConfigDescription("description")
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class CurrentAndLegacyConfigOnSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @LegacyConfig("replacedValue")
        @ConfigDescription("description")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class CurrentConfigWithReplacedByClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        public String getValueByOtherName()
        {
            return value;
        }

        @Config("other-name")
        public void setValueByOtherName(String value)
        {
            this.value = value;
        }

        @Config("value")
        @LegacyConfig(value = "replacedValue", replacedBy = "other-name")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class LegacyConfigOnGetterClass
    {
        private String value;

        @LegacyConfig("replacedValue")
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class LegacyConfigOnSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @LegacyConfig("replacedValue")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class LegacyConfigOnDeprecatedSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        public void setValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    public static class LegacyConfigOnNonDeprecatedSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @LegacyConfig("replacedValue")
        public void setValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    public static class MultipleLegacyConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @LegacyConfig({"legacy1", "legacy2"})
        @ConfigDescription("description")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class EmptyStringLegacyConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @LegacyConfig("")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class EmptyArrayLegacyConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @LegacyConfig({})
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class EmptyStringInArrayLegacyConfigClass
     {
         private String value;

         public String getValue()
         {
             return value;
         }

         @Config("value")
         @LegacyConfig({"foo", ""})
         public void setValue(String value)
         {
             this.value = value;
         }
     }

    public static class LegacyConfigDuplicatesConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @LegacyConfig("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class LegacyConfigDuplicatesConfigOnOtherMethodClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("value")
        public void setValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    public static class LegacyConfigDuplicatesConfigOnLinkedMethodClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig(value = "value", replacedBy = "value")
        public void setIntValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    public static class DeprecatedConfigClass
    {
        private String value;
        private String deprecated;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        public String getDeprecated()
        {
            return deprecated;
        }

        @Deprecated
        @Config("deprecated-value")
        public void setDeprecated(String deprecated)
        {
            this.deprecated = deprecated;
        }
    }

    public static class DeprecatedConfigOnSetterOnlyClass
    {
        private String value;
        private String deprecated;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        public String getDeprecated()
        {
            return deprecated;
        }

        @Deprecated
        @Config("deprecated-value")
        public void setDeprecated(String deprecated)
        {
            this.deprecated = deprecated;
        }
    }

    public static class DeprecatedConfigOnGetterOnlyClass
    {
        private String value;
        private String deprecated;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        public String getDeprecated()
        {
            return deprecated;
        }

        @Config("deprecated-value")
        public void setDeprecated(String deprecated)
        {
            this.deprecated = deprecated;
        }
    }

    @DefunctConfig({"defunct1", "defunct2"})
    public static class DefunctConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    @DefunctConfig({})
    public static class DefunctConfigEmptyArrayClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    @DefunctConfig({"defunct1", ""})
    public static class DefunctConfigEmptyStringClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    @DefunctConfig("value")
    public static class DefunctConfigInUseClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    @DefunctConfig("replacedValue")
    public static class DefunctConfigInLegacyUseClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        public void setValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    @DefunctConfig("replacedValue")
    public static class DefunctConfigInLinkedLegacyUseClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig(value = "replacedValue", replacedBy = "value")
        public void setIntValue(int value)
        {
            this.value = Integer.toString(value);
        }
    }

    @DefunctConfig({"defunct", "defunct"})
    public static class DuplicateDefunctConfigClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(String value)
        {
            this.value = value;
        }
    }
}
