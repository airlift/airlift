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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;

class ForwardedServlet
        extends HttpServlet
{
    private volatile String scheme;
    private volatile Boolean isSecure;
    private volatile String requestUrl;
    private volatile String remoteAddress;

    public void reset()
    {
        scheme = null;
        isSecure = null;
        requestUrl = null;
        remoteAddress = null;
    }

    public String getScheme()
    {
        return scheme;
    }

    public Boolean getIsSecure()
    {
        return isSecure;
    }

    public String getRequestUrl()
    {
        return requestUrl;
    }

    public String getRemoteAddress()
    {
        return remoteAddress;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    {
        scheme = request.getScheme();
        isSecure = request.isSecure();
        requestUrl = request.getRequestURL().toString();
        remoteAddress = request.getRemoteAddr();
        response.setStatus(SC_OK);
    }
}
