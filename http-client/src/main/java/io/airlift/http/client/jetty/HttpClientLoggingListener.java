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

import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import io.airlift.http.client.jetty.HttpClientLogger.ResponseInfo;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;

import java.nio.ByteBuffer;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

class HttpClientLoggingListener
        extends Response.Listener.Adapter
        implements Request.Listener
{
    private final HttpClientLogger logger;
    private final long requestTimestampMillis;
    private final HttpRequest request;
    private long contentSize;
    private final long requestCreatedTimestamp = System.nanoTime();
    private long requestBeginTimestamp;
    private long requestEndTimestamp;
    private long responseBeginTimestamp;
    private long responseCompleteTimestamp;

    HttpClientLoggingListener(HttpRequest request, long requestTimestampMillis, HttpClientLogger logger)
    {
        this.request = requireNonNull(request, "request is null");
        this.requestTimestampMillis = requestTimestampMillis;
        this.logger = requireNonNull(logger, "logger is null");
    }

    @Override
    public void onBegin(Request request)
    {
        requestBeginTimestamp = System.nanoTime();
    }

    @Override
    public void onCommit(Request request)
    {
    }

    @Override
    public void onContent(Request request, ByteBuffer content)
    {
        contentSize += content.remaining();
    }

    @Override
    public void onFailure(Request request, Throwable failure)
    {
        requestEndTimestamp = System.nanoTime();
    }

    @Override
    public void onHeaders(Request request)
    {
    }

    @Override
    public void onQueued(Request request)
    {
    }

    @Override
    public void onSuccess(Request request)
    {
        requestEndTimestamp = System.nanoTime();
    }

    @Override
    public void onBegin(Response response)
    {
        responseBeginTimestamp = System.nanoTime();
    }

    @Override
    public void onComplete(Result result)
    {
        responseCompleteTimestamp = System.nanoTime();
        logRequestResponse(result);
    }

    private void logRequestResponse(Result result)
    {
        RequestInfo requestInfo = RequestInfo.from(request, requestTimestampMillis, requestCreatedTimestamp, requestBeginTimestamp, requestEndTimestamp);
        Throwable throwable = result.getFailure();
        ResponseInfo responseInfo;
        if (throwable != null) {
            responseInfo = ResponseInfo.failed(Optional.of(result.getResponse()), Optional.of(throwable), responseBeginTimestamp, responseCompleteTimestamp);
        }
        else {
            responseInfo = ResponseInfo.from(Optional.of(result.getResponse()), contentSize, responseBeginTimestamp, responseCompleteTimestamp);
        }
        logger.log(requestInfo, responseInfo);
    }
}
