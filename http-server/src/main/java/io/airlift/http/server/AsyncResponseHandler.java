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
package io.airlift.http.server;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.units.Duration;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.Response.status;

@Beta
public class AsyncResponseHandler
{
    private final AsyncResponse asyncResponse;
    private final WeakReference<Future<?>> futureResponseReference;

    private AsyncResponseHandler(AsyncResponse asyncResponse, ListenableFuture<?> futureResponse)
    {
        this.asyncResponse = requireNonNull(asyncResponse, "asyncResponse is null");
        // the jaxrs implementation can hold on to the async timeout for a long time, and
        // the future can reference large expensive objects.  Since we are only interested
        // in canceling this future on a timeout, only hold a weak reference to the future
        this.futureResponseReference = new WeakReference<>(requireNonNull(futureResponse, "futureResponse is null"));
    }

    public static AsyncResponseHandler bindAsyncResponse(AsyncResponse asyncResponse, ListenableFuture<?> futureResponse, Executor httpResponseExecutor)
    {
        Futures.addCallback(futureResponse, toFutureCallback(asyncResponse), httpResponseExecutor);
        return new AsyncResponseHandler(asyncResponse, futureResponse);
    }

    public AsyncResponseHandler withTimeout(Duration timeout)
    {
        return withTimeout(timeout,
                status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("Timed out after waiting for " + timeout.convertToMostSuccinctTimeUnit())
                        .build());
    }

    public AsyncResponseHandler withTimeout(Duration timeout, Response timeoutResponse)
    {
        return withTimeout(timeout, () -> timeoutResponse);
    }

    public AsyncResponseHandler withTimeout(Duration timeout, Supplier<Response> timeoutResponse)
    {
        asyncResponse.setTimeoutHandler(asyncResponse -> {
            asyncResponse.resume(timeoutResponse.get());
            cancelFuture();
        });
        asyncResponse.setTimeout(timeout.toMillis(), MILLISECONDS);
        return this;
    }

    private void cancelFuture()
    {
        // Cancel the original future if it still exists
        Future<?> futureResponse = futureResponseReference.get();
        if (futureResponse != null) {
            try {
                // Do not interrupt the future if it is running. Jersey uses
                // the calling thread to write the response to the wire.
                futureResponse.cancel(false);
            }
            catch (Exception ignored) {
            }
        }
    }

    private static <T> FutureCallback<T> toFutureCallback(AsyncResponse asyncResponse)
    {
        return new FutureCallback<T>()
        {
            @Override
            public void onSuccess(T value)
            {
                checkArgument(!(value instanceof Response.ResponseBuilder), "Value is a ResponseBuilder. Did you forget to call build?");
                asyncResponse.resume(value);
            }

            @Override
            public void onFailure(Throwable t)
            {
                asyncResponse.resume(t);
            }
        };
    }
}
