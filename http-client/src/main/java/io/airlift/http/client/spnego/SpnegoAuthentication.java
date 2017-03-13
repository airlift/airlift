package io.airlift.http.client.spnego;

import com.google.common.collect.ImmutableMap;
import com.sun.security.auth.module.Krb5LoginModule;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Attributes;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import javax.annotation.concurrent.GuardedBy;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.File;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
import static org.ietf.jgss.GSSContext.INDEFINITE_LIFETIME;
import static org.ietf.jgss.GSSCredential.DEFAULT_LIFETIME;
import static org.ietf.jgss.GSSCredential.INITIATE_ONLY;
import static org.ietf.jgss.GSSName.NT_HOSTBASED_SERVICE;
import static org.ietf.jgss.GSSName.NT_USER_NAME;

public class SpnegoAuthentication
        implements Authentication
{
    private static final String NEGOTIATE = HttpHeader.NEGOTIATE.asString();
    private static final Logger LOG = Logger.get(SpnegoAuthentication.class);
    private static final Duration MIN_CREDENTIAL_LIFE_TIME = new Duration(60, TimeUnit.SECONDS);

    private static final GSSManager GSS_MANAGER = GSSManager.getInstance();

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

    private final File keytab;
    private final File credentialCache;
    private final String principal;
    private final String remoteServiceName;
    private final boolean useCanonicalHostname;

    @GuardedBy("this")
    private Session clientSession;

    public SpnegoAuthentication(File keytab, File kerberosConfig, File credentialCache, String principal, String remoteServiceName, boolean useCanonicalHostname)
    {
        requireNonNull(kerberosConfig, "kerberosConfig is null");
        requireNonNull(remoteServiceName, "remoteServiceName is null");

        this.keytab = keytab;
        this.credentialCache = credentialCache;
        this.principal = principal;
        this.remoteServiceName = remoteServiceName;
        this.useCanonicalHostname = useCanonicalHostname;

        System.setProperty("java.security.krb5.conf", kerberosConfig.getAbsolutePath());
    }

    @Override
    public Result authenticate(Request request, ContentResponse response, HeaderInfo headerInfo, Attributes attributes)
    {
        URI normalizedUri = UriUtil.normalizedUri(request.getURI());

        return new Result()
        {
            @Override
            public URI getURI()
            {
                return normalizedUri;
            }

            @Override
            public void apply(Request request)
            {
                GSSContext context = null;
                try {
                    String servicePrincipal = makeServicePrincipal(remoteServiceName, normalizedUri.getHost(), useCanonicalHostname);
                    Session session = getSession();
                    context = doAs(session.getLoginContext().getSubject(), () -> {
                        GSSContext result = GSS_MANAGER.createContext(
                                GSS_MANAGER.createName(servicePrincipal, NT_HOSTBASED_SERVICE),
                                SPNEGO_OID,
                                session.getClientCredential(),
                                INDEFINITE_LIFETIME);

                        result.requestMutualAuth(true);
                        result.requestConf(true);
                        result.requestInteg(true);
                        result.requestCredDeleg(false);
                        return result;
                    });

                    byte[] token = context.initSecContext(new byte[0], 0, 0);
                    if (token != null) {
                        request.header(headerInfo.getHeader(), format("%s %s", NEGOTIATE, Base64.getEncoder().encodeToString(token)));
                    }
                    else {
                        throw new RuntimeException(format("No token generated from GSS context for %s", request.getURI()));
                    }
                }
                catch (GSSException e) {
                    throw new RuntimeException(format("Failed to establish GSSContext for request %s", request.getURI()), e);
                }
                catch (LoginException e) {
                    throw new RuntimeException(format("Failed to establish LoginContext for request %s", request.getURI()), e);
                }
                finally {
                    try {
                        if (context != null) {
                            context.dispose();
                        }
                    }
                    catch (GSSException e) {
                        // ignore
                    }
                }
            }
        };
    }

    @Override
    public boolean matches(String type, URI uri, String realm)
    {
        // The class matches all requests for Negotiate scheme. Realm is not used for now
        return NEGOTIATE.equalsIgnoreCase(type);
    }

    private synchronized Session getSession()
            throws LoginException, GSSException
    {
        if (clientSession == null || clientSession.getClientCredential().getRemainingLifetime() < MIN_CREDENTIAL_LIFE_TIME.getValue(TimeUnit.SECONDS)) {
            // TODO: do we need to call logout() on the LoginContext?

            LoginContext loginContext = new LoginContext("", null, null, new Configuration()
            {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name)
                {
                    ImmutableMap.Builder<String, String> optionsBuilder = ImmutableMap.builder();
                    optionsBuilder.put("refreshKrb5Config", "true");
                    optionsBuilder.put("doNotPrompt", "true");
                    optionsBuilder.put("useKeyTab", "true");
                    if (LOG.isDebugEnabled()) {
                        optionsBuilder.put("debug", "true");
                    }

                    if (keytab != null) {
                        optionsBuilder.put("keyTab", keytab.getAbsolutePath());
                    }

                    if (credentialCache != null) {
                        optionsBuilder.put("ticketCache", credentialCache.getAbsolutePath());
                        optionsBuilder.put("useTicketCache", "true");
                        optionsBuilder.put("renewTGT", "true");
                    }

                    if (principal != null) {
                        optionsBuilder.put("principal", principal);
                    }

                    return new AppConfigurationEntry[] {
                            new AppConfigurationEntry(Krb5LoginModule.class.getName(), REQUIRED, optionsBuilder.build())
                    };
                }
            });

            loginContext.login();
            Subject subject = loginContext.getSubject();
            Principal clientPrincipal = subject.getPrincipals().iterator().next();
            GSSCredential clientCredential = doAs(subject, () -> GSS_MANAGER.createCredential(
                    GSS_MANAGER.createName(clientPrincipal.getName(), NT_USER_NAME),
                    DEFAULT_LIFETIME,
                    KERBEROS_OID,
                    INITIATE_ONLY));

            clientSession = new Session(loginContext, clientCredential);
        }

        return clientSession;
    }

    private static String makeServicePrincipal(String serviceName, String hostName, boolean useCanonicalHostname)
    {
        String serviceHostName = hostName;
        if (useCanonicalHostname) {
            serviceHostName = canonicalizeServiceHostname(hostName);
        }
        return format("%s@%s", serviceName, serviceHostName.toLowerCase(Locale.US));
    }

    private static String canonicalizeServiceHostname(String hostName)
    {
        try {
            InetAddress address = InetAddress.getByName(hostName);
            String fullHostName;
            if ("localhost".equalsIgnoreCase(address.getHostName())) {
                fullHostName = InetAddress.getLocalHost().getCanonicalHostName();
            }
            else {
                fullHostName = address.getCanonicalHostName();
            }
            checkState(!fullHostName.equalsIgnoreCase("localhost"), "Fully qualified name of localhost should not resolve to 'localhost'. System configuration error?");
            return fullHostName;
        }
        catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }
    }

    private interface GssSupplier<T>
    {
        T get()
                throws GSSException;
    }

    private static <T> T doAs(Subject subject, GssSupplier<T> action)
    {
        return Subject.doAs(subject, (PrivilegedAction<T>) () -> {
            try {
                return action.get();
            }
            catch (GSSException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static class Session
    {
        private final LoginContext loginContext;
        private final GSSCredential clientCredential;

        public Session(LoginContext loginContext, GSSCredential clientCredential)
                throws LoginException
        {
            requireNonNull(loginContext, "loginContext is null");
            requireNonNull(clientCredential, "gssCredential is null");

            this.loginContext = loginContext;
            this.clientCredential = clientCredential;
        }

        public LoginContext getLoginContext()
        {
            return loginContext;
        }

        public GSSCredential getClientCredential()
        {
            return clientCredential;
        }
    }
}
