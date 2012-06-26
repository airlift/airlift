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

import com.google.common.net.InetAddresses;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.Inet4Address;

import static io.airlift.testing.EquivalenceTester.comparisonTester;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestInet4Network
{
    @Test
    public void testFromCidrValid()
    {
        assertCidrValid("0.0.0.0/0");
        assertCidrValid("1.2.3.4/32");
        assertCidrValid("8.0.0.0/8");
        assertCidrValid("8.8.8.0/24");
        assertCidrValid("8.8.8.8/32");
        assertCidrValid("255.255.255.255/32");
    }

    private static void assertCidrValid(String cidr)
    {
        assertEquals(Inet4Network.fromCidr(cidr).toString(), cidr);
    }

    @DataProvider(name = "invalidCidr")
    public Object[][] invalidCidrProvider()
    {
        return new Object[][]{
                {" 0.0.0.0/0"},
                {"0.0.0.0/0 "},
                {"x.0.0.0/0"},
                {"0.x.0.0/0"},
                {"0.0.x.0/0"},
                {"0.0.0.x/0"},
                {"0.0.0.0/x"},
                {"256.0.0.0/0"},
                {"0.0.0.256/0"},
                {"0.0.0.0/33"},
                {"8.8.8.1/24"},
                {"1.0.0.0/0"},
                {"8.0.0"},
                {"8.0.0.0.0"},
                {"-8.1.0.0"},
                {"8.-1.0.0"},
        };
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "invalidCidr")
    public void testFromCidrInvalid(String cidr)
    {
        Inet4Network.fromCidr(cidr);
    }

    @Test
    public void testStartingAndEndingAddress()
    {
        assertStartingAndEndingAddress("0.0.0.0/0", "255.255.255.255");
        assertStartingAndEndingAddress("0.0.0.0/1", "127.255.255.255");
        assertStartingAndEndingAddress("0.0.0.0/8", "0.255.255.255");
        assertStartingAndEndingAddress("0.0.0.0/24", "0.0.0.255");
        assertStartingAndEndingAddress("0.0.0.0/32", "0.0.0.0");
        assertStartingAndEndingAddress("255.255.255.0/24", "255.255.255.255");
        assertStartingAndEndingAddress("8.8.8.8/32", "8.8.8.8");
        assertStartingAndEndingAddress("8.8.8.8/32", "8.8.8.8");
        assertStartingAndEndingAddress("202.12.128.0/18", "202.12.191.255");
    }

    private static void assertStartingAndEndingAddress(String cidr, String endingAddress)
    {
        String startingAddress = cidr.substring(0, cidr.indexOf('/'));
        Inet4Network network = Inet4Network.fromCidr(cidr);
        assertEquals(network.getStartingAddress(), InetAddresses.forString(startingAddress));
        assertEquals(network.getEndingAddress(), InetAddresses.forString(endingAddress));
    }

    @Test
    public void testAddressToLong()
    {
        assertAddressToLong("0.0.0.0", 0L);
        assertAddressToLong("255.255.255.255", 4294967295L);
        assertAddressToLong("8.8.8.8", 134744072L);
        assertAddressToLong("202.12.128.0", 3389816832L);
        assertAddressToLong("202.12.128.254", 3389817086L);
    }

    private static void assertAddressToLong(String address, long ip)
    {
        Inet4Address addr = (Inet4Address) InetAddresses.forString(address);
        assertEquals(Inet4Network.addressToLong(addr), ip);
    }

    @Test
    public void testFromAddress()
    {
        assertFromAddress("1.2.3.4", "1.2.3.4/32", 32);
        assertFromAddress("8.8.8.8", "8.8.8.8/32", 32);
        assertFromAddress("208.86.202.10", "208.86.202.10/32", 32);
        assertFromAddress("208.86.202.0", "208.86.202.0/24", 24);
    }

    private static void assertFromAddress(String address, String cidr, int bits)
    {
        Inet4Address addr = (Inet4Address) InetAddresses.forString(address);
        assertEquals(Inet4Network.fromAddress(addr, bits), Inet4Network.fromCidr(cidr));
    }

    @Test
    public void testTruncatedFromAddress()
    {
        assertTruncatedFromAddress("1.2.3.4", "1.2.3.4/32", 32);
        assertTruncatedFromAddress("1.2.3.4", "1.2.3.0/24", 24);
        assertTruncatedFromAddress("8.8.8.8", "8.8.8.8/32", 32);
        assertTruncatedFromAddress("8.8.8.8", "8.8.8.0/24", 24);
        assertTruncatedFromAddress("8.8.8.8", "8.8.0.0/16", 16);
        assertTruncatedFromAddress("8.8.8.8", "8.0.0.0/8", 8);
        assertTruncatedFromAddress("208.86.202.10", "208.86.202.10/32", 32);
        assertTruncatedFromAddress("208.86.202.10", "208.86.202.0/24", 24);
        assertTruncatedFromAddress("208.86.202.0", "208.86.202.0/24", 24);
    }

    private static void assertTruncatedFromAddress(String address, String cidr, int bits)
    {
        Inet4Address addr = (Inet4Address) InetAddresses.forString(address);
        assertEquals(Inet4Network.truncatedFromAddress(addr, bits), Inet4Network.fromCidr(cidr));
    }

    @Test
    public void testGetBits()
    {
        assertEquals(Inet4Network.fromCidr("8.0.0.0/8").getBits(), 8);
        assertEquals(Inet4Network.fromCidr("8.0.0.0/24").getBits(), 24);
        assertEquals(Inet4Network.fromCidr("8.0.0.0/32").getBits(), 32);
    }

    @Test
    public void testContainsAddress()
    {
        assertTrue(containsAddress("0.0.0.0/0", "0.0.0.0"));
        assertTrue(containsAddress("0.0.0.0/0", "1.2.3.4"));
        assertTrue(containsAddress("0.0.0.0/0", "255.255.255.255"));
        assertTrue(containsAddress("8.8.8.0/24", "8.8.8.0"));
        assertTrue(containsAddress("8.8.8.0/24", "8.8.8.8"));
        assertTrue(containsAddress("8.8.8.0/24", "8.8.8.255"));
        assertTrue(containsAddress("202.12.128.0/18", "202.12.128.0"));
        assertTrue(containsAddress("202.12.128.0/18", "202.12.128.255"));
        assertTrue(containsAddress("202.12.128.0/18", "202.12.157.123"));
        assertTrue(containsAddress("202.12.128.0/18", "202.12.191.255"));

        assertFalse(containsAddress("8.8.8.0/24", "8.8.9.0"));
        assertFalse(containsAddress("8.8.8.8/32", "8.8.8.9"));
        assertFalse(containsAddress("202.12.128.0/18", "202.12.127.255"));
        assertFalse(containsAddress("202.12.128.0/18", "202.12.192.0"));
    }

    private static boolean containsAddress(String cidr, String address)
    {
        Inet4Address addr = (Inet4Address) InetAddresses.forString(address);
        return Inet4Network.fromCidr(cidr).containsAddress(addr);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void testEquals()
    {
        equivalenceTester()
                .addEquivalentGroup(Inet4Network.fromCidr("8.0.0.0/8"))
                .addEquivalentGroup(Inet4Network.fromCidr("8.8.8.0/24"))
                .addEquivalentGroup(Inet4Network.fromCidr("8.8.8.8/32"))
                .check();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void testCompareTo()
    {
        comparisonTester()
                .addLesserGroup(Inet4Network.fromCidr("0.0.0.0/0"))
                .addGreaterGroup(Inet4Network.fromCidr("8.0.0.0/8"))
                .addGreaterGroup(Inet4Network.fromCidr("8.0.0.0/9"))
                .addGreaterGroup(Inet4Network.fromCidr("8.8.8.0/24"))
                .addGreaterGroup(Inet4Network.fromCidr("8.8.8.1/32"))
                .addGreaterGroup(Inet4Network.fromCidr("8.8.8.8/32"))
                .addGreaterGroup(Inet4Network.fromCidr("8.8.8.255/32"))
                .addGreaterGroup(Inet4Network.fromCidr("8.8.9.0/24"))
                .addGreaterGroup(Inet4Network.fromCidr("17.0.0.0/8"))
                .addGreaterGroup(Inet4Network.fromCidr("17.0.0.0/9"))
                .addGreaterGroup(Inet4Network.fromCidr("202.12.128.0/18"))
                .addGreaterGroup(Inet4Network.fromCidr("202.12.191.255/32"))
                .addGreaterGroup(Inet4Network.fromCidr("255.0.0.0/8"))
                .addGreaterGroup(Inet4Network.fromCidr("255.255.255.0/24"))
                .addGreaterGroup(Inet4Network.fromCidr("255.255.255.255/32"))
                .check();
    }
}
