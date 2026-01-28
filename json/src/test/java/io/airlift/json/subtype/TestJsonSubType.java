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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonModule;
import io.airlift.json.JsonSubType;
import io.airlift.json.ObjectMapperProvider;
import io.airlift.json.subtype.Employee.Manager;
import io.airlift.json.subtype.Employee.Programmer;
import io.airlift.json.subtype.Part.Container;
import io.airlift.json.subtype.Part.Item;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.InvalidTypeIdException;

import static io.airlift.json.JsonSubTypeBinder.jsonSubTypeBinder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJsonSubType
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
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        internalTest(objectMapper);
    }

    @Test
    public void testAddBindingSpecifiedNames()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "specified")
                .add(Programmer.class, "foo")
                .add(Manager.class, "bar")
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        internalTest(objectMapper);
    }

    @Test
    public void testAddPermittedSubClassBindings()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .addPermittedSubClasses()
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        internalTest(objectMapper);
    }

    @Test
    public void testAddPermittedSubClassBindingsSpecifiedNames()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "bogus")
                .addPermittedSubClasses(clazz -> "xxx__" + clazz.getName() + "__XXX")
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        internalTest(objectMapper);
    }

    @Test
    public void testFailsWithoutSubClassBindings()
    {
        ObjectMapper objectMapper = new ObjectMapperProvider().get();

        assertThatThrownBy(() -> internalTest(objectMapper))
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    public void testFailsWhenMissingBindings()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .build();
        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        assertThatThrownBy(() -> internalTest(objectMapper))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    public void testBuildMultipleTypes()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .addPermittedSubClasses()
                .forBase(Part.class, "category")
                .addPermittedSubClasses()
                .build();

        Injector injector = Guice.createInjector(new JsonModule(), binder -> jsonSubTypeBinder(binder).bindJsonSubType(jsonSubType));
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        internalTest(objectMapper);

        JsonCodecFactory codecFactory = new JsonCodecFactory(() -> objectMapper);
        JsonCodec<Part> jsonCodec = codecFactory.jsonCodec(Part.class);

        String item1Json = objectMapper.writeValueAsString(item1);
        String item2Json = objectMapper.writeValueAsString(item2);
        String containerJson = objectMapper.writeValueAsString(container);

        for (int i = 0; i < 2; ++i) {
            Part deserializedItem1 = (i == 0) ? objectMapper.readValue(item1Json, Part.class) : jsonCodec.fromJson(item1Json);
            Part deserializedItem2 = (i == 0) ? objectMapper.readValue(item2Json, Part.class) : jsonCodec.fromJson(item2Json);
            Part deserializedContainer = (i == 0) ? objectMapper.readValue(containerJson, Part.class) : jsonCodec.fromJson(containerJson);

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
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();

        ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider().withJsonSubTypes(ImmutableSet.of(jsonSubType));

        internalTest(objectMapperProvider.get());
    }

    @Test
    public void testExpectedJson()
    {
        JsonSubType jsonSubType1 = JsonSubType.builder()
                .forBase(Employee.class, "type")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        ObjectMapper objectMapper1 = new ObjectMapperProvider().withJsonSubTypes(ImmutableSet.of(jsonSubType1)).get();
        String programmer1Json = objectMapper1.writeValueAsString(programmer1);
        String manager1Json = objectMapper1.writeValueAsString(manager1);

        JsonSubType jsonSubType2 = JsonSubType.builder()
                .forBase(Employee.class, "category")
                .add(Programmer.class)
                .add(Manager.class)
                .build();
        ObjectMapper objectMapper2 = new ObjectMapperProvider().withJsonSubTypes(ImmutableSet.of(jsonSubType2)).get();
        String programmer2Json = objectMapper2.writeValueAsString(programmer1);
        String manager2Json = objectMapper2.writeValueAsString(manager1);

        assertThat(programmer1Json).isEqualTo("{\"name\":\"Joe\",\"type\":\"Programmer\"}");
        assertThat(manager1Json).isEqualTo("{\"name\":\"Jane\",\"reports\":[{\"name\":\"Joe\",\"type\":\"Programmer\"},{\"name\":\"Rachel\",\"type\":\"Programmer\"}],\"type\":\"Manager\"}");

        assertThat(programmer2Json).isEqualTo("{\"name\":\"Joe\",\"category\":\"Programmer\"}");
        assertThat(manager2Json).isEqualTo("{\"name\":\"Jane\",\"reports\":[{\"name\":\"Joe\",\"category\":\"Programmer\"},{\"name\":\"Rachel\",\"category\":\"Programmer\"}],\"category\":\"Manager\"}");
    }

    private static void internalTest(ObjectMapper objectMapper)
    {
        internalTest(objectMapper, false);
        internalTest(objectMapper, true);
    }

    private static void internalTest(ObjectMapper objectMapper, boolean writeWithCodec)
    {
        JsonCodecFactory codecFactory = new JsonCodecFactory(() -> objectMapper);
        JsonCodec<Employee> jsonCodec = codecFactory.jsonCodec(Employee.class);

        String programmer1Json = writeWithCodec ? jsonCodec.toJson(programmer1) : objectMapper.writeValueAsString(programmer1);
        String programmer2Json = writeWithCodec ? jsonCodec.toJson(programmer2) : objectMapper.writeValueAsString(programmer2);
        String programmer3Json = writeWithCodec ? jsonCodec.toJson(programmer3) : objectMapper.writeValueAsString(programmer3);
        String manager1Json = writeWithCodec ? jsonCodec.toJson(manager1) : objectMapper.writeValueAsString(manager1);
        String manager2Json = writeWithCodec ? jsonCodec.toJson(manager2) : objectMapper.writeValueAsString(manager2);

        for (int i = 0; i < 2; ++i) {
            Employee deserializedProgrammer1 = (i == 0) ? objectMapper.readValue(programmer1Json, Employee.class) : jsonCodec.fromJson(programmer1Json);
            Employee deserializedProgrammer2 = (i == 0) ? objectMapper.readValue(programmer2Json, Employee.class) : jsonCodec.fromJson(programmer2Json);
            Employee deserializedProgrammer3 = (i == 0) ? objectMapper.readValue(programmer3Json, Employee.class) : jsonCodec.fromJson(programmer3Json);
            Employee deserializedManager1 = (i == 0) ? objectMapper.readValue(manager1Json, Employee.class) : jsonCodec.fromJson(manager1Json);
            Employee deserializedManager2 = (i == 0) ? objectMapper.readValue(manager2Json, Employee.class) : jsonCodec.fromJson(manager2Json);

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
