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
package io.airlift.http.server;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static javax.servlet.http.HttpServletResponse.SC_OK;

class DummyServlet
        extends HttpServlet
{
    private final AtomicReference<Exception> exception = new AtomicReference<>();
    private final CountDownLatch requestLatch;

    public DummyServlet()
    {
        this.requestLatch = null;
    }

    public DummyServlet(CountDownLatch requestLatch)
    {
        this.requestLatch = requireNonNull(requestLatch, "requestLatch is null");
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    {
        try {
            if (requestLatch != null) {
                requestLatch.countDown();
            }
            response.setStatus(SC_OK);
            response.setHeader("X-Protocol", request.getProtocol());
            if (request.getUserPrincipal() != null) {
                response.getOutputStream().write(request.getUserPrincipal().getName().getBytes());
            }
            if (request.getParameter("sleep") != null) {
                Thread.sleep(Long.parseLong(request.getParameter("sleep")));
            }
        }
        catch (Exception t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            exception.set(t);
        }
    }

    public Throwable getException()
    {
        return exception.get();
    }
}
