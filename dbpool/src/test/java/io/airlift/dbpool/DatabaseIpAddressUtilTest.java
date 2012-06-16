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
package io.airlift.dbpool;

import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.airlift.dbpool.DatabaseIpAddressUtil.fromDatabaseIpAddress;
import static io.airlift.dbpool.DatabaseIpAddressUtil.toDatabaseIpAddress;
import static org.testng.Assert.assertEquals;

public class DatabaseIpAddressUtilTest
{
    @Test
    public void test()
            throws Exception
    {
        verifyIpAddressConversion("0.0.0.0", 0, Integer.MIN_VALUE);
        verifyIpAddressConversion("255.255.255.255", -1, Integer.MAX_VALUE);
        verifyIpAddressConversion("128.0.0.0", Integer.MIN_VALUE, 0);
        verifyIpAddressConversion("127.255.255.255", Integer.MAX_VALUE, -1);
    }

    private void verifyIpAddressConversion(String ipString, int expectedJavaIpAddress, int expectedDatabaseIpAddress)
            throws UnknownHostException
    {
        // use Java to convert the string to an integer
        InetAddress address = InetAddress.getByName(ipString);
        // java inet4 address hashCode is the address object
        int javaIpAddress = address.hashCode();

        // Verify the java integer value is as expected
        assertEquals(javaIpAddress, expectedJavaIpAddress);

        // Convert to database style and verify
        int databaseIpAddress = toDatabaseIpAddress(javaIpAddress);
        assertEquals(databaseIpAddress, expectedDatabaseIpAddress);

        // Finally, round trip back to java and verify
        assertEquals(fromDatabaseIpAddress(databaseIpAddress), javaIpAddress);
    }
}
