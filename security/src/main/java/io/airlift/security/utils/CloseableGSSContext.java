package io.airlift.security.utils;

import com.google.common.base.Throwables;
import com.sun.security.auth.module.Krb5LoginModule;
import io.airlift.log.Logger;
import io.airlift.security.exception.AuthenticationException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;

public class CloseableGSSContext
        implements Closeable
{
    private static final Logger log = Logger.get(KerberosUtil.class);

    private final GSSContext context;
    private final LoginContext loginContext;

    public CloseableGSSContext(String serviceName, URI serviceUri)
    {
        requireNonNull(serviceName, "serviceName is null");
        requireNonNull(serviceUri, "serviceUri is null");
        try {
            loginContext = new LoginContext("", null, null, new Configuration()
            {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name)
                {
                    Map<String, String> options = new HashMap<>();
                    options.put("refreshKrb5Config", "true");
                    options.put("doNotPrompt", "true");
                    if (log.isDebugEnabled()) {
                        options.put("debug", "true");
                    }

                    String ticketCache = System.getenv("KRB5CCNAME");
                    if (ticketCache != null) {
                        options.put("ticketCache", ticketCache);
                        options.put("useTicketCache", "true");
                        options.put("renewTGT", "true");
                    }

                    return new AppConfigurationEntry[] {
                            new AppConfigurationEntry(Krb5LoginModule.class.getName(), REQUIRED, options)
                    };
                }
            });
            String servicePrincipal = KerberosUtil.getServicePrincipal(serviceName, serviceUri.getHost());
            Subject subject = KerberosUtil.getClientSubject(loginContext);
            context = KerberosUtil.getGssContext(subject, servicePrincipal);
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
        try {
            context.dispose();
            loginContext.logout();
        }
        catch (GSSException | LoginException e) {
            throw new AuthenticationException(e);
        }
    }

    private static class KerberosUtil
    {
        private static final GSSManager gssManager = GSSManager.getInstance();

        private static final Oid SPNEGO_OID;
        private static final Oid KERBEROS_OID;

        static {
            try {
                SPNEGO_OID = new Oid("1.3.6.1.5.5.2");
                KERBEROS_OID = new Oid("1.2.840.113554.1.2.2");
            }
            catch (GSSException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Create Kerberos principal for a given service and hostname. It converts
         * hostname to lower case. If hostname is null or "0.0.0.0", it uses
         * dynamically looked-up fqdn of the current host instead.
         *
         * @param serviceName Service for which you want to generate the principal.
         * @param hostName Fully-qualified domain name.
         * @return Converted Kerberos principal name.
         * @throws UnknownHostException If no IP address for the local host could be found.
         */
        public static String getServicePrincipal(String serviceName, String hostName)
                throws UnknownHostException
        {
            requireNonNull(serviceName, "serviceName is null");
            requireNonNull(hostName, "hostName is null");
            InetAddress address = InetAddress.getByName(hostName);
            String fqdn;
            if (address.getHostName().toLowerCase().startsWith("localhost")) {
                return getServicePrincipal(serviceName);
            }
            fqdn = address.getCanonicalHostName();

            return String.format("%s/%s", serviceName, fqdn.toLowerCase(Locale.US));
        }

        public static String getServicePrincipal(String serviceName)
                throws UnknownHostException
        {
            requireNonNull(serviceName, "serviceName is null");
            String fqdn = InetAddress.getLocalHost().getCanonicalHostName();
            checkState(!fqdn.equalsIgnoreCase("localhost"), "Fully qualified host name for localhost is not retrieved");

            return String.format("%s/%s", serviceName, fqdn.toLowerCase(Locale.US));
        }

        public static Subject getClientSubject(LoginContext context)
                throws LoginException, UnknownHostException
        {

            context.login();
            return context.getSubject();
        }

        public static GSSContext getGssContext(Subject subject, String servicePrincipal)
        {
            return Subject.doAs(subject, (PrivilegedAction<GSSContext>) () -> {
                GSSContext context = null;
                try {
                    Principal subjectPrincipal = subject.getPrincipals().iterator().next();
                    GSSName gssUserName = gssManager.createName(subjectPrincipal.getName(), GSSName.NT_USER_NAME);
                    GSSCredential clientCredential = gssManager.createCredential(gssUserName, GSSCredential.DEFAULT_LIFETIME,
                            KERBEROS_OID, GSSCredential.INITIATE_ONLY);
                    GSSName gssServiceName = gssManager.createName(servicePrincipal, null);
                    context = gssManager.createContext(gssServiceName, SPNEGO_OID, clientCredential, GSSContext.INDEFINITE_LIFETIME);
                    context.requestMutualAuth(true);
                    context.requestConf(true);
                    context.requestInteg(true);
                    context.requestCredDeleg(false);
                    return context;
                }
                catch (GSSException e) {
                    throw Throwables.propagate(e);
                }
            });
        }
    }
}
