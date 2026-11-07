package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.airlift.opentelemetry.SpanFilterRule.ChildrenMode.DROP;
import static io.airlift.opentelemetry.SpanFilterRule.ChildrenMode.REPARENT;
import static io.airlift.opentelemetry.SpanFilterRule.parse;
import static io.airlift.opentelemetry.SpanFilterRule.parseAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestSpanFilterRule
{
    @Test
    void testParseNameOnly()
    {
        assertThat(parse("process")).isEqualTo(new SpanFilterRule(Optional.empty(), "process", REPARENT));
    }

    @Test
    void testParseScopeAndChildren()
    {
        assertThat(parse("trino=>GET /v1/task/*/results/*;children=drop"))
                .isEqualTo(new SpanFilterRule(Optional.of("trino"), "GET /v1/task/*/results/*", DROP));
        assertThat(parse("trino=>process;children=reparent"))
                .isEqualTo(new SpanFilterRule(Optional.of("trino"), "process", REPARENT));
    }

    @Test
    void testWhitespaceAndCase()
    {
        assertThat(parse("  trino  =>  process ; children = DROP "))
                .isEqualTo(new SpanFilterRule(Optional.of("trino"), "process", DROP));
    }

    @Test
    void testParseAll()
    {
        assertThat(parseAll(ImmutableList.of("process", "x=>y;children=drop")))
                .containsExactly(
                        new SpanFilterRule(Optional.empty(), "process", REPARENT),
                        new SpanFilterRule(Optional.of("x"), "y", DROP));
        assertThat(parseAll(ImmutableList.of())).isEmpty();
    }

    @Test
    void testRejectsInvalidEntries()
    {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty span name");
        assertThatThrownBy(() -> parse("trino=>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty span name");
        assertThatThrownBy(() -> parse("=>process"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty scope");
        assertThatThrownBy(() -> parse("process;children=keep"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid children mode 'keep'");
        assertThatThrownBy(() -> parse("process;child=drop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option 'child=drop'");
        assertThatThrownBy(() -> parse("process;children"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option 'children'");
        assertThatThrownBy(() -> parse("process;"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option ''");
        assertThatThrownBy(() -> parse("process;children=drop;children=reparent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate option 'children'");
    }

    @Test
    void testMatchesScope()
    {
        assertThat(parse("process").matchesScope("anything")).isTrue();
        assertThat(parse("trino=>process").matchesScope("trino")).isTrue();
        assertThat(parse("trino=>process").matchesScope("trino.catalog.hive")).isFalse();
        assertThat(parse("trino.catalog.*=>process").matchesScope("trino.catalog.hive")).isTrue();
    }

    @Test
    void testGlobMatching()
    {
        assertThat(parse("process").matchesName("process")).isTrue();
        assertThat(parse("process").matchesName("processor")).isFalse();
        assertThat(parse("proc*").matchesName("process")).isTrue();
        assertThat(parse("*cess").matchesName("process")).isTrue();
        assertThat(parse("p*c*s").matchesName("process")).isTrue();
        assertThat(parse("*").matchesName("")).isTrue();
        assertThat(parse("GET /v1/task/*/results/*").matchesName("GET /v1/task/q1.0.0.0/results/0/12")).isTrue();
        assertThat(parse("GET /v1/task/*/results/*").matchesName("GET /v1/task/q1.0.0.0/status")).isFalse();
        assertThat(parse("*/acknowledge").matchesName("GET /v1/task/t/results/0/1/acknowledge")).isTrue();
    }

    @Test
    void testGlobIsLiteralExceptStar()
    {
        assertThat(parse("Metadata.listTables").matchesName("MetadataXlistTables")).isFalse();
        assertThat(parse("a(b)?").matchesName("a(b)?")).isTrue();
        assertThat(parse("GET /v1/{id}").matchesName("GET /v1/{id}")).isTrue();
    }

    @Test
    void testOverlappingAnchors()
    {
        assertThat(parse("a*a").matchesName("a")).isFalse();
        assertThat(parse("a*a").matchesName("aa")).isTrue();
        assertThat(parse("ab*bc").matchesName("abc")).isFalse();
    }
}
