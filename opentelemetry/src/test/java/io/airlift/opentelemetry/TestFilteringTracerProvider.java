package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.opentelemetry.SpanFilterRule.parseAll;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFilteringTracerProvider
{
    private InMemorySpanExporter exporter;
    private SdkTracerProvider sdkTracerProvider;

    @BeforeEach
    void setUp()
    {
        exporter = InMemorySpanExporter.create();
        sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    @AfterEach
    void tearDown()
    {
        sdkTracerProvider.close();
    }

    private Tracer tracer(String scope, String... rules)
    {
        return new FilteringTracerProvider(sdkTracerProvider, parseAll(ImmutableList.copyOf(rules))).get(scope);
    }

    private List<String> exportedNames()
    {
        return exporter.getFinishedSpanItems().stream().map(SpanData::getName).toList();
    }

    private SpanData exported(String name)
    {
        return exporter.getFinishedSpanItems().stream().filter(span -> span.getName().equals(name)).collect(onlyElement());
    }

    @Test
    void testReparentAttachesChildrenToGrandparent()
    {
        Tracer tracer = tracer("trino", "trino=>process");
        Span split = tracer.spanBuilder("split").startSpan();

        Span process = tracer.spanBuilder("process")
                .setParent(Context.current().with(split))
                .setAttribute("leak", "yes")
                .startSpan();
        try (Scope _ = process.makeCurrent()) {
            tracer.spanBuilder("Input.readTail").startSpan().end();
        }
        process.setAttribute("leak2", "yes");
        process.end();

        assertThat(exportedNames()).containsExactly("Input.readTail");

        split.end();

        assertThat(exportedNames()).containsExactly("Input.readTail", "split");
        SpanData child = exported("Input.readTail");
        SpanData parent = exported("split");
        assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(parent.getAttributes().isEmpty()).isTrue();
        assertThat(process.getSpanContext()).isEqualTo(split.getSpanContext());
    }

    @Test
    void testReparentUsesCurrentContextWhenParentNotSet()
    {
        Tracer tracer = tracer("trino", "process");
        Span split = tracer.spanBuilder("split").startSpan();
        try (Scope _ = split.makeCurrent()) {
            Span process = tracer.spanBuilder("process").startSpan();
            try (Scope _ = process.makeCurrent()) {
                tracer.spanBuilder("child").startSpan().end();
            }
            process.end();
        }
        split.end();

        assertThat(exportedNames()).containsExactly("child", "split");
        assertThat(exported("child").getParentSpanId()).isEqualTo(exported("split").getSpanId());
    }

    @Test
    void testDropRemovesSubtree()
    {
        Tracer tracer = tracer("trino", "process;children=drop");
        Span split = tracer.spanBuilder("split").startSpan();
        try (Scope _ = split.makeCurrent()) {
            Span process = tracer.spanBuilder("process").startSpan();
            try (Scope _ = process.makeCurrent()) {
                tracer.spanBuilder("child").startSpan().end();
            }
            process.end();

            assertThat(process.getSpanContext().getTraceId()).isEqualTo(split.getSpanContext().getTraceId());
            assertThat(process.getSpanContext().getSpanId()).isNotEqualTo(split.getSpanContext().getSpanId());
            assertThat(process.getSpanContext().isSampled()).isFalse();

            tracer.spanBuilder("sibling").startSpan().end();
        }
        split.end();

        assertThat(exportedNames()).containsExactly("sibling", "split");
    }

    @Test
    void testReparentOfRootBehavesAsDrop()
    {
        Tracer tracer = tracer("trino", "process");
        Span process = tracer.spanBuilder("process").startSpan();
        try (Scope _ = process.makeCurrent()) {
            tracer.spanBuilder("child").startSpan().end();
        }
        process.end();

        assertThat(process.getSpanContext().isValid()).isTrue();
        assertThat(process.getSpanContext().isSampled()).isFalse();
        assertThat(exportedNames()).isEmpty();
    }

    @Test
    void testSetNoParentIgnoresCurrentSpan()
    {
        Tracer tracer = tracer("trino", "process");
        Span split = tracer.spanBuilder("split").startSpan();
        try (Scope _ = split.makeCurrent()) {
            Span process = tracer.spanBuilder("process").setNoParent().startSpan();
            try (Scope _ = process.makeCurrent()) {
                tracer.spanBuilder("child").startSpan().end();
            }
            process.end();
        }
        split.end();

        assertThat(exportedNames()).containsExactly("split");
    }

    @Test
    void testReparentUnderUnsampledParent()
    {
        Tracer tracer = tracer("trino", "process");
        SpanContext unsampled = SpanContext.create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getDefault(), TraceState.getDefault());

        Span process = tracer.spanBuilder("process")
                .setParent(Context.root().with(Span.wrap(unsampled)))
                .startSpan();
        try (Scope _ = process.makeCurrent()) {
            tracer.spanBuilder("child").startSpan().end();
        }
        process.end();

        assertThat(process.getSpanContext()).isEqualTo(unsampled);
        assertThat(exportedNames()).isEmpty();
    }

    @Test
    void testScopeRuleIgnoresOtherScopes()
    {
        tracer("trino.catalog.hive", "trino=>process").spanBuilder("process").startSpan().end();
        tracer("trino", "trino.catalog.*=>process").spanBuilder("process").startSpan().end();

        assertThat(exportedNames()).containsExactly("process", "process");
    }

    @Test
    void testUnmatchedScopeReturnsDelegate()
    {
        FilteringTracerProvider provider = new FilteringTracerProvider(sdkTracerProvider, parseAll(ImmutableList.of("trino=>process")));

        assertThat(provider.get("other")).isSameAs(sdkTracerProvider.get("other"));
        assertThat(provider.get("trino")).isInstanceOf(FilteringTracer.class);
    }

    @Test
    void testFirstMatchWins()
    {
        Tracer tracer = tracer("trino", "proc*;children=drop", "process");
        Span split = tracer.spanBuilder("split").startSpan();
        Span process = tracer.spanBuilder("process").setParent(Context.current().with(split)).startSpan();
        try (Scope _ = process.makeCurrent()) {
            tracer.spanBuilder("child").startSpan().end();
        }
        process.end();
        split.end();

        assertThat(exportedNames()).containsExactly("split");
    }

    @Test
    void testTracerBuilderAndVersionedGetAreFiltered()
    {
        FilteringTracerProvider provider = new FilteringTracerProvider(sdkTracerProvider, parseAll(ImmutableList.of("trino=>process")));

        provider.tracerBuilder("trino").setInstrumentationVersion("1").build().spanBuilder("process").startSpan().end();
        provider.get("trino", "1").spanBuilder("process").startSpan().end();
        provider.get("trino").spanBuilder("kept").startSpan().end();

        assertThat(exportedNames()).containsExactly("kept");
    }

    @Test
    void testReparentedSpanReportsParentRecordingState()
    {
        Tracer tracer = tracer("trino", "process");
        Span split = tracer.spanBuilder("split").startSpan();
        Span process = tracer.spanBuilder("process").setParent(Context.current().with(split)).startSpan();
        assertThat(process.isRecording()).isTrue();
        process.end();
        split.end();

        SpanContext unsampled = SpanContext.create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getDefault(), TraceState.getDefault());
        Span unsampledProcess = tracer.spanBuilder("process").setParent(Context.root().with(Span.wrap(unsampled))).startSpan();
        assertThat(unsampledProcess.isRecording()).isFalse();
    }

    @Test
    void testRecordingGatedChildSurvivesReparent()
    {
        Tracer tracer = tracer("trino", "planner");
        Span query = tracer.spanBuilder("query").startSpan();
        Span planner = tracer.spanBuilder("planner").setParent(Context.current().with(query)).startSpan();
        try (Scope _ = planner.makeCurrent()) {
            if (Span.current().isRecording()) {
                tracer.spanBuilder("optimize").startSpan().end();
            }
        }
        planner.end();
        query.end();

        assertThat(exportedNames()).containsExactly("optimize", "query");
        assertThat(exported("optimize").getParentSpanId()).isEqualTo(exported("query").getSpanId());
    }
}
