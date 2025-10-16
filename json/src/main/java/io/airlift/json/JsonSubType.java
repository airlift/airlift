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
package io.airlift.json;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Value;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.VirtualAnnotatedMember;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.AttributePropertyWriter;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

public class JsonSubType {
    private final Set<Module> modules;

    public static JsonSubType.Builder builder() {
        return new Builder();
    }

    public interface SubTypeSubBuilder<B> {
        default <T extends B> SubTypeSubBuilder<B> add(Class<T> subClass) {
            return add(subClass, subClass.getSimpleName());
        }

        <T extends B> SubTypeSubBuilder<B> add(Class<T> subClass, String propertyValue);

        default SubTypeSubBuilder<B> addPermittedSubClasses() {
            return addPermittedSubClasses(Class::getSimpleName);
        }

        SubTypeSubBuilder<B> addPermittedSubClasses(Function<Class<?>, String> propertyValueProvider);

        <T> SubTypeSubBuilder<T> forBase(Class<T> baseClass, String propertyName);

        JsonSubType build();
    }

    public static class Builder {
        private final ImmutableSet.Builder<Module> modules = ImmutableSet.builder();

        private Builder() {}

        public <B> SubTypeSubBuilder<B> forBase(Class<B> baseClass, String propertyName) {
            ImmutableMap.Builder<String, Class<?>> subClassBuilder = ImmutableMap.builder();

            Supplier<Map<String, Class<?>>> supplier = Suppliers.memoize(subClassBuilder::build);

            TypeAddingModule module = new TypeAddingModule(baseClass, propertyName, supplier);
            module.addDeserializer(baseClass, new Deserializer<>(propertyName, supplier));
            modules.add(module);

            return new SubTypeSubBuilder<>() {
                @Override
                public <T extends B> SubTypeSubBuilder<B> add(Class<T> subClass, String propertyValue) {
                    subClassBuilder.put(propertyValue, subClass);
                    return this;
                }

                @SuppressWarnings("unchecked")
                @Override
                public SubTypeSubBuilder<B> addPermittedSubClasses(Function<Class<?>, String> propertyValueProvider) {
                    Arrays.stream(baseClass.getPermittedSubclasses())
                            .forEach(clazz -> add((Class<? extends B>) clazz, propertyValueProvider.apply(clazz)));
                    return this;
                }

                @Override
                public <T> SubTypeSubBuilder<T> forBase(Class<T> baseClass, String propertyName) {
                    return Builder.this.forBase(baseClass, propertyName);
                }

                @Override
                public JsonSubType build() {
                    return new JsonSubType(modules.build());
                }
            };
        }
    }

    public Set<Module> modules() {
        return modules;
    }

    private JsonSubType(Set<Module> modules) {
        this.modules = modules;
    }

    private static class TypeAddingModule extends SimpleModule {
        private static final AtomicInteger MODULE_ID_SEQ = new AtomicInteger(1);

        private final Class<?> baseClass;
        private final String propertyName;
        private final Supplier<Map<String, Class<?>>> subClassPropertyValues;

        private TypeAddingModule(
                Class<?> baseClass, String propertyName, Supplier<Map<String, Class<?>>> subClassPropertyValues) {
            super("TypeAddingModule-" + MODULE_ID_SEQ.getAndIncrement(), new Version(1, 0, 0, null, null, null));

            this.baseClass = requireNonNull(baseClass, "baseClass is null");
            this.propertyName = requireNonNull(propertyName, "propertyName is null");
            this.subClassPropertyValues = requireNonNull(subClassPropertyValues, "subClassPropertyValues is null");
        }

        @Override
        public void setupModule(SetupContext context) {
            super.setupModule(context);

            Map<Class<?>, String> invertedSubClassMap = subClassPropertyValues.get().entrySet().stream()
                    .collect(toImmutableMap(Map.Entry::getValue, Map.Entry::getKey));

            context.insertAnnotationIntrospector(
                    new TypeAddingIntrospector(baseClass, propertyName, invertedSubClassMap));
        }
    }

