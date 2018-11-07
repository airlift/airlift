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
package io.airlift.jmx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

final class NetUtils
{
    private NetUtils() {}

    public static int findUnusedPort()
            throws IOException
    {
        int port;

        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(0));
            port = socket.getLocalPort();
        }

        return port;
    }
}
