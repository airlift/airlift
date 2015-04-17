package io.airlift.security.utils;

import io.airlift.security.exception.AuthenticationException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.Closeable;
import java.net.URI;
import java.net.UnknownHostException;

import static java.util.Objects.requireNonNull;

public class CloseableGSSContext
        implements Closeable
{
    private GSSContext context;

    public CloseableGSSContext(String serviceName, URI serviceURI)
    {
        requireNonNull(serviceName, "serviceName is null");
        requireNonNull(serviceURI, "serviceURI is null");
        String servicePrincipal = null;
        try {
            servicePrincipal = KerberosUtil.getServicePrincipal(serviceName, serviceURI.getHost());
            Subject clientSubject = KerberosUtil.getSubject(null, true);
            context = KerberosUtil.getGssContext(clientSubject, servicePrincipal, true);
        }
        catch (LoginException | UnknownHostException e) {
            throw new AuthenticationException(e);
        }
    }

    public GSSContext getContext()
    {
        return context;
    }

    @Override
    public void close()
    {
        if (context != null) {
            try {
                context.dispose();
            }
            catch (GSSException e) {
                throw new AuthenticationException(e);
            }
        }
    }
}
