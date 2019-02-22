package io.airlift.retry;

import org.assertj.core.api.Condition;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static io.airlift.retry.RetryDriver.retry;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;

public class TestRetryDriver
{
    @Test
    public void testNoRetry()
            throws Exception
    {
        Integer actual = retry()
                .run("retry", () -> 1);
        assertEquals(actual, (Integer) 1);
    }

    @Test
    public void testRetry()
            throws Exception
    {
        AtomicLong counter = new AtomicLong(2);
        Integer actual = retry()
                .run("retry", () -> {
                    if (counter.decrementAndGet() > 0) {
                        throw new Exception();
                    }
                    return 1;
                });
        assertEquals(counter.intValue(), 0);
        assertEquals(actual, (Integer) 1);
    }

    @Test
    public void testRetryAndStopOnIOException()
    {
        AtomicLong counter = new AtomicLong(2);
        assertThatThrownBy(() -> retry()
                .stopOn(IOException.class)
                .run("retry", () -> {
                    counter.decrementAndGet();
                    if (counter.longValue() > 0) {
                        throw new Exception(Long.toString(counter.get()));
                    }
                    if (counter.longValue() == 0) {
                        throw new IOException();
                    }
                    return 1;
                }))
                .isInstanceOf(IOException.class)
                .is(suppressingInstanceOf(Exception.class));
        assertEquals(counter.intValue(), 0);
    }

    @Test
    public void testRetryAndStopOnExceptionWithMessage()
    {
        AtomicLong counter = new AtomicLong(3);
        assertThatThrownBy(() -> retry()
                .stopOn(exceptionWithMessageEqualsTo("1"))
                .run("retry", () -> {
                    if (counter.decrementAndGet() > 0) {
                        throw new Exception(Long.toString(counter.get()));
                    }
                    return 1;
                }))
                .isInstanceOf(Exception.class)
                .hasMessage("1")
                .is(suppressing(exceptionWithMessageEqualsTo("2")));
        assertEquals(counter.intValue(), 1);
    }

    private <T extends Throwable> Condition<T> suppressingInstanceOf(Class<? extends Throwable> clazz)
    {
        return suppressing(clazz::isInstance);
    }

    private <T extends Throwable> Condition<T> suppressing(Predicate<? super Throwable> predicate)
    {
        return new Condition<>(
                exception -> hasSuppressedExceptionThatMatches(exception, predicate),
                "Suppressing Exception condition");
    }

    private <T extends Throwable> boolean hasSuppressedExceptionThatMatches(T exception, Predicate<? super Throwable> suppressedExceptionPredicate)
    {
        return asList(exception.getSuppressed()).stream()
                .anyMatch(suppressedExceptionPredicate);
    }

    private <T extends Throwable> Predicate<T> exceptionWithMessageEqualsTo(String expectedMessage)
    {
        return exception -> exception.getMessage().equals(expectedMessage);
    }
}
