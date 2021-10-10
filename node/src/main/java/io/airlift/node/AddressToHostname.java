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
package io.airlift.node;

import com.google.common.net.InetAddresses;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Optional;

public final class AddressToHostname
{
    private static final String IP_ENCODED_SUFFIX = ".ip";

    private AddressToHostname() {}

    public static String encodeAddressAsHostname(InetAddress inetAddress)
    {
        String ipString = InetAddresses.toAddrString(inetAddress);
        if (inetAddress instanceof Inet4Address) {
            ipString = ipString.replace('.', '-');
        }
        else {
            // v6 addresses contain `:` which isn't legal, also v6 can
            // start with a `:` so we prefix with an `x`
            ipString = "x" + ipString.replace(':', '-');
        }
        return ipString + ".ip";
    }

    public static Optional<InetAddress> tryDecodeHostnameToAddress(String hostname)
    {
        if (!hostname.endsWith(IP_ENCODED_SUFFIX)) {
            return Optional.empty();
        }

        String ipString = hostname.substring(0, hostname.length() - IP_ENCODED_SUFFIX.length());
        if (ipString.startsWith("x")) {
            ipString = ipString.substring(1).replace('-', ':');
        }
        else {
            ipString = ipString.replace('-', '.');
        }
        return Optional.of(InetAddresses.forString(ipString));
    }
}
