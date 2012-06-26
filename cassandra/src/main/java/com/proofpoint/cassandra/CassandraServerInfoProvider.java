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
package com.proofpoint.cassandra;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.InetAddress;

class CassandraServerInfoProvider
    implements Provider<CassandraServerInfo>
{
    private final InetAddress rpcAddress;
    private final int rpcPort;

    @Inject
    public CassandraServerInfoProvider(EmbeddedCassandraServer server)
    {
        // note: depend on EmbeddedCassandraServer so that the server is guaranteed to be running before CassandraServerInfo
        // is provided to clients

        this.rpcAddress = server.getRpcAddress();
        this.rpcPort = server.getRpcPort();
    }

    @Override
    public CassandraServerInfo get()
    {
        return new CassandraServerInfo(rpcAddress, rpcPort);
    }
}
