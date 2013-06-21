/*
 * Copyright 2013 Proofpoint, Inc.
 *
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
package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableSet;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.log.Logger;

import java.util.Set;

final class RetryingResponseHandler<T, E extends Exception>
        implements ResponseHandler<T, RetryException>
{
    private static final Set<Integer> RETRYABLE_STATUS_CODES = ImmutableSet.of(408, 500, 502, 503, 504);
    private static final Logger log = Logger.get(RetryingResponseHandler.class);
    private final Request originalRequest;
    private final ResponseHandler<T, E> innerHandler;

    public RetryingResponseHandler(Request originalRequest, ResponseHandler<T, E> innerHandler)
    {
        this.originalRequest = originalRequest;
        this.innerHandler = innerHandler;
    }

    @Override
    public RetryException handleException(Request request, Exception exception)
    {
        log.warn(exception, "Exception querying %s",
                request.getUri().resolve("/"));
        return new RetryException();
    }

    @Override
    public T handle(Request request, Response response)
            throws RetryException
    {
        if (RETRYABLE_STATUS_CODES.contains(response.getStatusCode())) {
            String retryHeader = response.getHeader("X-Proofpoint-Retry");
            if (!("no".equalsIgnoreCase(retryHeader))) {
                log.warn("%d response querying %s",
                        response.getStatusCode(), request.getUri().resolve("/"));
                throw new RetryException();
            }

            Object result;
            try {
                result = innerHandler.handle(originalRequest, response);
            }
            catch (Exception e) {
                throw new InnerHandlerException(e);
            }
            throw new FailureStatusException(result);
        }

        try {
            return innerHandler.handle(originalRequest, response);
        }
        catch (Exception e) {
            throw new InnerHandlerException(e);
        }
    }
}
