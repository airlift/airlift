package com.proofpoint.zookeeper;

import java.util.Arrays;

public class ZookeeperSessionID
{
    private long sessionId;
    private byte[] password;

    public long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(long sessionId)
    {
        this.sessionId = sessionId;
    }

    public byte[] getPassword()
    {
        return password;
    }

    public void setPassword(byte[] password)
    {
        this.password = password;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ZookeeperSessionID sessionID = (ZookeeperSessionID) o;

        if (sessionId != sessionID.sessionId) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (!Arrays.equals(password, sessionID.password)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (int) (sessionId ^ (sessionId >>> 32));
        result = 31 * result + Arrays.hashCode(password);
        return result;
    }
}
