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

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.util.List;

import static io.airlift.http.server.Inet4Network.fromCidr;

class Inet4Networks
{
    private static final List<Inet4Network> PRIVATE_NETWORKS = ImmutableList.of(
            fromCidr("127.0.0.0/8"),
            fromCidr("169.254.0.0/16"),
            fromCidr("192.168.0.0/16"),
            fromCidr("172.16.0.0/12"),
            fromCidr("10.0.0.0/8")
    );


    public static boolean isPrivateNetworkAddress(String inetAddress)
    {
        Inet4Address address = InetAddresses.getCoercedIPv4Address(InetAddresses.forString(inetAddress));
        return isPrivateNetworkAddress(address);
    }

    public static boolean isPrivateNetworkAddress(Inet4Address inetAddress)
    {
        for (Inet4Network privateNetwork : PRIVATE_NETWORKS) {
            if (privateNetwork.containsAddress(inetAddress)) {
                return true;
            }
        }
        return false;
    }

    private Inet4Networks()
    {
    }
}
