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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.airlift.units.Duration;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.Response.status;

@Beta
public class AsyncResponseHandler
{
    private final AsyncResponse asyncResponse;
    private final WeakReference<CompletableFuture<?>> futureResponseReference;

    private AsyncResponseHandler(AsyncResponse asyncResponse, CompletableFuture<?> futureResponse)
    {
        this.asyncResponse = checkNotNull(asyncResponse, "asyncResponse is null");
        // the jaxrs implementation can hold on to the async timeout for a long time, and
        // the future can reference large expensive objects.  Since we are only interested
        // in canceling this future on a timeout, only hold a weak reference to the future
        this.futureResponseReference = new WeakReference<>(checkNotNull(futureResponse, "futureResponse is null"));
    }

    public static AsyncResponseHandler bindAsyncResponse(AsyncResponse asyncResponse, CompletableFuture<?> futureResponse, Executor httpResponseExecutor)
    {
        futureResponse.whenCompleteAsync(toCompletionCallback(asyncResponse), httpResponseExecutor);
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
        return withTimeout(timeout, Suppliers.ofInstance(timeoutResponse));
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
        CompletableFuture<?> futureResponse = futureResponseReference.get();
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

    private static <T> BiConsumer<T, Throwable> toCompletionCallback(AsyncResponse asyncResponse)
    {
        return (value, throwable) -> {
            if (throwable != null) {
                asyncResponse.resume(throwable);
            }
            else {
                checkArgument(!(value instanceof Response.ResponseBuilder), "Value is a ResponseBuilder. Did you forget to call build?");
                asyncResponse.resume(value);
            }
        };
    }
}
