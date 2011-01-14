package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.testing.Assertions;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.testng.Assert.fail;

public class ConfigurationMetadataTest
{
    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(GetterConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(GetterConfigClass.class)
                ),
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class)
                )
        );

        EquivalenceTester.check(
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(GetterConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(GetterConfigClass.class).getAttributes().get("Value")
                ),
                ImmutableList.of(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class).getAttributes().get("Value")
                )
        );
    }

    @Test
    public void testGetterConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterConfigClass.class);
        verifyMetaData(metadata, GetterConfigClass.class, true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testValidGetterConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(GetterConfigClass.class);
        verifyMetaData(metadata, GetterConfigClass.class, true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testSetterConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class);
        verifyMetaData(metadata, SetterConfigClass.class, true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testSubGetterConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterSubConfigClass.class);
        verifyMetaData(metadata, GetterSubConfigClass.class, true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testGetterInterfaceImpl()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterInterfaceImpl.class);
        verifyMetaData(metadata, GetterInterfaceImpl.class, true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testSetterNoGetterConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterNoGetterConfigClass.class);
        verifyMetaData(metadata, SetterNoGetterConfigClass.class, true, false, true, null);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
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
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(AbstractClass.class);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 1);
        Message message = errors.getErrors().get(0);
        Assert.assertTrue(message.getMessage().contains("[" + metadata.getConfigClass().getName() + "]"));
        Assert.assertTrue(message.getMessage().contains("abstract"));
    }

    @Test
    public void testGetValidAbstractClass()
            throws Exception
    {
        try {
            ConfigurationMetadata.getValidConfigurationMetadata(AbstractClass.class);
            fail("Expected ConfigurationException");
        }
        catch (ConfigurationException e) {
            Collection<Message> errorMessages = e.getErrorMessages();
            Assert.assertEquals(errorMessages.size(), 1);
            Message message = errorMessages.iterator().next();
            Assert.assertTrue(message.getMessage().contains("[" + AbstractClass.class.getName() + "]"));
            Assert.assertTrue(message.getMessage().contains("abstract"));
        }
    }

    @Test
    public void testNotPublicClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicClass.class);
        verifyMetaData(metadata, NotPublicClass.class, true, true, true, null);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 1);
        Message message = errors.getErrors().get(0);
        Assert.assertTrue(message.getMessage().contains("[" + metadata.getConfigClass().getName() + "]"));
        Assert.assertTrue(message.getMessage().contains("not public"));

    }

    @Test
    public void testNotPublicConstructorClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicConstructorClass.class);
        verifyMetaData(metadata, NotPublicConstructorClass.class, true, true, true, null);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 1);
        Message message = errors.getErrors().get(0);
        Assert.assertTrue(message.getMessage().contains("[" + metadata.getConfigClass().getName() + "()]"));
        Assert.assertTrue(message.getMessage().contains("not public"));
    }

    @Test
    public void testNoNoArgConstructorClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NoNoArgConstructorClass.class);
        verifyMetaData(metadata, NoNoArgConstructorClass.class, false, true, true, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + metadata.getConfigClass().getName() + "]");
    }

    @Test
    public void testNoConfigMethodsClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NoConfigMethodsClass.class);
        verifyMetaData(metadata, NoConfigMethodsClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + metadata.getConfigClass().getName() + "]");
    }

    @Test
    public void testGetterAndSetterAnnotatedClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterAndSetterAnnotatedClass.class);
        verifyMetaData(metadata, GetterAndSetterAnnotatedClass.class, true, true, true, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + metadata.getConfigClass().getName() + "]");
    }

    @Test
    public void testEmptyPropertyNameClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyPropertyNameClass.class);
        verifyMetaData(metadata, EmptyPropertyNameClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + metadata.getConfigClass().getMethod("getValue").toGenericString() + "]");
    }

    @Test
    public void testNotPublicAttributeClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicAttributeClass.class);
        verifyMetaData(metadata, NotPublicAttributeClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        String name = "getValue";
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), name).toGenericString() + "]");
    }

    @Test
    public void testStaticAttributeClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(StaticAttributeClass.class);
        verifyMetaData(metadata, StaticAttributeClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testGetterWithParameterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterWithParameterClass.class);
        verifyMetaData(metadata, GetterWithParameterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        Class<?> configClass = metadata.getConfigClass();
        String name = "getValue";
        Class<?> parameterTypes = String.class;
        verifyErrors(errors, "[" + findMethod(configClass, name, parameterTypes).toGenericString() + "]");
    }

    @Test
    public void testGetterNoReturnClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoReturnClass.class);
        verifyMetaData(metadata, GetterNoReturnClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testGetterNoSetterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoSetterClass.class);
        verifyMetaData(metadata, GetterNoSetterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testGetterMultipleSettersClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterMultipleSettersClass.class);
        verifyMetaData(metadata, GetterMultipleSettersClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testGetterPrivateSetterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterPrivateSetterClass.class);
        verifyMetaData(metadata, GetterPrivateSetterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }


    @Test
    public void testIsMethodWithParameterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodWithParameterClass.class);
        verifyMetaData(metadata, IsMethodWithParameterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "isValue", boolean.class).toGenericString() + "]");
    }

    @Test
    public void testIsMethodNoReturnClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoReturnClass.class);
        verifyMetaData(metadata, IsMethodNoReturnClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "isValue").toGenericString() + "]");
    }

    @Test
    public void testIsMethodNoSetterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoSetterClass.class);
        verifyMetaData(metadata, IsMethodNoSetterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "isValue").toGenericString() + "]");
    }

    @Test
    public void testIsMethodMultipleSettersClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodMultipleSettersClass.class);
        verifyMetaData(metadata, IsMethodMultipleSettersClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "isValue").toGenericString() + "]");
    }

    @Test
    public void testIsMethodPrivateSetterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodPrivateSetterClass.class);
        verifyMetaData(metadata, IsMethodPrivateSetterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "isValue").toGenericString() + "]");
    }

    @Test
    public void testSetterWithNoParameterClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterWithNoParameterClass.class);
        verifyMetaData(metadata, SetterWithNoParameterClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "setValue").toGenericString() + "]");
    }

    @Test
    public void testNotJavaBeanClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotJavaBeanClass.class);
        verifyMetaData(metadata, NotJavaBeanClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "putValue", String.class).toGenericString() + "]");
    }

    @Test
    public void testCurrentAndDeprecatedConfigOnGetterClass()
        throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(CurrentAndDeprecatedConfigOnGetterClass.class);
        verifyMetaData(metadata, CurrentAndDeprecatedConfigOnGetterClass.class, "value", ImmutableList.of("deprecatedValue"), true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testCurrentAndDeprecatedConfigOnSetterClass()
        throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(CurrentAndDeprecatedConfigOnSetterClass.class);
        verifyMetaData(metadata, CurrentAndDeprecatedConfigOnSetterClass.class, "value", ImmutableList.of("deprecatedValue"), true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testDeprecatedConfigOnGetterClass()
        throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnGetterClass.class);
        verifyMetaData(metadata, DeprecatedConfigOnGetterClass.class, null, ImmutableList.of("deprecatedValue"), true, true, true, null);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testDeprecatedConfigOnSetterClass()
        throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnSetterClass.class);
        verifyMetaData(metadata, DeprecatedConfigOnSetterClass.class, null, ImmutableList.of("deprecatedValue"), true, true, true, null);
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testMultipleDeprecatedConfigClass()
        throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(MultipleDeprecatedConfigClass.class);
        verifyMetaData(metadata, MultipleDeprecatedConfigClass.class, "value", ImmutableList.of("deprecated1", "deprecated2"), true, true, true, "description");
        Errors errors = metadata.getErrors();
        Assert.assertEquals(errors.getErrors().size(), 0);
    }

    @Test
    public void testEmptyStringDeprecatedConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyStringDeprecatedConfigClass.class);
        verifyMetaData(metadata, EmptyStringDeprecatedConfigClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testEmptyArrayDeprecatedConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(EmptyArrayDeprecatedConfigClass.class);
        verifyMetaData(metadata, EmptyArrayDeprecatedConfigClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    @Test
    public void testDeprecatedConfigDuplicatesConfigClass()
            throws Exception
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigDuplicatesConfigClass.class);
        verifyMetaData(metadata, DeprecatedConfigDuplicatesConfigClass.class, true, false, false, null);
        Errors errors = metadata.getErrors();
        verifyErrors(errors, "[" + findMethod(metadata.getConfigClass(), "getValue").toGenericString() + "]");
    }

    private void verifyErrors(Errors errors, String expectedMessage)
    {
        if (errors.getErrors().size() > 1) {
            System.out.println(errors.getErrors());
        }
        Assert.assertEquals(errors.getErrors().size(), 1);
        Message message = errors.getErrors().get(0);
        Assertions.assertContains(message.getMessage(), expectedMessage);
    }

    private void verifyMetaData(ConfigurationMetadata<?> metadata, Class<?> configClass, boolean hasConstructor, boolean hasGetter, boolean hasSetter, String description)
            throws Exception
    {
         verifyMetaData(metadata, configClass, "value", ImmutableList.<String>of(), hasConstructor, hasGetter, hasSetter, description);
    }

    private void verifyMetaData(ConfigurationMetadata<?> metadata, Class<?> configClass, String propertyName, ImmutableList<String> deprecatedNames, boolean hasConstructor, boolean hasGetter, boolean hasSetter, String description)
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
            Assert.assertEquals(attribute.getDeprecatedNames(), deprecatedNames);
            Assert.assertEquals(attribute.getName(), "Value");
            Assert.assertEquals(attribute.getPropertyName(), propertyName);
            if (hasGetter) {
                Assert.assertEquals(attribute.getGetter(), findMethod(configClass, "getValue"));
            }
            if (hasSetter) {
                Assert.assertEquals(attribute.getSetter(), findMethod(configClass, "setValue", String.class));
            }
            Assert.assertEquals(attribute.getDescription(), description);
        }
    }

    public static class GetterConfigClass
    {
        private String value;

        @Config("value")
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

    public static class GetterSubConfigClass extends GetterConfigClass
    {
    }

    public interface GetterInterface
    {
        @Config("value")
        @ConfigDescription("description")
        public String getValue();
    }

    public static class GetterInterfaceImpl implements GetterInterface
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

        @Config("value")
        public String getValue()
        {
            return value;
        }

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

        @Config("value")
        public String getValue()
        {
            return value;
        }

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

        @Config("value")
        public String getValue()
        {
            return value;
        }

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

        @Config("value")
        public String getValue()
        {
            return value;
        }

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

        @Config("")
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class NotPublicAttributeClass
    {
        private String value;

        @Config("value")
        String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class StaticAttributeClass
    {
        private static String value;

        @Config("value")
        public static String getValue()
        {
            return value;
        }

        public static void setValue(String v)
        {
            value = v;
        }
    }

    public static class GetterWithParameterClass
    {
        private String value;

        @Config("value")
        public String getValue(String foo)
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class GetterNoReturnClass
    {
        @Config("value")
        public void getValue()
        {
        }

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

        @Config("value")
        public String getValue()
        {
            return value;
        }

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

        @Config("value")
        public String getValue()
        {
            return value;
        }

        private void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class IsMethodWithParameterClass
    {
        private boolean value;

        @Config("value")
        public boolean isValue(boolean foo)
        {
            return value;
        }

        public void setValue(boolean value)
        {
            this.value = value;
        }
    }

    public static class IsMethodNoReturnClass
    {
        @Config("value")
        public void isValue()
        {
        }

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

        @Config("value")
        public boolean isValue()
        {
            return value;
        }

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

        @Config("value")
        public boolean isValue()
        {
            return value;
        }

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

    public static class CurrentAndDeprecatedConfigOnGetterClass
    {
        private String value;

        @Config("value")
        @DeprecatedConfig("deprecatedValue")
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

    public static class CurrentAndDeprecatedConfigOnSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @Config("value")
        @DeprecatedConfig("deprecatedValue")
        @ConfigDescription("description")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class DeprecatedConfigOnGetterClass
    {
        private String value;

        @DeprecatedConfig("deprecatedValue")
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class DeprecatedConfigOnSetterClass
    {
        private String value;

        public String getValue()
        {
            return value;
        }

        @DeprecatedConfig("deprecatedValue")
        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class MultipleDeprecatedConfigClass
    {
        private String value;

        @Config("value")
        @DeprecatedConfig({"deprecated1", "deprecated2"})
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

    public static class EmptyStringDeprecatedConfigClass
    {
        private String value;

        @DeprecatedConfig("")
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class EmptyArrayDeprecatedConfigClass
    {
        private String value;

        @DeprecatedConfig({})
        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }
    }

    public static class DeprecatedConfigDuplicatesConfigClass
    {
        private String value;

        @Config("value")
        @DeprecatedConfig("value")
        public String getValue()
        {
            return value;
        }

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
