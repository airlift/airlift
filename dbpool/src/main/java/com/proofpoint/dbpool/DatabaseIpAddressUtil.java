package com.proofpoint.dbpool;

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
