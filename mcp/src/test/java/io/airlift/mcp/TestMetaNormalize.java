package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMetaNormalize
{
    @Test
    @SuppressWarnings({"OptionalAssignedToNull", "NullOptional"})
    public void testNull()
    {
        assertThat(normalize(null)).isEmpty();
    }

    @Test
    public void testEmptyOptional()
    {
        assertThat(normalize(Optional.empty())).isEmpty();
    }

    @Test
    public void testEmptyMap()
    {
        assertThat(normalize(Optional.of(ImmutableMap.of()))).contains(ImmutableMap.of());
    }

    @Test
    public void testEntriesArePreserved()
    {
        Map<String, Object> map = ImmutableMap.of("a", 1, "b", "two", "c", true);

        assertThat(normalize(Optional.of(map))).contains(map);
    }

    @Test
    public void testNullKeyIsFiltered()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put(null, 2);

        assertThat(normalize(Optional.of(map))).contains(ImmutableMap.of("a", 1));
    }

    @Test
    public void testNullValueIsFiltered()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", null);

        assertThat(normalize(Optional.of(map))).contains(ImmutableMap.of("a", 1));
    }

    @Test
    public void testAllEntriesFilteredYieldsPresentEmptyMap()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(null, null);
        map.put("b", null);

        assertThat(normalize(Optional.of(map))).contains(ImmutableMap.of());
    }

    @Test
    public void testIterationOrderIsPreserved()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z", 1);
        map.put("a", null);
        map.put("m", 3);

        assertThat(normalize(Optional.of(map)).orElseThrow())
                .containsExactly(Map.entry("z", 1), Map.entry("m", 3));
    }

    @Test
    public void testResultIsImmutable()
    {
        Map<String, Object> result = normalize(Optional.of(ImmutableMap.of("a", 1))).orElseThrow();

        assertThatThrownBy(() -> result.put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testResultIsDefensiveCopy()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);

        Map<String, Object> result = normalize(Optional.of(map)).orElseThrow();
        map.put("b", 2);

        assertThat(result).containsExactly(Map.entry("a", 1));
    }
}
