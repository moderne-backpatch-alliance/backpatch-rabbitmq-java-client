// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** A TLS server on loopback, with a self-signed certificate for a host that is not localhost. */
public class TlsDefaultVerificationTest {

    private static final char[] PASSWORD = "password".toCharArray();

    private X509Certificate certificate;
    private SSLServerSocket serverSocket;
    private Thread serverThread;

    @Before
    public void startServer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=not-this-host.example");
        long now = System.currentTimeMillis();
        certificate = new JcaX509CertificateConverter().getCertificate(
            new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE,
                new Date(now - TimeUnit.DAYS.toMillis(1)), new Date(now + TimeUnit.DAYS.toMillis(1)),
                name, keyPair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), PASSWORD, new X509Certificate[] {certificate});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD);
        SSLContext serverContext = SSLContext.getInstance("TLSv1.2");
        serverContext.init(kmf.getKeyManagers(), null, null);

        serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
            .createServerSocket(0, 5, InetAddress.getLoopbackAddress());
        serverThread = new Thread(this::acceptAndHandshake, "tls-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void stopServer() throws Exception {
        serverSocket.close();
        serverThread.join(TimeUnit.SECONDS.toMillis(5));
    }

    private void acceptAndHandshake() {
        ServerSocket server = serverSocket;
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5000);
                ((SSLSocket) socket).startHandshake();
            } catch (IOException e) {
                // the client refusing the certificate ends up here
            }
        }
    }

    private void handshake(SocketFactory socketFactory, ConnectionFactory cf) throws IOException {
        try (Socket socket = socketFactory.createSocket()) {
            cf.getSocketConfigurator().configure(socket);
            socket.setSoTimeout(5000);
            socket.connect(new InetSocketAddress("localhost", serverSocket.getLocalPort()), 5000);
            ((SSLSocket) socket).startHandshake();
        }
    }

    private SSLContext contextTrustingTheServer() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", certificate);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

    @Test
    public void useSslProtocolRefusesCertificateNotInDefaultTrustStore() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useSslProtocol();
        assertThatThrownBy(() -> handshake(cf.getSocketFactory(), cf))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void useSslProtocolWithProtocolRefusesCertificateNotInDefaultTrustStore() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useSslProtocol("TLSv1.2");
        assertThatThrownBy(() -> handshake(cf.getSocketFactory(), cf))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void amqpsUriRefusesCertificateNotInDefaultTrustStore() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.setUri("amqps://localhost");
        assertThatThrownBy(() -> handshake(cf.getSocketFactory(), cf))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void useSslProtocolWithContextVerifiesHostname() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useSslProtocol(contextTrustingTheServer());
        assertThatThrownBy(() -> handshake(cf.getSocketFactory(), cf))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void sslContextFactoryVerifiesHostname() throws Exception {
        SSLContext context = contextTrustingTheServer();
        ConnectionFactory cf = new ConnectionFactory();
        cf.setSslContextFactory(name -> context);
        assertThatThrownBy(() -> handshake(context.getSocketFactory(), cf))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void useTlsWithNoVerificationAcceptsTheServer() throws Exception {
        ConnectionFactory cf = new ConnectionFactory();
        cf.useTlsWithNoVerification();
        handshake(cf.getSocketFactory(), cf);
        assertThat(cf.isSSL()).isTrue();
    }
}
