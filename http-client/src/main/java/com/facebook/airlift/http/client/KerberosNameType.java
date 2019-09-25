package com.facebook.airlift.http.client;

import org.ietf.jgss.Oid;

import static java.util.Objects.requireNonNull;
import static org.ietf.jgss.GSSName.NT_HOSTBASED_SERVICE;
import static org.ietf.jgss.GSSName.NT_USER_NAME;

public enum KerberosNameType
{
    HOSTBASED_SERVICE(NT_HOSTBASED_SERVICE),
    USER_NAME(NT_USER_NAME);

    private final Oid oid;

    KerberosNameType(Oid oid)
    {
        this.oid = requireNonNull(oid, "oid is null");
    }

    public Oid getOid()
    {
        return oid;
    }
}
