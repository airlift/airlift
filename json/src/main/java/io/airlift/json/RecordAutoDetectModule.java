package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class RecordAutoDetectModule
        extends SimpleModule
{
    @Override
    public void setupModule(SetupContext context)
    {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new Introspector());
    }

    private static class Introspector
            extends AnnotationIntrospector
    {
        private static final VisibilityChecker.Std RECORD_VISIBILITY_CHECKER = VisibilityChecker.Std.defaultInstance()
                .withGetterVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withFieldVisibility(JsonAutoDetect.Visibility.DEFAULT)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.DEFAULT);

        @Override
        public VisibilityChecker<?> findAutoDetectVisibility(AnnotatedClass ac, VisibilityChecker<?> checker)
        {
            if (ac.getRawType().isRecord()) {
                JsonAutoDetect overrideAnnotation = ac.getRawType().getAnnotation(JsonAutoDetect.class);
                if (overrideAnnotation != null) {
                    return VisibilityChecker.Std.construct(JsonAutoDetect.Value.from(overrideAnnotation));
                }
                return RECORD_VISIBILITY_CHECKER;
            }
            return checker;
        }

        @Override
        public Version version()
        {
            return Version.unknownVersion();
        }
    }
}