    private static class TypeAddingIntrospector extends JacksonAnnotationIntrospector {
        private final Class<?> baseClass;
        private final String propertyName;
        private final Map<Class<?>, String> subClassPropertyValues;
        private final List<NamedType> namedTypes;
        private final Value value;

        private TypeAddingIntrospector(
                Class<?> baseClass, String propertyName, Map<Class<?>, String> subClassPropertyValues) {
            this.baseClass = requireNonNull(baseClass, "baseClass is null");
            this.propertyName = requireNonNull(propertyName, "propertyName is null");
            this.subClassPropertyValues = ImmutableMap.copyOf(subClassPropertyValues);

            namedTypes = subClassPropertyValues.entrySet().stream()
                    .map(entry -> new NamedType(entry.getKey(), entry.getValue()))
                    .collect(toImmutableList());

            value = Value.construct(NAME, EXISTING_PROPERTY, propertyName, null, true, true);
        }

        @Override
        public Value findPolymorphicTypeInfo(MapperConfig<?> config, Annotated annotated) {
            if (annotated.getRawType().equals(baseClass)) {
                return value;
            }
            return super.findPolymorphicTypeInfo(config, annotated);
        }

        @Override
        public List<NamedType> findSubtypes(Annotated annotated) {
            if (annotated.getRawType().equals(baseClass)) {
                return namedTypes;
            }
            return super.findSubtypes(annotated);
        }

        @Override
        public void findAndAddVirtualProperties(
                MapperConfig<?> config, AnnotatedClass ac, List<BeanPropertyWriter> properties) {
            String propertyValue = subClassPropertyValues.get(ac.getRawType());
            if (propertyValue == null) {
                return;
            }

            // mostly copied from JacksonAnnotationIntrospector._constructVirtualProperty()

            JavaType stringType = config.constructType(String.class);

            AnnotatedMember member = new VirtualAnnotatedMember(ac, ac.getRawType(), propertyName, stringType);
            SimpleBeanPropertyDefinition propDef =
                    SimpleBeanPropertyDefinition.construct(config, member, PropertyName.construct(propertyName));
            AttributePropertyWriter propertyWriter =
                    new AttributePropertyWriter(propertyName, propDef, null, stringType) {
                        @Override
                        protected Object value(Object bean, JsonGenerator jgen, SerializerProvider prov) {
                            return propertyValue;
                        }
                    };

            properties.add(propertyWriter);
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }
    }

    private static class Deserializer<B> extends JsonDeserializer<B> {
        private final String propertyName;
        private final Supplier<Map<String, Class<?>>> subClassPropertyValues;

        public Deserializer(String propertyName, Supplier<Map<String, Class<?>>> subClassPropertyValues) {
            this.propertyName = requireNonNull(propertyName, "propertyName is null");
            this.subClassPropertyValues = subClassPropertyValues;
        }

        @SuppressWarnings("unchecked")
        @Override
        public B deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            TreeNode treeNode = p.readValueAsTree();
            TreeNode propertyNode = treeNode.get(propertyName);

            String propertyValue = ((propertyNode != null) && propertyNode.isValueNode())
                    ? ((ValueNode) propertyNode).textValue()
                    : null;
            if (propertyValue == null) {
                throw new IllegalArgumentException(
                        "JSON expected to have a property named \"%s\". Double check the addBinding() or addPermittedSubClassBindings()"
                                .formatted(propertyName));
            }

            Class<?> subClass = subClassPropertyValues.get().get(propertyValue);
            if (subClass == null) {
                throw new IllegalArgumentException(
                        "No binding was made for property name \"%s\" and value \"%s\". Double check the addBinding() or addPermittedSubClassBindings()."
                                .formatted(propertyName, propertyValue));
            }

            return (B) p.getCodec().treeToValue(treeNode, subClass);
        }
    }
}
