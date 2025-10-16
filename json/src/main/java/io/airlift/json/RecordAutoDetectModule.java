package io.airlift.json;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Set;
import java.util.stream.Stream;

public class RecordAutoDetectModule extends SimpleModule {
    @Deprecated(forRemoval = true)
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface LegacyRecordIntrospection {}

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new Introspector());
    }

    private static class Introspector extends AnnotationIntrospector {
        private static final VisibilityChecker.Std RECORD_VISIBILITY_CHECKER = VisibilityChecker.Std.defaultInstance()
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withFieldVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.DEFAULT);

        @Override
        public VisibilityChecker<?> findAutoDetectVisibility(AnnotatedClass ac, VisibilityChecker<?> checker) {
            if (ac.getRawType().isRecord()) {
                JsonAutoDetect overrideAnnotation = ac.getRawType().getAnnotation(JsonAutoDetect.class);
                if (overrideAnnotation != null) {
                    return VisibilityChecker.Std.construct(JsonAutoDetect.Value.from(overrideAnnotation));
                }
                if (ac.getRawType().isAnnotationPresent(LegacyRecordIntrospection.class)) {
                    return RECORD_VISIBILITY_CHECKER;
                }
                return new RecordVisibilityChecker(ac.getRawType().asSubclass(Record.class));
            }
            return checker;
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }
    }

    private static class RecordVisibilityChecker implements VisibilityChecker<RecordVisibilityChecker> {
        private final Set<String> componentNames;

        public RecordVisibilityChecker(Class<? extends Record> recordClass) {
            componentNames = Stream.of(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(toImmutableSet());
        }

        @Override
        public RecordVisibilityChecker with(JsonAutoDetect annotation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withOverrides(JsonAutoDetect.Value value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker with(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withVisibility(
                PropertyAccessor propertyAccessor, JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withGetterVisibility(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withIsGetterVisibility(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withSetterVisibility(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withCreatorVisibility(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordVisibilityChecker withFieldVisibility(JsonAutoDetect.Visibility visibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGetterVisible(Method method) {
            if (!Modifier.isPublic(method.getModifiers())) {
                return false;
            }
            return componentNames.contains(method.getName());
        }

        @Override
        public boolean isGetterVisible(AnnotatedMethod annotatedMethod) {
            return isGetterVisible(annotatedMethod.getAnnotated());
        }

        @Override
        public boolean isIsGetterVisible(Method method) {
            return false;
        }

        @Override
        public boolean isIsGetterVisible(AnnotatedMethod annotatedMethod) {
            return false;
        }

        @Override
        public boolean isSetterVisible(Method method) {
            return false;
        }

        @Override
        public boolean isSetterVisible(AnnotatedMethod annotatedMethod) {
            return false;
        }

        @Override
        public boolean isCreatorVisible(Member member) {
            return true;
        }

        @Override
        public boolean isCreatorVisible(AnnotatedMember annotatedMember) {
            return true;
        }

        @Override
        public boolean isFieldVisible(Field field) {
            return false;
        }

        @Override
        public boolean isFieldVisible(AnnotatedField field) {
            return false;
        }
    }
}
