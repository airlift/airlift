/*
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
package io.airlift.json.subtype;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.jackson.JacksonSubType;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.airlift.json.JsonModule;
import io.airlift.json.subtype.Employee.Manager;
import io.airlift.json.subtype.Employee.Programmer;
import io.airlift.json.subtype.Part.Container;
import io.airlift.json.subtype.Part.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.airlift.jackson.JacksonSubTypeBinder.jacksonSubTypeBinder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJacksonSubType
{
    private static final Employee programmer1 = new Programmer("Joe");
    private static final Employee programmer2 = new Programmer("Rachel");
    private static final Employee programmer3 = new Programmer("Chris");
    private static final Employee manager1 = new Manager("Jane", ImmutableList.of(programmer1, programmer2));
    private static final Employee manager2 = new Manager("Horace", ImmutableList.of(programmer3, manager1));

    private static final Part item1 = new Item("one");
    private static final Part item2 = new Item("two");
    private static final Part container = new Container(ImmutableList.of(item1, item2));

    @Test
    public void testAddBinding()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        internalTest(jsonMapper);
    }

    @Test
    public void testAddBindingSpecifiedNames()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "specified")
                .add(Programmer.class, "foo")
                .add(Manager.class, "bar")
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        internalTest(jsonMapper);
    }

    @Test
    public void testAddPermittedSubClassBindings()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .addPermittedSubClasses()
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        internalTest(jsonMapper);
    }

    @Test
    public void testAddPermittedSubClassBindingsSpecifiedNames()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "bogus")
                .addPermittedSubClasses(clazz -> "xxx__" + clazz.getName() + "__XXX")
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        internalTest(jsonMapper);
    }

    @Test
    public void testFailsWithoutSubClassBindings()
    {
        JsonMapper jsonMapper = new JsonMapperProvider().get();

        assertThatThrownBy(() -> internalTest(jsonMapper))
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    public void testFailsWhenMissingBindings()
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        assertThatThrownBy(() -> internalTest(jsonMapper))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    public void testBuildMultipleTypes()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .addPermittedSubClasses()
                .forBase(Part.class, "category")
                .addPermittedSubClasses()
                .build();

        Injector injector = Guice.createInjector(new JsonModule(), binder -> jacksonSubTypeBinder(binder).bindJacksonSubType(jacksonSubType));
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        internalTest(jsonMapper);

        JsonCodecFactory codecFactory = new JsonCodecFactory(jsonMapper);
        JsonCodec<Part> jsonCodec = codecFactory.jsonCodec(Part.class);

        String item1Json = jsonMapper.writeValueAsString(item1);
        String item2Json = jsonMapper.writeValueAsString(item2);
        String containerJson = jsonMapper.writeValueAsString(container);

        for (int i = 0; i < 2; ++i) {
            Part deserializedItem1 = (i == 0) ? jsonMapper.readValue(item1Json, Part.class) : jsonCodec.fromJson(item1Json);
            Part deserializedItem2 = (i == 0) ? jsonMapper.readValue(item2Json, Part.class) : jsonCodec.fromJson(item2Json);
            Part deserializedContainer = (i == 0) ? jsonMapper.readValue(containerJson, Part.class) : jsonCodec.fromJson(containerJson);

            assertThat(deserializedItem1).isInstanceOf(Item.class);
            assertThat(deserializedItem2).isInstanceOf(Item.class);
            assertThat(deserializedContainer).isInstanceOf(Container.class);

            assertThat(deserializedItem1).isEqualTo(item1);
            assertThat(deserializedItem2).isEqualTo(item2);
            assertThat(deserializedContainer).isEqualTo(container);
        }
    }

    @Test
    public void testStandalone()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();

        JsonMapperProvider jsonMapperProvider = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType));

        internalTest(jsonMapperProvider.get());
    }

    @Test
    public void testExpectedJson()
            throws Exception
    {
        JacksonSubType jacksonSubType1 = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        JsonMapper jsonMapper1 = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType1)).get();
        String programmer1Json = jsonMapper1.writeValueAsString(programmer1);
        String manager1Json = jsonMapper1.writeValueAsString(manager1);

        JacksonSubType jacksonSubType2 = JacksonSubType.builder()
                .forBase(Employee.class, "category")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        JsonMapper jsonMapper2 = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType2)).get();
        String programmer2Json = jsonMapper2.writeValueAsString(programmer1);
        String manager2Json = jsonMapper2.writeValueAsString(manager1);

        assertThat(programmer1Json).isEqualTo("{\"name\":\"Joe\",\"type\":\"Programmer\"}");
        assertThat(manager1Json).isEqualTo("{\"name\":\"Jane\",\"reports\":[{\"name\":\"Joe\",\"type\":\"Programmer\"},{\"name\":\"Rachel\",\"type\":\"Programmer\"}],\"type\":\"Manager\"}");

        assertThat(programmer2Json).isEqualTo("{\"name\":\"Joe\",\"category\":\"Programmer\"}");
        assertThat(manager2Json).isEqualTo("{\"name\":\"Jane\",\"reports\":[{\"name\":\"Joe\",\"category\":\"Programmer\"},{\"name\":\"Rachel\",\"category\":\"Programmer\"}],\"category\":\"Manager\"}");
    }

    @Test
    public void testAnnotatedSubtypeHasOneDiscriminator()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(AnnotatedValue.class, "type")
                .add(FirstValue.class, "first")
                .add(SecondValue.class, "second")
                .build();
        JsonMapper jsonMapper = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType)).get();

        for (AnnotatedValue value : ImmutableList.of(new FirstValue("first"), new SecondValue("second"))) {
            String expectedJson = "{\"value\":\"" + value.value() + "\",\"type\":\"" + value.value() + "\"}";
            for (String json : ImmutableList.of(
                    jsonMapper.writeValueAsString(value),
                    jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(value),
                    jsonMapper.writerFor(value.getClass()).writeValueAsString(value))) {
                assertThat(json).isEqualTo(expectedJson);
                assertThat((Object) jsonMapper.readerFor(AnnotatedValue.class)
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readValue(json)).isEqualTo(value);
                assertThat((Object) jsonMapper.readerFor(value.getClass())
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readValue(json)).isEqualTo(value);
            }
        }
        assertThatThrownBy(() -> jsonMapper.readValue("{\"type\":\"unknown\",\"value\":\"first\"}", AnnotatedValue.class))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    public void testAnnotatedSubtypeHasOneDiscriminatorWithOtherRegistries()
            throws Exception
    {
        JacksonSubType annotatedRegistry = JacksonSubType.builder()
                .forBase(AnnotatedValue.class, "type")
                .add(FirstValue.class, "first")
                .add(SecondValue.class, "second")
                .build();
        JacksonSubType employeeRegistry = JacksonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();

        // the answer must not depend on which registry's introspector is installed last (and thus consulted first)
        for (List<JacksonSubType> order : ImmutableList.of(
                ImmutableList.of(annotatedRegistry, employeeRegistry),
                ImmutableList.of(employeeRegistry, annotatedRegistry))) {
            // like JsonMapperProvider, tolerate the visible discriminator when the subtype declares no such property
            JsonMapper.Builder builder = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            order.forEach(registry -> registry.modules().forEach(builder::addModule));
            JsonMapper jsonMapper = builder.build();

            AnnotatedValue value = new FirstValue("first");
            String expectedJson = "{\"value\":\"first\",\"type\":\"first\"}";
            for (String json : ImmutableList.of(
                    jsonMapper.writeValueAsString(value),
                    jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(value),
                    jsonMapper.writerFor(FirstValue.class).writeValueAsString(value))) {
                assertThat(json).isEqualTo(expectedJson);
                assertThat((Object) jsonMapper.readerFor(AnnotatedValue.class)
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readValue(json)).isEqualTo(value);
            }

            // the unannotated registry keeps working: one discriminator, and subtypes still deserialize directly
            Programmer programmer = new Programmer("Joe");
            String programmerJson = jsonMapper.writeValueAsString(programmer);
            assertThat(programmerJson).isEqualTo("{\"name\":\"Joe\",\"type\":\"Programmer\"}");
            assertThat(jsonMapper.writerFor(Employee.class).writeValueAsString(programmer)).isEqualTo(programmerJson);
            assertThat((Object) jsonMapper.readerFor(Employee.class)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(programmerJson)).isEqualTo(programmer);
            assertThat(jsonMapper.readValue("{\"name\":\"Joe\"}", Programmer.class)).isEqualTo(programmer);
        }
    }

    @Test
    public void testSubtypeKeepsItsOwnTypeInfo()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(AnnotatedValue.class, "type")
                .add(FirstValue.class, "first")
                .add(SecondValue.class, "second")
                .add(OwnKindValue.class, "own")
                .add(InheritedKindValue.class, "inherited")
                .build();
        JsonMapper jsonMapper = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType)).get();

        OwnKindValue value = new OwnKindValue("own");
        // through the base, the registry discriminator applies; on its own, the subtype's declaration is not replaced
        assertThat(jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(value)).isEqualTo("{\"value\":\"own\",\"type\":\"own\"}");
        String ownJson = jsonMapper.writerFor(OwnKindValue.class).writeValueAsString(value);
        assertThat(ownJson).isEqualTo("{\"kind\":\"TestJacksonSubType$OwnKindValue\",\"value\":\"own\",\"type\":\"own\"}");
        assertThat((Object) jsonMapper.readerFor(OwnKindValue.class).readValue(ownJson)).isEqualTo(value);

        // a declaration on a type between the subtype and the base belongs to the subtype as well
        InheritedKindValue inherited = new InheritedKindValue("inherited");
        assertThat(jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(inherited)).isEqualTo("{\"value\":\"inherited\",\"type\":\"inherited\"}");
        String inheritedJson = jsonMapper.writerFor(InheritedKindValue.class).writeValueAsString(inherited);
        assertThat(inheritedJson).isEqualTo("{\"kind\":\"TestJacksonSubType$InheritedKindValue\",\"value\":\"inherited\",\"type\":\"inherited\"}");
        assertThat((Object) jsonMapper.readerFor(InheritedKindValue.class).readValue(inheritedJson)).isEqualTo(inherited);
    }

    @Test
    public void testSubtypeKeepsSideInterfaceTypeInfo()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(AnnotatedValue.class, "type")
                .add(FirstValue.class, "first")
                .add(SecondValue.class, "second")
                .add(SideKindValue.class, "side")
                .build();
        JsonMapper jsonMapper = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType)).get();

        // a declaration on an interface outside the registry hierarchy is the subtype's own as well
        SideKindValue value = new SideKindValue("side");
        assertThat(jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(value)).isEqualTo("{\"value\":\"side\",\"type\":\"side\"}");
        String json = jsonMapper.writerFor(SideKindValue.class).writeValueAsString(value);
        assertThat(json).isEqualTo("{\"kind\":\"TestJacksonSubType$SideKindValue\",\"value\":\"side\",\"type\":\"side\"}");
        assertThat((Object) jsonMapper.readerFor(SideKindValue.class).readValue(json)).isEqualTo(value);
    }

    @Test
    public void testSubtypeKeepsMixInTypeInfo()
            throws Exception
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(AnnotatedValue.class, "type")
                .add(FirstValue.class, "first")
                .add(SecondValue.class, "second")
                .add(MixInKindValue.class, "mixin")
                .build();
        JsonMapper jsonMapper = new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType)).get()
                .rebuild()
                .addMixIn(MixInKindValue.class, KindMixIn.class)
                .build();

        // a declaration supplied through a mix-in is the subtype's own, exactly like one written on the type
        MixInKindValue value = new MixInKindValue("mixin");
        assertThat(jsonMapper.writerFor(AnnotatedValue.class).writeValueAsString(value)).isEqualTo("{\"value\":\"mixin\",\"type\":\"mixin\"}");
        String json = jsonMapper.writerFor(MixInKindValue.class).writeValueAsString(value);
        assertThat(json).isEqualTo("{\"kind\":\"TestJacksonSubType$MixInKindValue\",\"value\":\"mixin\",\"type\":\"mixin\"}");
        assertThat((Object) jsonMapper.readerFor(MixInKindValue.class).readValue(json)).isEqualTo(value);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = FirstValue.class, name = "first"),
            @JsonSubTypes.Type(value = SecondValue.class, name = "second"),
    })
    public sealed interface AnnotatedValue
    {
        String value();
    }

    public record FirstValue(String value)
            implements AnnotatedValue {}

    public record SecondValue(String value)
            implements AnnotatedValue {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public record OwnKindValue(String value)
            implements AnnotatedValue {}

    public record MixInKindValue(String value)
            implements AnnotatedValue {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public interface SideKind {}

    public record SideKindValue(String value)
            implements SideKind, AnnotatedValue {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public interface KindMixIn {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public sealed interface KindValue
            extends AnnotatedValue {}

    public record InheritedKindValue(String value)
            implements KindValue {}

    private static void internalTest(JsonMapper jsonMapper)
            throws JsonProcessingException
    {
        internalTest(jsonMapper, false);
        internalTest(jsonMapper, true);
    }

    private static void internalTest(JsonMapper jsonMapper, boolean writeWithCodec)
            throws JsonProcessingException
    {
        JsonCodecFactory codecFactory = new JsonCodecFactory(jsonMapper);
        JsonCodec<Employee> jsonCodec = codecFactory.jsonCodec(Employee.class);

        String programmer1Json = writeWithCodec ? jsonCodec.toJson(programmer1) : jsonMapper.writeValueAsString(programmer1);
        String programmer2Json = writeWithCodec ? jsonCodec.toJson(programmer2) : jsonMapper.writeValueAsString(programmer2);
        String programmer3Json = writeWithCodec ? jsonCodec.toJson(programmer3) : jsonMapper.writeValueAsString(programmer3);
        String manager1Json = writeWithCodec ? jsonCodec.toJson(manager1) : jsonMapper.writeValueAsString(manager1);
        String manager2Json = writeWithCodec ? jsonCodec.toJson(manager2) : jsonMapper.writeValueAsString(manager2);

        for (int i = 0; i < 2; ++i) {
            Employee deserializedProgrammer1 = (i == 0) ? jsonMapper.readValue(programmer1Json, Employee.class) : jsonCodec.fromJson(programmer1Json);
            Employee deserializedProgrammer2 = (i == 0) ? jsonMapper.readValue(programmer2Json, Employee.class) : jsonCodec.fromJson(programmer2Json);
            Employee deserializedProgrammer3 = (i == 0) ? jsonMapper.readValue(programmer3Json, Employee.class) : jsonCodec.fromJson(programmer3Json);
            Employee deserializedManager1 = (i == 0) ? jsonMapper.readValue(manager1Json, Employee.class) : jsonCodec.fromJson(manager1Json);
            Employee deserializedManager2 = (i == 0) ? jsonMapper.readValue(manager2Json, Employee.class) : jsonCodec.fromJson(manager2Json);

            assertThat(deserializedProgrammer1).isInstanceOf(Programmer.class);
            assertThat(deserializedProgrammer2).isInstanceOf(Programmer.class);
            assertThat(deserializedProgrammer3).isInstanceOf(Programmer.class);
            assertThat(deserializedManager1).isInstanceOf(Manager.class);
            assertThat(deserializedManager2).isInstanceOf(Manager.class);

            assertThat(deserializedProgrammer1).isEqualTo(programmer1);
            assertThat(deserializedProgrammer2).isEqualTo(programmer2);
            assertThat(deserializedProgrammer3).isEqualTo(programmer3);
            assertThat(deserializedManager1).isEqualTo(manager1);
            assertThat(deserializedManager2).isEqualTo(manager2);
        }
    }
}
