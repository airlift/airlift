package io.airlift.jaxrs;

import org.glassfish.jersey.servlet.spi.AsyncContextDelegate;
import org.glassfish.jersey.servlet.spi.AsyncContextDelegateProvider;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

public class ServletAsyncContextDelegateProvider
        implements AsyncContextDelegateProvider
{
    @Override
    public AsyncContextDelegate createDelegate(HttpServletRequest request, HttpServletResponse response)
    {
        return new ServletAsyncContextDelegate(request, response);
    }

    private static class ServletAsyncContextDelegate
            implements AsyncContextDelegate
    {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final AtomicReference<AsyncContext> asyncContext = new AtomicReference<>();

        private ServletAsyncContextDelegate(HttpServletRequest request, HttpServletResponse response)
        {
            this.request = request;
            this.response = response;
        }

        @Override
        public void suspend()
                throws IllegalStateException
        {
            // Suspend the servlet request
            AsyncContext asyncContext = request.startAsync(request, response);

            // Jersey has it's own private timeout system, so disable the servlet one
            asyncContext.setTimeout(-1);

            // store in an atomic because there are no memory visibility guarantee
            this.asyncContext.set(asyncContext);
        }

        @Override
        public void complete()
        {
            // we only want to complete the request once, so clear the atomic
            AsyncContext asyncContext = this.asyncContext.getAndSet(null);

            // ignore duplicate complete calls
            if (asyncContext == null) {
                return;
            }

            // complete the request
            asyncContext.complete();
        }
    }
}
