package io.airlift.http.client.spnego;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.sun.security.auth.module.Krb5LoginModule;
import io.airlift.log.Logger;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Attributes;
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

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
import static org.ietf.jgss.GSSContext.INDEFINITE_LIFETIME;

public class SpnegoAuthentication
        implements Authentication
{
    private static final String NEGOTIATE = HttpHeader.NEGOTIATE.asString();
    private static final Logger LOG = Logger.get(SpnegoAuthentication.class);
    private static final int MIN_CREDENTIAL_LIFE_TIME_SEC = 60;

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
    private final AtomicReference<CredentialHolder> credentialHolder = new AtomicReference<>();

    public SpnegoAuthentication(File keytab, File kerberosConfig, File credentialCache, String principal, String remoteServiceName)
    {
        requireNonNull(kerberosConfig, "kerberosConfig is null");
        requireNonNull(remoteServiceName, "remoteServiceName is null");

        this.keytab = keytab;
        this.credentialCache = credentialCache;
        this.principal = principal;
        this.remoteServiceName = remoteServiceName;

        System.setProperty("java.security.krb5.conf", kerberosConfig.getAbsolutePath());
    }

    public void shutdown()
    {
        try {
            if (credentialHolder.get() != null) {
                credentialHolder.get().getLoginContext().logout();
            }
        }
        catch (LoginException e) {
            Throwables.propagate(e);
        }
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
                    CredentialHolder updatedCredentialHolder = getUpdatedCredentialHolder();
                    String servicePrincipal = makeServicePrincipal(remoteServiceName, normalizedUri.getHost());
                    context = doAs(updatedCredentialHolder.getLoginContext().getSubject(), () -> {
                            GSSContext result = GSS_MANAGER.createContext(
                                    GSS_MANAGER.createName(servicePrincipal, GSSName.NT_HOSTBASED_SERVICE),
                                    SPNEGO_OID,
                                    updatedCredentialHolder.getGssCredential(),
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

    private CredentialHolder getUpdatedCredentialHolder()
            throws LoginException, GSSException
    {
        if (credentialHolder.get() == null || credentialHolder.get().getGssCredential().getRemainingLifetime() <= MIN_CREDENTIAL_LIFE_TIME_SEC) {
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
                        optionsBuilder.put("keytab", keytab.getAbsolutePath());
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
            GSSCredential gssCredential = doAs(subject, () -> GSS_MANAGER.createCredential(
                    GSS_MANAGER.createName(clientPrincipal.getName(), GSSName.NT_USER_NAME),
                    GSSCredential.DEFAULT_LIFETIME,
                    KERBEROS_OID,
                    GSSCredential.INITIATE_ONLY));

            CredentialHolder newCredentialHolder = new CredentialHolder(loginContext, gssCredential);
            credentialHolder.set(newCredentialHolder);
        }
        return credentialHolder.get();
    }

    private static String makeServicePrincipal(String serviceName, String hostName)
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

            return format("%s@%s", serviceName, fullHostName.toLowerCase(Locale.US));
        }
        catch (UnknownHostException e) {
            throw Throwables.propagate(e);
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
                throw Throwables.propagate(e);
            }
        });
    }

    private static class CredentialHolder
    {
        private final LoginContext loginContext;
        private final GSSCredential gssCredential;

        public CredentialHolder(LoginContext loginContext, GSSCredential gssCredential)
                throws LoginException
        {
            requireNonNull(loginContext, "loginContext is null");
            requireNonNull(gssCredential, "gssCredential is null");

            this.loginContext = loginContext;
            this.gssCredential = gssCredential;
        }

        public LoginContext getLoginContext()
        {
            return loginContext;
        }

        public GSSCredential getGssCredential()
        {
            return gssCredential;
        }
    }
}
