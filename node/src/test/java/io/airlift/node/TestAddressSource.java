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

import static io.airlift.node.AddressToHostname.encodeAddressAsHostname;
import static io.airlift.node.AddressToHostname.tryDecodeHostnameToAddress;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.InetAddresses;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class TestAddressSource {
    @Test
    public void testIpEncoding() {
        verifyEncoding("127.0.0.1", "127-0-0-1.ip");
        verifyEncoding("1.2.3.4", "1-2-3-4.ip");
        verifyEncoding("2001:db8:85a3::8a2e:370:7334", "x2001-db8-85a3--8a2e-370-7334.ip");
        verifyEncoding("2001:db8:85a3:0000::8a2e:0370:7334", "x2001-db8-85a3--8a2e-370-7334.ip");
        verifyEncoding("::1", "x--1.ip");
    }

    private static void verifyEncoding(String addressString, String encodedHostname) {
        assertThat(encodeAddressAsHostname(InetAddresses.forString(addressString)))
                .isEqualTo(encodedHostname);
        assertThat(tryDecodeHostnameToAddress(encodedHostname))
                .isEqualTo(Optional.of(InetAddresses.forString(addressString)));
    }
}
