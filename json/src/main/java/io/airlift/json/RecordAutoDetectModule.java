package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.core.Version;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.VisibilityChecker;
import tools.jackson.databind.module.SimpleModule;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class RecordAutoDetectModule
        extends SimpleModule
{
    @Deprecated(forRemoval = true)
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface LegacyRecordIntrospection {}

    @Override
    public void setupModule(SetupContext context)
    {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new Introspector());
    }

    private static class Introspector
            extends AnnotationIntrospector
    {
        private static final VisibilityChecker RECORD_VISIBILITY_CHECKER = VisibilityChecker.defaultInstance()
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withFieldVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.DEFAULT);

        @Override
        public VisibilityChecker findAutoDetectVisibility(MapperConfig<?> mapperConfig, AnnotatedClass ac, VisibilityChecker checker)
        {
            if (ac.getRawType().isRecord()) {
                JsonAutoDetect overrideAnnotation = ac.getRawType().getAnnotation(JsonAutoDetect.class);
                if (overrideAnnotation != null) {
                    return VisibilityChecker.construct(JsonAutoDetect.Value.from(overrideAnnotation));
                }
                if (ac.getRawType().isAnnotationPresent(LegacyRecordIntrospection.class)) {
                    return RECORD_VISIBILITY_CHECKER;
                }
                return new RecordVisibilityChecker(ac.getRawType().asSubclass(Record.class));
            }
            return checker;
        }

        @Override
        public Version version()
        {
            return Version.unknownVersion();
        }
    }

    private static class RecordVisibilityChecker
            extends VisibilityChecker
    {
        private final Set<String> componentNames;

        public RecordVisibilityChecker(Class<? extends Record> recordClass)
        {
            super(JsonAutoDetect.Visibility.PUBLIC_ONLY);
            componentNames = Stream.of(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(toImmutableSet());
        }

        @Override
        public RecordVisibilityChecker withOverrides(JsonAutoDetect.Value value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker with(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withVisibility(PropertyAccessor propertyAccessor, JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withGetterVisibility(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withIsGetterVisibility(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withSetterVisibility(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withCreatorVisibility(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withFieldVisibility(JsonAutoDetect.Visibility visibility)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGetterVisible(AnnotatedMethod method)
        {
            if (!Modifier.isPublic(method.getModifiers())) {
                return false;
            }
            return componentNames.contains(method.getName());
        }

        @Override
        public boolean isIsGetterVisible(AnnotatedMethod annotatedMethod)
        {
            return false;
        }

        @Override
        public boolean isSetterVisible(AnnotatedMethod annotatedMethod)
        {
            return false;
        }

        @Override
        public boolean isCreatorVisible(AnnotatedMember annotatedMember)
        {
            return true;
        }

        @Override
        public boolean isFieldVisible(AnnotatedField field)
        {
            return false;
        }
    }
}
