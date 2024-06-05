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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

public final class AddressToHostname
{
    private static final String IP_ENCODED_SUFFIX = ".ip";

    private AddressToHostname() {}

    public static String encodeAddressAsHostname(InetAddress inetAddress)
    {
        String hostname = switch (inetAddress) {
            case Inet4Address inet4Address -> InetAddresses
                    .toAddrString(inet4Address)
                    .replace('.', '-');

            // v6 addresses contain `:` which isn't legal, also v6 can
            // start with a `:` so we prefix with an `x`
            case Inet6Address inet6Address -> 'x' + stripScopeId(InetAddresses.toAddrString(inet6Address))
                    .replace(':', '-');

            default -> InetAddresses.toAddrString(inetAddress);
        };

        return hostname + IP_ENCODED_SUFFIX;
    }

    // Strips scope ID added to toAddrString representation in https://github.com/google/guava/commit/3f61870ac6e5b18dbb74ce6f6cb2930ad8750a43
    private static String stripScopeId(String address)
    {
        int index = address.indexOf('%');
        if (index == -1) {
            return address;
        }
        return address.substring(0, index);
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

        byte[] address = InetAddresses.forString(ipString).getAddress();

        try {
            return Optional.of(InetAddress.getByAddress(hostname, address));
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
