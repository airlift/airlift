/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.Closeable;

@Beta
public interface HttpClient
        extends Closeable
{
    <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E;

    <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler);

    <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler, ResponseListener responseListener);

    RequestStats getStats();

    long getMaxContentLength();

    @Override
    void close();

    boolean isClosed();

    interface HttpResponseFuture<T>
            extends ListenableFuture<T>
    {
        /**
         * State for diagnostics.  Do not rely on values from this method.
         */
        String getState();
    }
}
