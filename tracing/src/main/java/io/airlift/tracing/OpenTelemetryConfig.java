package io.airlift.tracing;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class OpenTelemetryConfig
{
    private String endpoint = "http://localhost:4317";
    private int maxBatchSize = 512; // BatchSpanProcessorBuilder.DEFAULT_MAX_EXPORT_BATCH_SIZE
    private int maxQueueSize = 2048; // BatchSpanProcessorBuilder. DEFAULT_MAX_QUEUE_SIZE

    @NotNull
    @Pattern(regexp = "^(http|https)://.*$", message = "must start with http:// or https://")
    public String getEndpoint()
    {
        return endpoint;
    }

    @Config("tracing.exporter.endpoint")
    public OpenTelemetryConfig setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }

    @Min(128)
    @Max(65_536)
    public int getMaxBatchSize()
    {
        return maxBatchSize;
    }

    @Config("tracing.exporter.max-batch-size")
    public OpenTelemetryConfig setMaxBatchSize(int maxBatchSize)
    {
        this.maxBatchSize = maxBatchSize;
        return this;
    }

    @Min(128)
    @Max(65_536)
    public int getMaxQueueSize()
    {
        return maxQueueSize;
    }

    @Config("tracing.exporter.max-queue-size")
    public OpenTelemetryConfig setMaxQueueSize(int maxQueueSize)
    {
        this.maxQueueSize = maxQueueSize;
        return this;
    }
}
