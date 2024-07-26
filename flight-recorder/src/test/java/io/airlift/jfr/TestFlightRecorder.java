package io.airlift.jfr;

import io.airlift.bootstrap.Bootstrap;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.jfr.FlightRecorderBinder.flightRecorderBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFlightRecorder
{
    private static final String THREAD_NAME = "captureMe";

    @Test
    public void testCaptureThreadStart()
            throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RecordedEvent> eventRef = new AtomicReference<>();

        new Bootstrap(new FlightRecorderModule(), binder ->
                flightRecorderBinder(binder, "jdk.ThreadStart")
                    .withoutStackTrace()
                    .withThreshold(null) // 0 ns
                    .withPeriod(null) // 0 ns
                    .bindTo(recordedEvent -> {
                        if (recordedEvent.getThread().getJavaName().equalsIgnoreCase(THREAD_NAME)) {
                            eventRef.set(recordedEvent);
                            latch.countDown();
                        }
                    }))
                .initialize();

        // It will take a second while watchdog starts
        Thread.sleep(100);

        spawnThread();
        latch.await();

        assertThat(eventRef.get()).isNotNull();
        assertThat(eventRef.get().getEventType().getName()).isEqualTo("jdk.ThreadStart");
        assertThat(eventRef.get().getThread().getJavaName()).isEqualTo(THREAD_NAME);
    }

    private static void spawnThread()
    {
        Thread thread = new Thread(() -> {});
        thread.setName(THREAD_NAME);
        thread.start();
    }
}
