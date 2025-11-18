package io.airlift.http.client;

import io.airlift.json.JsonCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static java.util.Objects.requireNonNull;

public final class StreamingJsonBodyGenerator<T>
        implements BodyGenerator
{
    private static final Executor EXECUTOR = Executors.newThreadPerTaskExecutor(virtualThreadsNamed("streaming-json-body#v%s"));
    private final JsonCodec<T> codec;
    private final T instance;

    public static <T> StreamingJsonBodyGenerator<T> jsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        return new StreamingJsonBodyGenerator<>(jsonCodec, instance);
    }

    private StreamingJsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        requireNonNull(jsonCodec, "jsonCodec is null");
        requireNonNull(instance, "instance is null");
        this.codec = jsonCodec;
        this.instance = instance;
    }

    public InputStream source()
    {
        try {
            PipedOutputStream output = new PipedOutputStream();
            InputStream input = new PipedInputStream(output);

            EXECUTOR.execute(() -> {
                try (output) {
                    codec.toJsonStream(instance, output);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            return input;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String contentType()
    {
        return JSON_UTF_8.toString();
    }
}
