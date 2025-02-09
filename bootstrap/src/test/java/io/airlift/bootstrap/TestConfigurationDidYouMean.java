package io.airlift.bootstrap;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static io.airlift.bootstrap.ConfigurationDidYouMean.didYouMean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestConfigurationDidYouMean
{
    @Test
    void testFuzzyMatching()
    {
        assertThatThrownBy(() -> didYouMean("foo", ImmutableSet.of("foo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key 'foo' is already present in the [foo] properties");

        assertThat(didYouMean("fo", ImmutableSet.of("foo", "bar", "baz")))
                .containsExactly("foo");

        assertThat(didYouMean("fo", ImmutableSet.of("foo", "fooz", "foox")))
                .containsExactly("foo");

        assertThat(didYouMean("foo.enabled", ImmutableSet.of("foo.disabled", "bar.disabled", "bar.enabled")))
                .containsExactly("foo.disabled");

        assertThat(didYouMean("delta.s3.enabled", ImmutableSet.of("delta.fs.s3.enabled", "delta.fs.gcs.enabled", "delta.fs.azure.enabled", "delta.max-http-requests", "delta.max-write-size")))
                .containsExactly("delta.fs.s3.enabled", "delta.fs.gcs.enabled", "delta.fs.azure.enabled");
    }
}
