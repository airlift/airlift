package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Optional;

import static java.lang.invoke.MethodType.methodType;

public class RecordAutoDetectModule
        extends SimpleModule
{
    private static final Optional<MethodHandle> IS_RECORD;

    static {
        MethodHandle methodHandle;
        try {
            //noinspection JavaLangInvokeHandleSignature
            methodHandle = MethodHandles.lookup()
                    .findVirtual(Class.class, "isRecord", methodType(boolean.class));
        }
        catch (Exception e) {
            // ignore - assume we're not on Java 14+
            methodHandle = null;
        }
        IS_RECORD = Optional.ofNullable(methodHandle);
    }

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
            if (IS_RECORD.map(methodHandle -> isRecord(ac, methodHandle)).orElse(false)) {
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

    private static boolean isRecord(AnnotatedClass ac, MethodHandle methodHandle)
    {
        try {
            // TODO replace with real call to isRecord() when Airlift is on Java 14+
            return (boolean) methodHandle.invokeExact(ac.getRawType());
        }
        catch (Throwable e) {
            return false;   // should never get here
        }
    }
}
