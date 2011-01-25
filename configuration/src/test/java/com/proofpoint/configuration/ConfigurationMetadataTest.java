package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.ConfigurationException;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.testng.Assert.fail;

public class ConfigurationMetadataTest
{
    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class)
                ),
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class)
                )
        );

        EquivalenceTester.check(
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class).getAttributes().get("Value")
                ),
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value")
                )
        );
    }

    @Test
    public void testSetterConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class, monitor);
        verifyMetaData(metadata, SetterConfigClass.class, true, true, true, "description");
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testSubSetterConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class, monitor);
        verifyMetaData(metadata, SetterSubConfigClass.class, true, true, true, "description");
        Problems problems = metadata.getProblems();
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testSetterInterfaceImpl()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterInterfaceImpl.class, monitor);
        verifyMetaData(metadata, SetterInterfaceImpl.class, true, true, true, "description");
        Problems problems = metadata.getProblems();
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
        verifyMetaData(metadata, NotPublicClass.class, true, true, true, null);
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
        verifyMetaData(metadata, NotPublicConstructorClass.class, true, true, true, null);
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
        verifyMetaData(metadata, NoNoArgConstructorClass.class, false, true, true, null);
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
        verifyMetaData(metadata, NoConfigMethodsClass.class, true, false, false, null);
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
        verifyMetaData(metadata, GetterAndSetterAnnotatedClass.class, true, true, true, null);
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
        verifyMetaData(metadata, EmptyPropertyNameClass.class, true, false, false, null);
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
        verifyMetaData(metadata, NotPublicAttributeClass.class, true, false, false, null);
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
        verifyMetaData(metadata, StaticAttributeClass.class, true, false, false, null);
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
        verifyMetaData(metadata, GetterWithParameterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, GetterNoReturnClass.class, true, false, false, null);
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
        verifyMetaData(metadata, GetterNoSetterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, GetterMultipleSettersClass.class, true, false, false, null);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testGetterPrivateSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterPrivateSetterClass.class, monitor);
        verifyMetaData(metadata, GetterPrivateSetterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, IsMethodWithParameterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, IsMethodNoReturnClass.class, true, false, false, null);
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
        verifyMetaData(metadata, IsMethodNoSetterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, IsMethodMultipleSettersClass.class, true, false, false, null);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testIsMethodPrivateSetterClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodPrivateSetterClass.class, monitor);
        verifyMetaData(metadata, IsMethodPrivateSetterClass.class, true, false, false, null);
        Problems problems = metadata.getProblems();
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
        verifyMetaData(metadata, SetterWithNoParameterClass.class, true, false, false, null);
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
        verifyMetaData(metadata, NotJavaBeanClass.class, true, false, false, null);
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
        verifyMetaData(metadata, MultipleAnnotatedSettersClass.class, true, false, false, null);
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
        verifyMetaData(metadata, CurrentAndLegacyConfigOnSetterClass.class, "value", ImmutableList.of("replacedValue"), true, true, true, "description");
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
        verifyMetaData(metadata, MultipleLegacyConfigClass.class, "value", ImmutableList.of("legacy1", "legacy2"), true, true, true, "description");
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testEmptyStringLegacyConfigClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyStringLegacyConfigClass.class, monitor);
        verifyMetaData(metadata, EmptyStringLegacyConfigClass.class, true, false, false, null);
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
        verifyMetaData(metadata, EmptyArrayLegacyConfigClass.class, true, false, false, null);
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
        verifyMetaData(metadata, EmptyStringInArrayLegacyConfigClass.class, true, false, false, null);
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
        verifyMetaData(metadata, LegacyConfigDuplicatesConfigClass.class, true, false, false, null);
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
        verifyMetaData(metadata, LegacyConfigDuplicatesConfigOnOtherMethodClass.class, true, false, false, null);
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
        verifyMetaData(metadata, LegacyConfigDuplicatesConfigOnLinkedMethodClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DeprecatedConfigClass.class, true, false, false, null);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }


    @Test
    public void testDeprecatedConfigOnSetterOnlyClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnSetterOnlyClass.class, monitor);
        verifyMetaData(metadata, DeprecatedConfigOnSetterOnlyClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DeprecatedConfigOnGetterOnlyClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DefunctConfigClass.class, true, false, false, null);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
    }

    @Test
    public void testDefunctConfigEmptyArrayClass()
            throws Exception
    {
        TestMonitor monitor = new TestMonitor();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigEmptyArrayClass.class, monitor);
        verifyMetaData(metadata, DefunctConfigEmptyArrayClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DefunctConfigEmptyStringClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DefunctConfigInUseClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DefunctConfigInLegacyUseClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DefunctConfigInLinkedLegacyUseClass.class, true, false, false, null);
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
        verifyMetaData(metadata, DuplicateDefunctConfigClass.class, true, false, false, null);
        monitor.assertNumberOfErrors(1);
        monitor.assertNumberOfWarnings(0);
        monitor.assertMatchingErrorRecorded("Defunct property", "'defunct'", "listed more than once");
    }

    private void verifyMetaData(ConfigurationMetadata<?> metadata, Class<?> configClass, boolean hasConstructor, boolean hasGetter, boolean hasSetter, String description)
            throws Exception
    {
         verifyMetaData(metadata, configClass, "value", ImmutableList.<String>of(), hasConstructor, hasGetter, hasSetter, description);
    }

    private void verifyMetaData(ConfigurationMetadata<?> metadata, Class<?> configClass, String propertyName, ImmutableList<String> legacyNames, boolean hasConstructor, boolean hasGetter, boolean hasSetter, String description)
            throws Exception
    {
        Assert.assertEquals(metadata.getConfigClass(), configClass);
        if (hasConstructor) {
            Assert.assertEquals(metadata.getConstructor(), configClass.getDeclaredConstructor());
        }
        if (hasGetter || hasSetter) {
            Assert.assertEquals(metadata.getAttributes().size(), 1);
            AttributeMetadata attribute = metadata.getAttributes().get("Value");
            Assert.assertEquals(attribute.getConfigClass(), configClass);
            Set<String> namesToTest = Sets.newHashSet();
            for (ConfigurationMetadata.InjectionPointMetaData legacyInjectionPoint : attribute.getLegacyInjectionPoints()) {
                namesToTest.add(legacyInjectionPoint.getProperty());
            }
            Assert.assertEquals(namesToTest, legacyNames);
            Assert.assertEquals(attribute.getName(), "Value");
            Assert.assertEquals(attribute.getInjectionPoint().getProperty(), propertyName);
            if (hasGetter) {
                Assert.assertEquals(attribute.getGetter(), findMethod(configClass, "getValue"));
            }
            if (hasSetter) {
                Assert.assertEquals(attribute.getInjectionPoint().getSetter(), findMethod(configClass, "setValue", String.class));
            }
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

    private static Method findMethod(Class<?> configClass, String methodName, Class<?>... paramTypes)
    {
        Method method = null;
        try {
            method = configClass.getDeclaredMethod(methodName, paramTypes);
            if (method.isAnnotationPresent(Config.class)) {
                return method;
            }
        }
        catch (NoSuchMethodException e) {
            // ignore
        }

        if (configClass.getSuperclass() != null) {
            Method managedMethod = findMethod(configClass.getSuperclass(), methodName, paramTypes);
            if (managedMethod != null) {
                if (managedMethod.isAnnotationPresent(Config.class)) {
                    return managedMethod;
                }
                method = managedMethod;
            }
        }

        for (Class<?> iface : configClass.getInterfaces()) {
            Method managedMethod = findMethod(iface, methodName, paramTypes);
            if (managedMethod != null) {
                if (managedMethod.isAnnotationPresent(Config.class)) {
                    return managedMethod;
                }
                method = managedMethod;
            }
        }

        return method;
    }
}
