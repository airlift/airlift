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
        private final long requestCreatedTimestamp;
        private final long requestBeginTimestamp;
        private final long requestEndTimestamp;

        private RequestInfo(Request request, long requestTimestampMillis, long requestCreatedTimestamp, long requestBeginTimestamp, long requestEndTimestamp)
        {
            this.request = requireNonNull(request, "request is null");
            this.requestTimestampMillis = requestTimestampMillis;
            this.requestCreatedTimestamp = requestCreatedTimestamp;
            this.requestBeginTimestamp = requestBeginTimestamp;
            this.requestEndTimestamp = requestEndTimestamp;
        }

        public static RequestInfo from(Request request, long requestTimestampMillis)
        {
            requireNonNull(request, "request is null");
            return new RequestInfo(request, requestTimestampMillis, 0L, 0L, 0L);
        }

        public static RequestInfo from(Request request, long requestTimestampMillis, long requestCreatedTimestamp, long requestBeginTimestamp, long requestEndTimestamp)
        {
            requireNonNull(request, "request is null");
            return new RequestInfo(request, requestTimestampMillis, requestCreatedTimestamp, requestBeginTimestamp, requestEndTimestamp);
        }

        public Request getRequest()
        {
            return request;
        }

        public long getRequestTimestampMillis()
        {
            return requestTimestampMillis;
        }

        public long getRequestBeginTimestamp()
        {
            return requestBeginTimestamp;
        }

        public long getRequestEndTimestamp()
        {
            return requestEndTimestamp;
        }

        public long getRequestCreatedTimestamp()
        {
            return requestCreatedTimestamp;
        }
    }

    class ResponseInfo
    {
        private final Optional<Response> response;
        private final Optional<Throwable> failureCause;
        private final long responseSize;
        private final long responseTimestampMillis = System.currentTimeMillis();
        private final long responseBeginTimestamp;
        private final long responseCompleteTimestamp;

        private ResponseInfo(
                Optional<Response> response,
                long responseSize,
                long responseBeginTimestamp,
                long responseCompleteTimestamp,
                Optional<Throwable> failure)
        {
            requireNonNull(response, "response is null");
            requireNonNull(failure, "failure is null");
            this.response = response;
            this.responseSize = responseSize;
            this.responseBeginTimestamp = responseBeginTimestamp;
            this.responseCompleteTimestamp = responseCompleteTimestamp;
            this.failureCause = failure;
        }

        public static ResponseInfo from(Optional<Response> response, long responseSize, long responseBeginTimestamp, long responseCompleteTimestamp)
        {
            requireNonNull(response, "response is null");
            checkArgument(responseSize >= 0, "responseSize is negative");
            return new ResponseInfo(response, responseSize, responseBeginTimestamp, responseCompleteTimestamp, Optional.empty());
        }

        public static ResponseInfo failed(Optional<Response> response, Optional<Throwable> failureCause)
        {
            requireNonNull(response, "response is null");
            requireNonNull(failureCause, "failureCause is null");
            return new ResponseInfo(response, 0L, 0L, System.nanoTime(), failureCause);
        }

        public static ResponseInfo failed(Optional<Response> response, Optional<Throwable> failureCause, long responseBeginTimestamp, long responseCompleteTimestamp)
        {
            requireNonNull(response, "response is null");
            requireNonNull(failureCause, "failureCause is null");
            return new ResponseInfo(response, 0L, responseBeginTimestamp, responseCompleteTimestamp, failureCause);
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

        public long getResponseBeginTimestamp()
        {
            return responseBeginTimestamp;
        }

        public long getResponseCompleteTimestamp()
        {
            return responseCompleteTimestamp;
        }
    }
}
