package io.airlift.security.mtls;

import com.google.common.collect.ImmutableList;
import io.airlift.node.AddressToHostname;

import javax.security.auth.x500.X500Principal;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.security.cert.CertificateBuilder.certificateBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.list;

public final class AutomaticMtls
{
    private AutomaticMtls() {}

    public static void addServerKeyAndCertificateForCurrentNode(String sharedSecret, KeyStore keyStore, String commonName, String keyManagerPassword)
    {
        try {
            KeyPair keyPair = fromSharedSecret(sharedSecret);

            X500Principal subject = certificateSubject(commonName);
            LocalDate notBefore = LocalDate.now();
            LocalDate notAfter = notBefore.plusYears(10);
            List<InetAddress> allLocalIpAddresses = getAllLocalIpAddresses();
            List<String> ipAddressMappedNames = allLocalIpAddresses.stream()
                    .map(AddressToHostname::encodeAddressAsHostname)
                    .collect(toImmutableList());
            X509Certificate certificateServer = certificateBuilder()
                    .setKeyPair(keyPair)
                    .setSerialNumber(System.currentTimeMillis())
                    .setIssuer(subject)
                    .setNotBefore(notBefore)
                    .setNotAfter(notAfter)
                    .setSubject(subject)
                    .addSanIpAddresses(allLocalIpAddresses)
                    .addSanDnsNames(ipAddressMappedNames)
                    .buildSelfSigned();

            char[] password = keyManagerPassword == null ? new char[0] : keyManagerPassword.toCharArray();
            keyStore.setKeyEntry(commonName, keyPair.getPrivate(), password, new Certificate[] {certificateServer});
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void addClientTrust(String sharedSecret, KeyStore keyStore, String commonName)
    {
        try {
            KeyPair keyPair = fromSharedSecret(sharedSecret);

            X500Principal subject = certificateSubject(commonName);
            LocalDate notBefore = LocalDate.now();
            LocalDate notAfter = notBefore.plusYears(10);
            X509Certificate certificateServer = certificateBuilder()
                    .setKeyPair(keyPair)
                    .setSerialNumber(System.currentTimeMillis())
                    .setIssuer(subject)
                    .setNotBefore(notBefore)
                    .setNotAfter(notAfter)
                    .setSubject(subject)
                    .buildSelfSigned();

            keyStore.setCertificateEntry(commonName, certificateServer);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<InetAddress> getAllLocalIpAddresses()
            throws SocketException
    {
        ImmutableList.Builder<InetAddress> list = ImmutableList.builder();
        for (NetworkInterface networkInterface : list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress address : list(networkInterface.getInetAddresses())) {
                if (!address.isAnyLocalAddress() && !address.isLinkLocalAddress() && !address.isMulticastAddress()) {
                    list.add(address);
                }
            }
        }
        return list.build();
    }

    private static KeyPair fromSharedSecret(String sharedSecret)
    {
        try {
            byte[] seed = sharedSecret.getBytes(UTF_8);
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
            return generator.generateKeyPair();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static X500Principal certificateSubject(String commonName)
    {
        return new X500Principal("CN=" + commonName);
    }
}
