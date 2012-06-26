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
package io.airlift.jmx.http.rpc;

public class HttpMBeanServerCredentials
{
    private final String username;
    private final String password;

    public HttpMBeanServerCredentials(String username, String password)
    {
        this.username = username;
        this.password = password;
    }

    public boolean authenticate(HttpMBeanServerCredentials userCredentials)
    {
        if (username == null && password == null) {
            return true;
        }

        return equals(username, userCredentials.username) &&
                equals(password, userCredentials.password);
    }

    public static HttpMBeanServerCredentials fromBasicAuthHeader(String authHeader)
    {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return new HttpMBeanServerCredentials(null, null);
        }

        String credentials = new String(HttpMBeanServerRpc.base64Decode(authHeader.substring("Basic ".length())));
        int index = credentials.indexOf(':');
        if (index >= 0) {
            return new HttpMBeanServerCredentials(credentials.substring(0, index), credentials.substring(index + 1));
        }
        else {
            return new HttpMBeanServerCredentials(credentials, null);
        }
    }

    public String toBasicAuthHeader()
    {
        return "Basic " + HttpMBeanServerRpc.base64Encode(String.format("%s:%s", username == null ? "" : username, password == null ? "" : password));
    }

    private static boolean equals(Object left, Object right)
    {
        if (left == null) {
            return right == null;
        }
        else {
            return left.equals(right);
        }
    }
}
