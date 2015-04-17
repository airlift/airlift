package io.airlift.security.utils;

import com.google.common.base.Throwables;
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class KerberosUtil
{
    private static final Oid SPNEGO_OID;
    private static final Oid KERBEROS_OID;
    private static final Oid[] SUPPORTED_OIDS;
    private static final Class<?> KRB5_CONFIG_CLASS;

    static {
        try {
            SPNEGO_OID = new Oid("1.3.6.1.5.5.2");
            KERBEROS_OID = new Oid("1.2.840.113554.1.2.2");
            SUPPORTED_OIDS = new Oid[] {SPNEGO_OID, KERBEROS_OID};
            KRB5_CONFIG_CLASS = Class.forName("sun.security.krb5.Config");
        }
        catch (GSSException | ClassNotFoundException e) {
            throw new AuthenticationException(e);
        }
    }

    /* Return the Kerberos login module name */
    public static String getKrb5LoginModuleName()
    {
        return System.getProperty("java.vendor").contains("IBM")
                ? "com.ibm.security.auth.module.Krb5LoginModule"
                : "com.sun.security.auth.module.Krb5LoginModule";
    }

    public static Oid getOidInstance(String oidName)
            throws GSSException, ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException
    {
        Class<?> oidClass;
        if (System.getProperty("java.vendor").contains("IBM")) {
            oidClass = Class.forName("com.ibm.security.jgss.GSSUtil");
        }
        else {
            oidClass = Class.forName("sun.security.jgss.GSSUtil");
        }
        Field oidField = oidClass.getDeclaredField(oidName);
        return (Oid) oidField.get(oidClass);
    }

    public static String getDefaultRealm()
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException,
            InvocationTargetException
    {
        Method getInstanceMethod = KRB5_CONFIG_CLASS.getMethod("getInstance", new Class[0]);
        Object kerbConf = getInstanceMethod.invoke(KRB5_CONFIG_CLASS, new Object[0]);
        Method getDefaultRealmMethod = KRB5_CONFIG_CLASS.getDeclaredMethod("getDefaultRealm", new Class[0]);
        return (String) getDefaultRealmMethod.invoke(kerbConf, new Object[0]);
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
    public static final String getServicePrincipal(String serviceName, String hostName)
            throws UnknownHostException
    {
        InetAddress address = hostName == null ? null : InetAddress.getByName(hostName);
        String fqdn;
        if (address == null || address.getHostName().toLowerCase().startsWith("localhost")) {
            fqdn = InetAddress.getLocalHost().getCanonicalHostName();
            checkState(!fqdn.equalsIgnoreCase("localhost"), "Fully qualified host name for localhost is not retrieved");
        }
        else {
            fqdn = address.getCanonicalHostName();
        }

        return String.format("%s/%s", serviceName, fqdn.toLowerCase(Locale.US));
    }

    public static Subject getSubject(String subjectName, boolean client)
            throws LoginException, UnknownHostException
    {
        KerberosConfiguration kerberosConfiguration = new KerberosConfiguration(subjectName, client);
        String configName = client ? KerberosConfiguration.GSS_CLIENT : KerberosConfiguration.GSS_SERVER;
        LoginContext context = new LoginContext(configName, null, null, kerberosConfiguration);
        context.login();
        return context.getSubject();
    }

    public static GSSContext getGssContext(Subject subject, String servicePrincipal, boolean initiator)
    {
        return Subject.doAs(subject, (PrivilegedAction<GSSContext>) () -> {
            GSSContext context = null;
            try {
                Principal subjectPrincipal = subject.getPrincipals().iterator().next();
                GSSManager gssManager = GSSManager.getInstance();

                if (initiator) {
                    GSSName gssUserName = gssManager.createName(subjectPrincipal.getName(), GSSName.NT_USER_NAME);
                    GSSCredential clientCredential = gssManager.createCredential(gssUserName, GSSCredential.DEFAULT_LIFETIME,
                            KERBEROS_OID, GSSCredential.INITIATE_ONLY);

                    GSSName gssServiceName = gssManager.createName(servicePrincipal, null);
                    context = gssManager.createContext(gssServiceName, SPNEGO_OID, clientCredential, GSSContext.INDEFINITE_LIFETIME);
                }
                else {
                    GSSName gssServiceName = gssManager.createName(servicePrincipal, null);
                    GSSCredential serverCredential = gssManager.createCredential(gssServiceName, GSSCredential.INDEFINITE_LIFETIME,
                            SUPPORTED_OIDS, GSSCredential.ACCEPT_ONLY);
                    context = gssManager.createContext(serverCredential);
                }
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

    public static class KerberosConfiguration
            extends Configuration
    {
        public static final String GSS_SERVER = "gss-server";
        public static final String GSS_CLIENT = "gss-client";
        private static final Logger log = Logger.get(KerberosConfiguration.class);
        private final String principal;

        public KerberosConfiguration(String subjectName, boolean fromClient)
                throws UnknownHostException
        {
            if (fromClient) {
                this.principal = subjectName;
            }
            else {
                this.principal = KerberosUtil.getServicePrincipal(subjectName, null);
            }
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name)
        {
            Map<String, String> options = new HashMap<>();
            options.put("refreshKrb5Config", "true");
            options.put("doNotPrompt", "true");
            if (log.isDebugEnabled()) {
                options.put("debug", "true");
            }
            switch (name) {
                case GSS_SERVER:
                    options.put("isInitiator", "false");
                    options.put("useKeyTab", "true");
                    if (principal != null) {
                        options.put("principal", principal);
                    }
                    options.put("storeKey", "true");
                    break;
                case GSS_CLIENT:
                    String ticketCache = System.getenv("KRB5CCNAME");
                    if (ticketCache != null) {
                        options.put("ticketCache", ticketCache);
                        options.put("useTicketCache", "true");
                        options.put("renewTGT", "true");
                    }
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Krb5LoginModule configuration name %s is invalid. it should be one of %s and %s",
                            name, GSS_CLIENT, GSS_SERVER));
            }

            return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(KerberosUtil.getKrb5LoginModuleName(),
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            options)};
        }
    }
}
