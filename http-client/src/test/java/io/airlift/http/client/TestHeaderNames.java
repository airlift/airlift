package io.airlift.http.client;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestHeaderNames
{
    @Test
    void containsAllGuavaHeaders()
    {
        assertThat(getAirliftHeaders())
                .containsAll(getGuavaHeaders());
    }

    private static Set<String> getGuavaHeaders()
    {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Field field : HttpHeaders.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                if (field.getAnnotationsByType(Deprecated.class).length == 0) {
                    builder.add(field.getName());
                }
            }
        }
        return builder.build();
    }

    private static Set<String> getAirliftHeaders()
    {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Field field : HeaderNames.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                builder.add(field.getName());
            }
        }
        return builder.build();
    }

    void main()
    {
        // Copy & paste into HeaderNames to regenerate
        for (String guavaHeader : new TreeSet<>(getGuavaHeaders())) {
            System.out.printf("public static final HeaderName %s = HeaderName.of(HttpHeaders.%s);%n", guavaHeader, guavaHeader);
        }
    }
}
