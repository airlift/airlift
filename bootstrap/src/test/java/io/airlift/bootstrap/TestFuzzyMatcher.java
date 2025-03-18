package io.airlift.bootstrap;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static io.airlift.bootstrap.FuzzyMatcher.findSimilar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFuzzyMatcher
{
    @Test
    void testFuzzyMatching()
    {
        assertThatThrownBy(() -> findSimilar("foo", ImmutableSet.of("foo"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key 'foo' is already present in the [foo] candidates");

        assertThat(findSimilar("fo", ImmutableSet.of("foo", "bar", "baz"), 3))
                .containsExactly("foo");

        assertThat(findSimilar("fo", ImmutableSet.of("foo", "fooz", "foox"), 3))
                .containsExactly("foo", "fooz", "foox");

        assertThat(findSimilar("foo.enabled", ImmutableSet.of("foo.disabled", "bar.disabled", "bar.enabled"), 3))
                .containsExactly("foo.disabled");

        assertThat(findSimilar("delta.s3.enabled", ImmutableSet.of("delta.fs.s3.enabled", "delta.fs.gcs.enabled", "delta.fs.azure.enabled", "delta.max-http-requests", "delta.max-write-size"), 2))
                .containsExactly("delta.fs.s3.enabled", "delta.fs.gcs.enabled");
    }
}
