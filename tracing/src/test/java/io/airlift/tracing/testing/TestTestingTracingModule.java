/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.tracing.testing;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTestingTracingModule
{
    @Test
    public void testInMemorySpanExporter()
    {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        Bootstrap app = new Bootstrap(
                new JsonModule(),
                new TestingNodeModule(),
                new TestingTracingModule("foo-bar-service-name", "test-version", Optional.of(SimpleSpanProcessor.create(spanExporter))));
        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(Map.of())
                .initialize();
        Tracer tracer = injector.getInstance(Tracer.class);

        tracer.spanBuilder("foo-span").startSpan()
                .setAttribute("foo-attribute", "some-value")
                .end();

        List<SpanData> recordedSpans = spanExporter.getFinishedSpanItems();
        assertThat(recordedSpans).as("all recorder spans")
                .hasSize(1);
        SpanData recorderSpan = recordedSpans.stream().collect(onlyElement());
        assertThat(recorderSpan.getName()).as("span name")
                .isEqualTo("foo-span");

        assertThat(recorderSpan.getAttributes().asMap()).as("span attributes")
                .isEqualTo(Map.of(AttributeKey.stringKey("foo-attribute"), "some-value"));
        assertThat(recorderSpan.getResource().getAttributes().get(ResourceAttributes.SERVICE_NAME)).as("span resource service name")
                .isEqualTo("foo-bar-service-name");
        assertThat(recorderSpan.getResource().getAttributes().get(ResourceAttributes.SERVICE_VERSION)).as("span resource service version")
                .isEqualTo("test-version");
    }
}
