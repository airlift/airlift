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

import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;
import io.airlift.configuration.ConfigurationMetadata.AttributeMetadata;
import jakarta.validation.constraints.Min;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class TestConfigurationMetadata {
    @Test
    public void testEquivalence() {
        equivalenceTester()
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class))
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class))
                .check();

        equivalenceTester()
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class)
                                .getAttributes()
                                .get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class)
                                .getAttributes()
                                .get("Value"))
                .addEquivalentGroup(
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class)
                                .getAttributes()
                                .get("Value"),
                        ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class)
                                .getAttributes()
                                .get("Value"))
                .check();
    }

    @Test
    public void testSetterConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterConfigClass.class, "description", false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSetterSensitiveClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterSensitiveClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterSensitiveClass.class, "description", true, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSetterHiddenClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterHiddenClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterHiddenClass.class, "description", false, true, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSubSetterConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterSubConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterSubConfigClass.class, "description", false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSetterInterfaceImpl() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(SetterInterfaceImpl.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, SetterInterfaceImpl.class, "description", false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSetterNoGetterConfigClassThrows() {
        try {
            ConfigurationMetadata.getValidConfigurationMetadata(SetterNoGetterConfigClass.class);
            fail("Expected ConfigurationException");
        } catch (ConfigurationException e) {
            assertThat(e.getErrorMessages())
                    .containsExactly(
                            new Message(
                                    "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$SetterNoGetterConfigClass.setValue(java.lang.String)]. The following methods are unusable: [public void io.airlift.configuration.TestConfigurationMetadata$SetterNoGetterConfigClass.setValue(java.lang.String)]"));
        }
    }

    @Test
    public void testNull() {
        assertThatThrownBy(() -> ConfigurationMetadata.getConfigurationMetadata(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testAbstractClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(AbstractClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, AbstractClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(new Message(
                        "Config class [io.airlift.configuration.TestConfigurationMetadata$AbstractClass] is abstract"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetValidAbstractClass() {
        try {
            ConfigurationMetadata.getValidConfigurationMetadata(AbstractClass.class);
            fail("Expected ConfigurationException");
        } catch (ConfigurationException e) {
            assertThat(e.getErrorMessages())
                    .containsExactly(
                            new Message(
                                    "Config class [io.airlift.configuration.TestConfigurationMetadata$AbstractClass] is abstract"));
        }
    }

    @Test
    public void testNotPublicClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotPublicClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NotPublicClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Config class [io.airlift.configuration.TestConfigurationMetadata$NotPublicClass] is not public"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testNotPublicConstructorClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(NotPublicConstructorClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NotPublicConstructorClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Constructor [io.airlift.configuration.TestConfigurationMetadata$NotPublicConstructorClass()] is not public"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testNoNoArgConstructorClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(NoNoArgConstructorClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, NoNoArgConstructorClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Configuration class [io.airlift.configuration.TestConfigurationMetadata$NoNoArgConstructorClass] does not have a public no-arg constructor"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testNoConfigMethodsClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NoConfigMethodsClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, NoConfigMethodsClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Configuration class [io.airlift.configuration.TestConfigurationMetadata$NoConfigMethodsClass] does not have any @Config annotations"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterAndSetterAnnotatedClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(GetterAndSetterAnnotatedClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, GetterAndSetterAnnotatedClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$GetterAndSetterAnnotatedClass.getValue()] is not a valid setter (e.g. setFoo) for configuration annotation"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testEmptyPropertyNameClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(EmptyPropertyNameClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, EmptyPropertyNameClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [public void io.airlift.configuration.TestConfigurationMetadata$EmptyPropertyNameClass.setValue(java.lang.String)] annotation has an empty value"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testNotPublicAttributeClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(NotPublicAttributeClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, NotPublicAttributeClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$NotPublicAttributeClass.setValue(java.lang.String)]. The following methods are unusable: [java.lang.String io.airlift.configuration.TestConfigurationMetadata$NotPublicAttributeClass.getValue()][public void io.airlift.configuration.TestConfigurationMetadata$NotPublicAttributeClass.setValue(java.lang.String)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testStaticAttributeClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(StaticAttributeClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, StaticAttributeClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [public static void io.airlift.configuration.TestConfigurationMetadata$StaticAttributeClass.setValue(java.lang.String)] is static"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterWithParameterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(GetterWithParameterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, GetterWithParameterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$GetterWithParameterClass.setValue(java.lang.String)]. The following methods are unusable: [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$GetterWithParameterClass.getValue(java.lang.String)][public void io.airlift.configuration.TestConfigurationMetadata$GetterWithParameterClass.setValue(java.lang.String)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterNoReturnClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoReturnClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, GetterNoReturnClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$GetterNoReturnClass.setValue(java.lang.String)]. The following methods are unusable: [public void io.airlift.configuration.TestConfigurationMetadata$GetterNoReturnClass.getValue()][public void io.airlift.configuration.TestConfigurationMetadata$GetterNoReturnClass.setValue(java.lang.String)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterNoSetterClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(GetterNoSetterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, GetterNoSetterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$GetterNoSetterClass.getValue()] is not a valid setter (e.g. setFoo) for configuration annotation"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterMultipleSettersClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(GetterMultipleSettersClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, GetterMultipleSettersClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testGetterPrivateSetterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(GetterPrivateSetterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, GetterPrivateSetterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [private void io.airlift.configuration.TestConfigurationMetadata$GetterPrivateSetterClass.setValue(java.lang.String)] is not public"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testIsMethodWithParameterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(IsMethodWithParameterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, IsMethodWithParameterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$IsMethodWithParameterClass.setValue(boolean)]. The following methods are unusable: [public boolean io.airlift.configuration.TestConfigurationMetadata$IsMethodWithParameterClass.isValue(boolean)][public void io.airlift.configuration.TestConfigurationMetadata$IsMethodWithParameterClass.setValue(boolean)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testIsMethodNoReturnClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoReturnClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, IsMethodNoReturnClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "No getter for @Config method [public void io.airlift.configuration.TestConfigurationMetadata$IsMethodNoReturnClass.setValue(boolean)]. The following methods are unusable: [public void io.airlift.configuration.TestConfigurationMetadata$IsMethodNoReturnClass.isValue()][public void io.airlift.configuration.TestConfigurationMetadata$IsMethodNoReturnClass.setValue(boolean)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testIsMethodNoSetterClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(IsMethodNoSetterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, IsMethodNoSetterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public boolean io.airlift.configuration.TestConfigurationMetadata$IsMethodNoSetterClass.isValue()] is not a valid setter (e.g. setFoo) for configuration annotation"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testIsMethodMultipleSettersClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(IsMethodMultipleSettersClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, IsMethodMultipleSettersClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testIsMethodPrivateSetterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(IsMethodPrivateSetterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, IsMethodPrivateSetterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [private void io.airlift.configuration.TestConfigurationMetadata$IsMethodPrivateSetterClass.setValue(boolean)] is not public"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testSetterWithNoParameterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(SetterWithNoParameterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, SetterWithNoParameterClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Configuration setter method [public void io.airlift.configuration.TestConfigurationMetadata$SetterWithNoParameterClass.setValue()] does not have exactly one parameter"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testNotJavaBeanClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(NotJavaBeanClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, NotJavaBeanClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public void io.airlift.configuration.TestConfigurationMetadata$NotJavaBeanClass.putValue(java.lang.String)] is not a valid setter (e.g. setFoo) for configuration annotation"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testMultipleAnnotatedSettersClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(MultipleAnnotatedSettersClass.class);

        // Not validating metadata, since the actual setter it picks is not deterministic

        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Configuration class [io.airlift.configuration.TestConfigurationMetadata$MultipleAnnotatedSettersClass] Multiple methods are annotated for @Config attribute [Value]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testCurrentAndLegacyConfigOnGetterClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(CurrentAndLegacyConfigOnGetterClass.class);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$CurrentAndLegacyConfigOnGetterClass.getValue()] is not a valid setter (e.g. setFoo) for configuration annotation"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testCurrentAndLegacyConfigOnSetterClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(CurrentAndLegacyConfigOnSetterClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, CurrentAndLegacyConfigOnSetterClass.class, "description", false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testCurrentConfigWithReplacedByClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(CurrentConfigWithReplacedByClass.class);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [public void io.airlift.configuration.TestConfigurationMetadata$CurrentConfigWithReplacedByClass.setValue(java.lang.String)] has annotation claiming to be replaced by another property ('other-name')"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigOnGetterClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnGetterClass.class);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Method [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$LegacyConfigOnGetterClass.getValue()] is not a valid setter (e.g. setFoo) for configuration annotation"),
                        new Message(
                                "@LegacyConfig method [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$LegacyConfigOnGetterClass.getValue()] is not associated with any valid @Config attribute."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigOnSetterClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnSetterClass.class);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigOnSetterClass.setValue(java.lang.String)] is not associated with any valid @Config attribute."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigOnDeprecatedSetterClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnDeprecatedSetterClass.class);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigOnNonDeprecatedSetterClass() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigOnNonDeprecatedSetterClass.class);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings())
                .containsExactly(
                        new Message(
                                "Replaced @LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigOnNonDeprecatedSetterClass.setValue(int)] should be @Deprecated"));
    }

    @Test
    public void testMultipleLegacyConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(MultipleLegacyConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("legacy1", "legacy2", "value"));

        verifyMetaData(metadata, MultipleLegacyConfigClass.class, "description", false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testEmptyStringLegacyConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(EmptyStringLegacyConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, EmptyStringLegacyConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$EmptyStringLegacyConfigClass.setValue(java.lang.String)] annotation contains null or empty value"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testEmptyArrayLegacyConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(EmptyArrayLegacyConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, EmptyArrayLegacyConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$EmptyArrayLegacyConfigClass.setValue(java.lang.String)] annotation has an empty list"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testEmptyStringInArrayLegacyConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(EmptyStringInArrayLegacyConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, EmptyStringInArrayLegacyConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$EmptyStringInArrayLegacyConfigClass.setValue(java.lang.String)] annotation contains null or empty value"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigDuplicatesConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();

        verifyMetaData(metadata, LegacyConfigDuplicatesConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config property name 'value' appears in @LegacyConfig annotation for method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigDuplicatesConfigClass.setValue(java.lang.String)]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigDuplicatesConfigOnOtherMethodClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigOnOtherMethodClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, LegacyConfigDuplicatesConfigOnOtherMethodClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig property 'value' on method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigDuplicatesConfigOnOtherMethodClass.setValue(int)] is replaced by @Config property of same name on method [setValue]"),
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigDuplicatesConfigOnOtherMethodClass.setValue(int)] is not associated with any valid @Config attribute."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigDuplicatesConfigOnLinkedMethodClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigDuplicatesConfigOnLinkedMethodClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(
                metadata, LegacyConfigDuplicatesConfigOnLinkedMethodClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig property 'value' on method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigDuplicatesConfigOnLinkedMethodClass.setIntValue(int)] is replaced by @Config property of same name on method [setValue]"),
                        new Message(
                                "@LegacyConfig method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigDuplicatesConfigOnLinkedMethodClass.setIntValue(int)] is not associated with any valid @Config attribute."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDeprecatedConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDeprecatedConfigOnSetterOnlyClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnSetterOnlyClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigOnSetterOnlyClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Methods [public void io.airlift.configuration.TestConfigurationMetadata$DeprecatedConfigOnSetterOnlyClass.setDeprecated(java.lang.String)] and [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$DeprecatedConfigOnSetterOnlyClass.getDeprecated()] must be @Deprecated together"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDeprecatedConfigOnGetterOnlyClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DeprecatedConfigOnGetterOnlyClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));
        expectedAttributes.put("Deprecated", ImmutableSet.of("deprecated-value"));

        verifyMetaData(metadata, DeprecatedConfigOnGetterOnlyClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Methods [public void io.airlift.configuration.TestConfigurationMetadata$DeprecatedConfigOnGetterOnlyClass.setDeprecated(java.lang.String)] and [public java.lang.String io.airlift.configuration.TestConfigurationMetadata$DeprecatedConfigOnGetterOnlyClass.getDeprecated()] must be @Deprecated together"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getConfigurationMetadata(DefunctConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigEmptyArrayClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DefunctConfigEmptyArrayClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigEmptyArrayClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@DefunctConfig annotation on class [io.airlift.configuration.TestConfigurationMetadata$DefunctConfigEmptyArrayClass] is empty"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigEmptyStringClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DefunctConfigEmptyStringClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigEmptyStringClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@DefunctConfig annotation on class [io.airlift.configuration.TestConfigurationMetadata$DefunctConfigEmptyStringClass] contains empty values"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigInUseClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInUseClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DefunctConfigInUseClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config property 'value' on method [public void io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInUseClass.setValue(java.lang.String)] is defunct on class [class io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInUseClass]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigInLegacyUseClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInLegacyUseClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, DefunctConfigInLegacyUseClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig property 'replacedValue' on method [public void io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInLegacyUseClass.setValue(int)] is defunct on class [class io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInLegacyUseClass]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDefunctConfigInLinkedLegacyUseClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DefunctConfigInLinkedLegacyUseClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, DefunctConfigInLinkedLegacyUseClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@LegacyConfig property 'replacedValue' on method [public void io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInLinkedLegacyUseClass.setIntValue(int)] is defunct on class [class io.airlift.configuration.TestConfigurationMetadata$DefunctConfigInLinkedLegacyUseClass]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testDuplicateDefunctConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(DuplicateDefunctConfigClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value"));

        verifyMetaData(metadata, DuplicateDefunctConfigClass.class, null, false, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "Defunct property 'defunct' is listed more than once in @DefunctConfig for class [io.airlift.configuration.TestConfigurationMetadata$DuplicateDefunctConfigClass]"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testOnlyDefunctConfigClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(OnlyDefunctConfigClass.class);

        verifyMetaData(metadata, OnlyDefunctConfigClass.class, null, false, ImmutableMap.of());
        assertThat(metadata.getProblems().getErrors()).isEmpty();
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigSensitiveClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigSensitiveClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(metadata, LegacyConfigSensitiveClass.class, null, false /* don't care */, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@ConfigSecuritySensitive method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigSensitiveClass.setValue(int)] is not annotated with @Config."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testLegacyConfigHiddenClass() throws Exception {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(LegacyConfigHiddenClass.class);
        Map<String, Set<String>> expectedAttributes = new HashMap<>();
        expectedAttributes.put("Value", ImmutableSet.of("value", "replacedValue"));

        verifyMetaData(
                metadata, LegacyConfigHiddenClass.class, null, false, false /* don't care */, expectedAttributes);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@ConfigHidden method [public void io.airlift.configuration.TestConfigurationMetadata$LegacyConfigHiddenClass.setValue(int)] is not annotated with @Config."));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    @Test
    public void testMisplacedValidationAnnotation() {
        ConfigurationMetadata<?> metadata =
                ConfigurationMetadata.getConfigurationMetadata(MisplacedValidationAnnotationClass.class);
        assertThat(metadata.getProblems().getErrors())
                .containsExactly(
                        new Message(
                                "@Config method [public void io.airlift.configuration.TestConfigurationMetadata$MisplacedValidationAnnotationClass.setValue(int)] annotation @jakarta.validation.constraints.Min(message=\"{jakarta.validation.constraints.Min.message}\", payload={}, groups={}, value=10L) should be placed on a getter"));
        assertThat(metadata.getProblems().getWarnings()).isEmpty();
    }

    private void verifyMetaData(
            ConfigurationMetadata<?> metadata,
            Class<?> configClass,
            String description,
            boolean securitySensitive,
            Map<String, Set<String>> attributeProperties)
            throws Exception {
        verifyMetaData(metadata, configClass, description, securitySensitive, false, attributeProperties);
    }

    private void verifyMetaData(
            ConfigurationMetadata<?> metadata,
            Class<?> configClass,
            String description,
            boolean securitySensitive,
            boolean hidden,
            Map<String, Set<String>> attributeProperties)
            throws Exception {
        assertThat(metadata.getConfigClass()).isEqualTo(configClass);

        if (metadata.getConstructor() != null) {
            assertThat(metadata.getConstructor()).isEqualTo(configClass.getDeclaredConstructor());
        } else {
            try {
                configClass.getDeclaredConstructor();
                fail(String.format("Expected configClass [%s] not to have a constructor", configClass.getName()));
            } catch (NoSuchMethodException expected) {
            }
        }

        assertThat(metadata.getAttributes()).hasSameSizeAs(attributeProperties.keySet());

        for (String name : attributeProperties.keySet()) {
            AttributeMetadata attribute = metadata.getAttributes().get(name);
            assertThat(attribute.getConfigClass()).isEqualTo(configClass);
            Set<String> namesToTest = new HashSet<>();
            namesToTest.add(attribute.getInjectionPoint().getProperty());
            for (ConfigurationMetadata.InjectionPointMetaData legacyInjectionPoint :
                    attribute.getLegacyInjectionPoints()) {
                namesToTest.add(legacyInjectionPoint.getProperty());
            }
            assertThat(namesToTest).isEqualTo(attributeProperties.get(name));
            assertThat(attribute.getDescription()).isEqualTo(description);
            assertThat(attribute.isSecuritySensitive()).isEqualTo(securitySensitive);
            assertThat(attribute.isHidden()).isEqualTo(hidden);
        }
    }

    public static class SetterConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @ConfigDescription("description")
        public void setValue(String value) {
            this.value = value;
        }

        public void setValue(Object value) {
            this.value = String.valueOf(value);
        }
    }

    public static class SetterSubConfigClass extends SetterConfigClass {}

    public static class SetterSensitiveClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @ConfigSecuritySensitive
        @ConfigDescription("description")
        public void setValue(String value) {
            this.value = value;
        }

        public void setValue(Object value) {
            this.value = String.valueOf(value);
        }
    }

    public static class SetterHiddenClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @ConfigHidden
        @ConfigDescription("description")
        public void setValue(String value) {
            this.value = value;
        }

        public void setValue(Object value) {
            this.value = String.valueOf(value);
        }
    }

    public interface SetterInterface {
        @Config("value")
        @ConfigDescription("description")
        void setValue(String value);
    }

    public static class SetterInterfaceImpl implements SetterInterface {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class SetterNoGetterConfigClass {
        @Config("value")
        public void setValue(String value) {}
    }

    public abstract static class AbstractClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    static class NotPublicClass {
        private String value;

        public NotPublicClass() {}

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class NoNoArgConstructorClass {
        private String value;

        public NoNoArgConstructorClass(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class NotPublicConstructorClass {
        private String value;

        NotPublicConstructorClass() {}

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class NoConfigMethodsClass {}

    public static class GetterAndSetterAnnotatedClass {
        private String value;

        @Config("value")
        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class EmptyPropertyNameClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class NotPublicAttributeClass {
        private String value;

        String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class StaticAttributeClass {
        private static String value;

        public static String getValue() {
            return value;
        }

        @Config("value")
        public static void setValue(String v) {
            value = v;
        }
    }

    public static class GetterWithParameterClass {
        private String value;

        public String getValue(String foo) {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class GetterNoReturnClass {
        public void getValue() {}

        @Config("value")
        public void setValue(String value) {}
    }

    public static class GetterNoSetterClass {
        @Config("value")
        public String getValue() {
            return null;
        }
    }

    public static class GetterMultipleSettersClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        public void setValue(Object value) {}
    }

    public static class GetterPrivateSetterClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        private void setValue(String value) {
            this.value = value;
        }
    }

    public static class IsMethodWithParameterClass {
        private boolean value;

        public boolean isValue(boolean foo) {
            return value;
        }

        @Config("value")
        public void setValue(boolean value) {
            this.value = value;
        }
    }

    public static class IsMethodNoReturnClass {
        public void isValue() {}

        @Config("value")
        public void setValue(boolean value) {}
    }

    public static class IsMethodNoSetterClass {
        @Config("value")
        public boolean isValue() {
            return false;
        }
    }

    public static class IsMethodMultipleSettersClass {
        private boolean value;

        public boolean isValue() {
            return value;
        }

        @Config("value")
        public void setValue(boolean value) {
            this.value = value;
        }

        public void setValue(Object value) {}
    }

    public static class IsMethodPrivateSetterClass {
        private boolean value;

        public boolean isValue() {
            return value;
        }

        @Config("value")
        private void setValue(boolean value) {
            this.value = value;
        }
    }

    public static class SetterWithNoParameterClass {
        public String getValue() {
            return null;
        }

        @Config("value")
        public void setValue() {}
    }

    public static class NotJavaBeanClass {
        private String value;

        public String fetchValue() {
            return value;
        }

        @Config("value")
        public void putValue(String value) {
            this.value = value;
        }
    }

    public static class MultipleAnnotatedSettersClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Config("int-value")
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class CurrentAndLegacyConfigOnGetterClass {
        private String value;

        @Config("value")
        @LegacyConfig("replacedValue")
        @ConfigDescription("description")
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class CurrentAndLegacyConfigOnSetterClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig("replacedValue")
        @ConfigDescription("description")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class CurrentConfigWithReplacedByClass {
        private String value;

        public String getValue() {
            return value;
        }

        public String getValueByOtherName() {
            return value;
        }

        @Config("other-name")
        public void setValueByOtherName(String value) {
            this.value = value;
        }

        @Config("value")
        @LegacyConfig(value = "replacedValue", replacedBy = "other-name")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class LegacyConfigOnGetterClass {
        private String value;

        @LegacyConfig("replacedValue")
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class LegacyConfigOnSetterClass {
        private String value;

        public String getValue() {
            return value;
        }

        @LegacyConfig("replacedValue")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class LegacyConfigOnDeprecatedSetterClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class LegacyConfigOnNonDeprecatedSetterClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @LegacyConfig("replacedValue")
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class MultipleLegacyConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig({"legacy1", "legacy2"})
        @ConfigDescription("description")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class EmptyStringLegacyConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig("")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class EmptyArrayLegacyConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig({})
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class EmptyStringInArrayLegacyConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig({"foo", ""})
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class LegacyConfigDuplicatesConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        @LegacyConfig("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class LegacyConfigDuplicatesConfigOnOtherMethodClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("value")
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class LegacyConfigDuplicatesConfigOnLinkedMethodClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig(value = "value", replacedBy = "value")
        public void setIntValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class DeprecatedConfigClass {
        private String value;
        private String deprecated;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        public String getDeprecated() {
            return deprecated;
        }

        @Deprecated
        @Config("deprecated-value")
        public void setDeprecated(String deprecated) {
            this.deprecated = deprecated;
        }
    }

    public static class DeprecatedConfigOnSetterOnlyClass {
        private String value;
        private String deprecated;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        public String getDeprecated() {
            return deprecated;
        }

        @Deprecated
        @Config("deprecated-value")
        public void setDeprecated(String deprecated) {
            this.deprecated = deprecated;
        }
    }

    public static class DeprecatedConfigOnGetterOnlyClass {
        private String value;
        private String deprecated;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        public String getDeprecated() {
            return deprecated;
        }

        @Config("deprecated-value")
        public void setDeprecated(String deprecated) {
            this.deprecated = deprecated;
        }
    }

    @DefunctConfig({"defunct1", "defunct2"})
    public static class DefunctConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @DefunctConfig({})
    public static class DefunctConfigEmptyArrayClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @DefunctConfig({"defunct1", ""})
    public static class DefunctConfigEmptyStringClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @DefunctConfig("value")
    public static class DefunctConfigInUseClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @DefunctConfig("replacedValue")
    public static class DefunctConfigInLegacyUseClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    @DefunctConfig("replacedValue")
    public static class DefunctConfigInLinkedLegacyUseClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig(value = "replacedValue", replacedBy = "value")
        public void setIntValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    @DefunctConfig({"defunct", "defunct"})
    public static class DuplicateDefunctConfigClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @DefunctConfig("defunct")
    public static class OnlyDefunctConfigClass {}

    public static class LegacyConfigSensitiveClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        @ConfigSecuritySensitive
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class LegacyConfigHiddenClass {
        private String value;

        public String getValue() {
            return value;
        }

        @Config("value")
        public void setValue(String value) {
            this.value = value;
        }

        @Deprecated
        @LegacyConfig("replacedValue")
        @ConfigHidden
        public void setValue(int value) {
            this.value = Integer.toString(value);
        }
    }

    public static class MisplacedValidationAnnotationClass {
        private int value;

        public int getValue() {
            return value;
        }

        @Config("value")
        @Min(10)
        public void setValue(int value) {
            this.value = value;
        }
    }
}
