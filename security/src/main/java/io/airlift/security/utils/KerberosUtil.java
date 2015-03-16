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

    static {
        try {
            SPNEGO_OID = new Oid("1.3.6.1.5.5.2");
            KERBEROS_OID = new Oid("1.2.840.113554.1.2.2");
            SUPPORTED_OIDS = new Oid[]{SPNEGO_OID, KERBEROS_OID};
        } catch (GSSException e) {
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
        Object kerbConf;
        Class<?> classRef;
        Method getInstanceMethod;
        Method getDefaultRealmMethod;
        if (System.getProperty("java.vendor").contains("IBM")) {
            classRef = Class.forName("com.ibm.security.krb5.internal.Config");
        }
        else {
            classRef = Class.forName("sun.security.krb5.Config");
        }
        getInstanceMethod = classRef.getMethod("getInstance", new Class[0]);
        kerbConf = getInstanceMethod.invoke(classRef, new Object[0]);
        getDefaultRealmMethod = classRef.getDeclaredMethod(
                "getDefaultRealm",
                new Class[0]);
        return (String) getDefaultRealmMethod.invoke(kerbConf, new Object[0]);
    }

    /**
     * Create Kerberos principal for a given service and hostname. It converts
     * hostname to lower case. If hostname is null or "0.0.0.0", it uses
     * dynamically looked-up fqdn of the current host instead.
     *
     * @param serviceName Service for which you want to generate the principal.
     * @param serviceHostname Fully-qualified domain name.
     * @return Converted Kerberos principal name.
     * @throws UnknownHostException If no IP address for the local host could be found.
     */
    public static final String getServicePrincipal(String serviceName, String serviceHostname)
            throws UnknownHostException
    {
        String fqdn = serviceHostname;
        if (null == fqdn || fqdn.equals("") || fqdn.equals("0.0.0.0") || fqdn.equals("localhost")) {
            fqdn = InetAddress.getLocalHost().getCanonicalHostName();
            ;
        }
        return serviceName + "/" + fqdn.toLowerCase(Locale.US);
    }

    public static Subject getSubject(String subjectName, String krb5Conf, Boolean client)
            throws LoginException, UnknownHostException
    {
        KerberosConfiguration kerberosConfiguration = new KerberosConfiguration(subjectName, krb5Conf, client);
        String configName = client ? KerberosConfiguration.GSS_CLIENT : KerberosConfiguration.GSS_SERVER;
        LoginContext context = new LoginContext(configName, null, null, kerberosConfiguration);
        context.login();
        return context.getSubject();
    }

    public static GSSContext getGssContext(final Subject subject, final String servicePrincipal, final boolean initiator)
    {
        GSSContext gssContext = null;
        gssContext = Subject.doAs(subject, new PrivilegedAction<GSSContext>()
        {
            public GSSContext run()
            {
                GSSContext context = null;
                try {
                    Principal subjectPrincipal = subject.getPrincipals().iterator().next();
                    GSSManager gssManager = GSSManager.getInstance();

                    if (initiator) {
                        GSSName gssUserName = gssManager.createName(subjectPrincipal.getName(), GSSName.NT_USER_NAME);
                        GSSCredential clientCredential = gssManager.createCredential(
                                gssUserName,
                                GSSCredential.DEFAULT_LIFETIME,
                                KERBEROS_OID,
                                GSSCredential.INITIATE_ONLY);

                        GSSName gssServiceName = gssManager.createName(
                                servicePrincipal,
                                null);

                        context = gssManager.createContext(
                                gssServiceName,
                                SPNEGO_OID,
                                clientCredential,
                                GSSContext.INDEFINITE_LIFETIME);
                    }
                    else {
                        GSSName gssServiceName = gssManager.createName(servicePrincipal, null);
                        GSSCredential serverCredential = gssManager.createCredential(
                                gssServiceName,
                                GSSCredential.INDEFINITE_LIFETIME,
                                SUPPORTED_OIDS,
                                GSSCredential.ACCEPT_ONLY);

                        context = gssManager.createContext(serverCredential);
                    }
                    context.requestMutualAuth(true);
                    context.requestConf(true);
                    context.requestInteg(true);
                    context.requestCredDeleg(false);

                }
                catch (GSSException e) {
                    throw Throwables.propagate(e);
                }
                return context;
            }
        });
        return gssContext;
    }

    public static class KerberosConfiguration
            extends Configuration
    {
        public static final String GSS_SERVER = "gss-server";
        public static final String GSS_CLIENT = "gss-client";
        private final String principal;
        private final String krb5Conf;
        private static final Logger log = Logger.get(KerberosConfiguration.class);

        public KerberosConfiguration(String subjectName, String krb5Config, boolean fromClient)
                throws UnknownHostException
        {
            if (fromClient) {
                this.principal = subjectName;
            }
            else {
                this.principal = KerberosUtil.getServicePrincipal(subjectName, null);
            }
            this.krb5Conf = krb5Config;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name)
        {
            System.setProperty("java.security.krb5.conf", krb5Conf);
            String ticketCache = System.getenv("KRB5CCNAME");

            Map<String, String> options = new HashMap<String, String>();
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
                    if (ticketCache != null) {
                        options.put("ticketCache", ticketCache);
                        options.put("useTicketCache", "true");
                        options.put("renewTGT", "true");
                    }
                    break;
                default:
                    checkState(name == GSS_SERVER || name == GSS_CLIENT,
                            String.format("Krb5LoginModule configuration name %s is invalid. it should be one of %s and %s",
                                    name,
                                    GSS_CLIENT,
                                    GSS_SERVER));
                    break;
            }

            return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(KerberosUtil.getKrb5LoginModuleName(),
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            options)};
        }
    }
}
