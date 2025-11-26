package io.airlift.bootstrap;

import org.junit.jupiter.api.Test;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAutoCloseableCloser
{
    @Test
    public void testEmpty()
            throws Exception
    {
        AutoCloseableCloser closer = AutoCloseableCloser.create();
        closer.close();
    }

    @Test
    public void testRegisterAfterClose()
            throws Exception
    {
        AutoCloseableCloser closer = AutoCloseableCloser.create();
        closer.close();
        assertThatThrownBy(() -> closer.register(failingCloseable(new Exception("Expected failure"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Already closed")
                .hasSuppressedException(new Exception("Expected failure"));
    }

    @Test
    public void testAllClosed()
    {
        assertAllClosed(succeedingCloseable(), succeedingCloseable());
        assertAllClosed(failingCloseable(new RuntimeException()), failingCloseable(new RuntimeException()));
        assertAllClosed(failingCloseable(new Exception()), failingCloseable(new Exception()));
        assertAllClosed(failingCloseable(new Error()), failingCloseable(new Error()));
        assertAllClosed(failingCloseable(new Throwable()), failingCloseable(new Throwable()));
        assertAllClosed(failingCloseable(new Throwable()), failingCloseable(new Throwable()), failingCloseable(new Throwable()));
    }

    @Test
    public void testSuppressedException()
    {
        RuntimeException runtimeException = new RuntimeException();
        Exception exception = new Exception();
        Error error = new Error();

        AutoCloseableCloser closer = AutoCloseableCloser.create();
        // add twice to test self suppression handling
        closer.register(failingCloseable(error));
        closer.register(failingCloseable(error));
        closer.register(failingCloseable(exception));
        closer.register(failingCloseable(exception));
        closer.register(failingCloseable(runtimeException));
        closer.register(failingCloseable(runtimeException));

        assertThatThrownBy(closer::close)
                .isInstanceOfSatisfying(Exception.class, t -> {
                    assertThat(t).isSameAs(runtimeException);
                    assertThat(t.getSuppressed()[0]).isSameAs(exception);
                    assertThat(t.getSuppressed()[1]).isSameAs(exception);
                    assertThat(t.getSuppressed()[2]).isSameAs(error);
                    assertThat(t.getSuppressed()[3]).isSameAs(error);
                });
    }

    private static void assertAllClosed(TestAutoCloseable... closeables)
    {
        AutoCloseableCloser closer = AutoCloseableCloser.create();
        for (AutoCloseable closeable : closeables) {
            closer.register(closeable);
        }
        try {
            closer.close();
        }
        catch (Throwable _) {
        }
        for (TestAutoCloseable closeable : closeables) {
            assertThat(closeable.isClosed()).isTrue();
        }
    }

    private static TestAutoCloseable succeedingCloseable()
    {
        return new TestAutoCloseable(null);
    }

    private static TestAutoCloseable failingCloseable(Throwable t)
    {
        return new TestAutoCloseable(t);
    }

    private static class TestAutoCloseable
            implements AutoCloseable
    {
        private final Throwable failure;
        private boolean closed;

        private TestAutoCloseable(Throwable failure)
        {
            this.failure = failure;
        }

        public boolean isClosed()
        {
            return closed;
        }

        @Override
        public void close()
                throws Exception
        {
            closed = true;
            if (failure != null) {
                throwIfInstanceOf(failure, Exception.class);
                throwIfUnchecked(failure);
                // not possible
                throw new AssertionError(failure);
            }
        }
    }
}
