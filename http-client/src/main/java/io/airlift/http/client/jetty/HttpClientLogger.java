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

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public interface HttpClientLogger
{
    void log(RequestInfo requestInfo, ResponseInfo responseInfo);
    void close();
    int getQueueSize();

    class RequestInfo
    {
        private final Request request;
        private final long requestTimestampMillis;

        private RequestInfo(Request request, long requestTimestampMillis)
        {
            checkArgument(requestTimestampMillis >= 0, "requestTimestampMillis is negative");
            this.request = requireNonNull(request, "request is null");
            this.requestTimestampMillis = requestTimestampMillis;
        }

        public static RequestInfo from(Request request, long requestTimestampMillis)
        {
            requireNonNull(request, "request is null");
            checkArgument(requestTimestampMillis >= 0, "requestTimestampMillis is negative");
            return new RequestInfo(request, requestTimestampMillis);
        }

        public Request getRequest()
        {
            return request;
        }

        public long getRequestTimestampMillis()
        {
            return requestTimestampMillis;
        }
    }

    class ResponseInfo
    {
        private final Optional<Response> response;
        private final Optional<Throwable> failureCause;
        private final long responseSize;
        private final long responseTimestampMillis = System.currentTimeMillis();

        private ResponseInfo(Optional<Response> response, long responseSize, Optional<Throwable> failure)
        {
            requireNonNull(response, "response is null");
            requireNonNull(failure, "failure is null");
            this.response = response;
            this.responseSize = responseSize;
            this.failureCause = failure;
        }

        public static ResponseInfo from(Optional<Response> response, long responseSize)
        {
            requireNonNull(response, "response is null");
            checkArgument(responseSize >= 0, "responseSize is negative");
            return new ResponseInfo(response, responseSize, Optional.empty());
        }

        public static ResponseInfo failed(Optional<Response> response, Optional<Throwable> failureCause)
        {
            requireNonNull(response, "response is null");
            requireNonNull(failureCause, "failureCause is null");
            return new ResponseInfo(response, -1L, failureCause);
        }

        public Optional<Response> getResponse()
        {
            return response;
        }

        public long getResponseSize()
        {
            return responseSize;
        }

        public Optional<Throwable> getFailureCause()
        {
            return failureCause;
        }

        public long getResponseTimestampMillis()
        {
            return responseTimestampMillis;
        }
    }
}
