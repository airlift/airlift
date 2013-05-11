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

import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;

final class RetryingResponseHandler<T, E extends Exception>
        implements ResponseHandler<T, RetryException>
{
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
        return new RetryException();
    }

    @Override
    public T handle(Request request, Response response)
            throws RetryException
    {
        switch (response.getStatusCode()) {
            case 408:
            case 500:
            case 502:
            case 503:
            case 504:
                throw new RetryException();
        }

        try {
            return innerHandler.handle(originalRequest, response);
        }
        catch (Exception e) {
            throw new InnerHandlerException(e);
        }
    }
}
