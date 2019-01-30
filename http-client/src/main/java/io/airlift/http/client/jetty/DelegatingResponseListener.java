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
package io.airlift.http.client.jetty;

import io.airlift.http.client.ResponseListener;
import io.airlift.http.client.ResponseTooLargeException;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpHeader;

import javax.annotation.concurrent.GuardedBy;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class DelegatingResponseListener
        extends Response.Listener.Adapter
{
    private final JettyResponseFuture<?, ?> future;
    private final int maxLength;
    private final ResponseListener delegate;

    @GuardedBy("this")
    private long size;

    public DelegatingResponseListener(JettyResponseFuture<?, ?> future, int maxLength, ResponseListener delegate)
    {
        this.future = requireNonNull(future, "future is null");
        checkArgument(maxLength > 0, "maxLength must be greater than zero");
        this.maxLength = maxLength;
        this.delegate = requireNonNull(delegate, "delegate is null");
    }

    @Override
    public synchronized void onHeaders(Response response)
    {
        long length = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH.asString());
        if (length > maxLength) {
            response.abort(new ResponseTooLargeException());
        }
    }

    @Override
    public synchronized void onContent(Response response, ByteBuffer content)
    {
        int length = content.remaining();
        size += length;
        if (size > maxLength) {
            response.abort(new ResponseTooLargeException());
            return;
        }

        delegate.onContent(content);
    }

    @Override
    public synchronized void onComplete(Result result)
    {
        Throwable throwable = result.getFailure();
        if (throwable != null) {
            future.failed(throwable);
        }
        else {
            future.completed(result.getResponse(), delegate.onComplete());
            size = 0;
        }
    }
}
