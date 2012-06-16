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

/**
 * This class provides utility methods to convert from java ip addresses to
 * normalized database ip addresses where 0.0.0.0 is the smallest value and
 * 255.255.255.255 is largest value.  This allows ip addresses to be compared
 * using simple greater than and less than in the where clause.
 */
public final class DatabaseIpAddressUtil
{
    private DatabaseIpAddressUtil()
    {
    }

    /**
     * Converts the specified java ip address to the normalized database format.
     */
    public static int toDatabaseIpAddress(int javaIpAddress)
    {
        return javaIpAddress + (1 << 31);
    }

    /**
     * Converts the specified normalized database ip address to a java ip address.
     */
    public static int fromDatabaseIpAddress(int databaseIpAddress)
    {
        return databaseIpAddress + (1 << 31);
    }
}
