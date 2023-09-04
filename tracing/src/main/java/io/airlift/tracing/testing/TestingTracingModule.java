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

import com.google.inject.Binder;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.tracing.OpenTelemetryModule;
import io.airlift.tracing.TracingModule;
import io.airlift.tracing.TracingSupportModule;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class TestingTracingModule
        extends AbstractConfigurationAwareModule
{
    private final String serviceName;
    private final String serviceVersion;
    private final Optional<SpanProcessor> spanProcessor;

    public TestingTracingModule(String serviceName, String serviceVersion, Optional<SpanProcessor> spanProcessor)
    {
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
        this.serviceVersion = requireNonNull(serviceVersion, "serviceVersion is null");
        this.spanProcessor = requireNonNull(spanProcessor, "spanProcessor is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        if (spanProcessor.isPresent()) {
            install(new OpenTelemetryModule(serviceName, serviceVersion, spanProcessor));
            install(new TracingSupportModule(serviceName));
        }
        else {
            install(new TracingModule(serviceName, serviceVersion));
        }
    }
}
