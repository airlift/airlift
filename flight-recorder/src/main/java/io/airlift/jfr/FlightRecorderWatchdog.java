package io.airlift.jfr;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jdk.jfr.EventSettings;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static io.airlift.concurrent.Threads.threadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class FlightRecorderWatchdog
{
    private static final Logger log = Logger.get(FlightRecorderWatchdog.class);

    private final RecordingStream stream;
    private final ExecutorService executor = newSingleThreadExecutor(threadsNamed("flight-recorder-%d"));
    private final Map<FlightRecorderEvent, Consumer<RecordedEvent>> eventConsumers;

    @Inject
    FlightRecorderWatchdog(Map<FlightRecorderEvent, Consumer<RecordedEvent>> eventConsumers)
    {
        this.stream = new RecordingStream();
        this.eventConsumers = requireNonNull(eventConsumers, "eventConsumer is null");
    }

    private void enableEvent(RecordingStream stream, FlightRecorderEvent event, Consumer<RecordedEvent> consumer)
    {
        EventSettings settings = stream.enable(event.eventName());
        for (Map.Entry<String, String> property : event.properties().entrySet()) {
            settings.with(property.getKey(), property.getValue());
        }

        log.info("Listening for FlightRecorder event %s with %s", event.eventName(), event.properties());
        stream.onEvent(event.eventName(), consumer);
    }

    @PostConstruct
    public void start()
    {
        executor.submit(() -> {
            for (FlightRecorderEvent event : eventConsumers.keySet()) {
                enableEvent(stream, event, eventConsumers.get(event));
            }

            log.info("Starting FlightRecorder watchdog");
            stream.start();
        });
    }

    @PreDestroy
    public void stop()
    {
        stream.stop();
        log.info("Stopped FlightRecorder watchdog");
    }
}
