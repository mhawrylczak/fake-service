package pl.allegro.edge.fake;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.IoUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;


public class Server {

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    public static void main(final String[] args) throws URISyntaxException {

        String bindhost = "localhost";
        if( System.getProperty("bindhost") != null ){
            bindhost = System.getProperty("bindhost");
        }

        Undertow server = Undertow.builder()
                .addHttpListener(8080, bindhost)
                .addAjpListener(8090, bindhost)
                .addHttpsListener(8443, bindhost, getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_SPDY, true)
                .setHandler(path()
                                .addPrefixPath("/wschat", websocket(new WebSocketConnectionCallback() {

                                    @Override
                                    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                                        channel.getReceiveSetter().set(new AbstractReceiveListener() {

                                            @Override
                                            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                                final String messageData = message.getData();
                                                for (WebSocketChannel session : channel.getPeerConnections()) {
                                                    WebSockets.sendText(messageData, session, null);
                                                }
                                            }
                                        });
                                        channel.resumeReceives();
                                    }

                                }))
                                .addPrefixPath("/chat", resource(new ClassPathResourceManager(Server.class.getClassLoader(), Server.class.getPackage()))
                                        .addWelcomeFiles("chat.html"))
                                .addPrefixPath("/large", resource(new ClassPathResourceManager(Server.class.getClassLoader(), Server.class.getPackage()))
                                        .addWelcomeFiles("large.json"))
                                .addPrefixPath("/medium", resource(new ClassPathResourceManager(Server.class.getClassLoader(), Server.class.getPackage()))
                                        .addWelcomeFiles("medium.json"))
                                .addPrefixPath("/small", resource(new ClassPathResourceManager(Server.class.getClassLoader(), Server.class.getPackage()))
                                        .addWelcomeFiles("small.json"))
                                .addPrefixPath("/tiny", resource(new ClassPathResourceManager(Server.class.getClassLoader(), Server.class.getPackage()))
                                        .addWelcomeFiles("tiny.json"))
                )
                .build();

        server.start();
    }

    public static SSLContext getServerSslContext() {
        try {
            return createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws IOException {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        } catch (UnrecoverableKeyException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        } catch (KeyStoreException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        }

        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        } catch (KeyStoreException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        }

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        } catch (KeyManagementException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        }

        return sslContext;
    }

    private static KeyStore loadKeyStore(final String name) throws IOException {
        final InputStream stream = Server.class.getClassLoader().getResourceAsStream(name);
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);

            return loadedKeystore;
        } catch (KeyStoreException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } catch (CertificateException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }
}
