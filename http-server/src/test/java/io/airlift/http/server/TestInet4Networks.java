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

import org.testng.annotations.Test;

import static io.airlift.http.server.Inet4Networks.isPrivateNetworkAddress;
import static org.assertj.core.api.Assertions.assertThat;

public class TestInet4Networks
{
    @Test
    public void test()
    {
        assertThat(isPrivateNetworkAddress("127.0.0.1")).isTrue();
        assertThat(isPrivateNetworkAddress("127.1.2.3")).isTrue();
        assertThat(isPrivateNetworkAddress("169.254.0.1")).isTrue();
        assertThat(isPrivateNetworkAddress("169.254.1.2")).isTrue();
        assertThat(isPrivateNetworkAddress("192.168.0.1")).isTrue();
        assertThat(isPrivateNetworkAddress("192.168.1.2")).isTrue();
        assertThat(isPrivateNetworkAddress("172.16.0.1")).isTrue();
        assertThat(isPrivateNetworkAddress("172.16.1.2")).isTrue();
        assertThat(isPrivateNetworkAddress("172.16.1.2")).isTrue();
        assertThat(isPrivateNetworkAddress("10.0.0.1")).isTrue();
        assertThat(isPrivateNetworkAddress("10.1.2.3")).isTrue();

        assertThat(isPrivateNetworkAddress("1.2.3.4")).isFalse();
        assertThat(isPrivateNetworkAddress("172.33.0.0")).isFalse();
    }
}
