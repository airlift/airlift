package io.airlift.http.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.http.client.HeaderNames.ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHeaderName
{
    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(HeaderName.of("foo"), HeaderName.of("FOO"), HeaderName.of("foo"))
                .addEquivalentGroup(HeaderName.of("bar"), HeaderName.of("BAR"), HeaderName.of("bar"))
                .check();
    }

    @Test
    public void testToString()
    {
        assertThat(HeaderName.of("FOO").toString()).isEqualTo("foo");
        assertThat(HeaderName.of("foo").toString()).isEqualTo("foo");
    }

    @Test
    public void testHeaderNameSerialization()
    {
        JsonCodec<HeaderName> codec = jsonCodec(HeaderName.class);

        String json = codec.toJson(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK);
        assertThat(json).isEqualTo("\"access-control-allow-private-network\"");
        assertThat(codec.fromJson(json)).isEqualTo(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK);
    }

    @Test
    public void testHeaderMapNameSerialization()
    {
        // Ensures backward compatibility with previous representation
        JsonCodec<Map<HeaderName, String>> codec = mapJsonCodec(HeaderName.class, String.class);
        JsonCodec<Map<String, String>> stringMapCodec = mapJsonCodec(String.class, String.class);

        String json = codec.toJson(ImmutableMap.of(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, "yes"));
        assertThat(json).isEqualToIgnoringWhitespace(
                """
                {
                  "access-control-allow-private-network" : "yes"
                }
                """);
        assertThat(codec.fromJson(json)).isEqualTo(ImmutableMap.of(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK, "yes"));
        assertThat(stringMapCodec.fromJson(json)).isEqualTo(ImmutableMap.of(ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK.toString(), "yes"));
    }
}
