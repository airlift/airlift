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
package com.proofpoint.http.client;

import com.google.common.base.Throwables;

import java.util.concurrent.ExecutionException;

import static com.google.common.base.Throwables.propagate;

public class SyncToAsyncWrapperClient
    implements HttpClient
{
    private final HttpClient delegate;

    public SyncToAsyncWrapperClient(HttpClient delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        return delegate.executeAsync(request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        try {
              return delegate.executeAsync(request, responseHandler).get();
          }
          catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw propagate(e);
          }
          catch (ExecutionException e) {
              Throwables.propagateIfPossible(e.getCause());

              if (e.getCause() instanceof Exception) {
                  // the HTTP client and ResponseHandler interface enforces this
                  throw (E) e.getCause();
              }

              // e.getCause() is some direct subclass of throwable
              throw propagate(e.getCause());
          }
    }

    @Override
    public RequestStats getStats()
    {
        return delegate.getStats();
    }

    @Override
    public void close()
    {
        delegate.close();
    }
}
