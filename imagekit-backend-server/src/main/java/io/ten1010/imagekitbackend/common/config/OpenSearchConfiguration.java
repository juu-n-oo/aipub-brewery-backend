package io.ten1010.imagekitbackend.common.config;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * OpenSearch 클라이언트 Bean. {@code imagekit.opensearch.enabled=true} 일 때만 생성된다.
 * opensearch-java 3.x 의 ApacheHttpClient5 transport 를 사용하며, basic auth 와
 * TLS(CA 신뢰 또는 trust-all) 를 와이어링한다.
 */
@Configuration
public class OpenSearchConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "imagekit.opensearch", name = "enabled", havingValue = "true")
    public OpenSearchClient openSearchClient(OpenSearchProperties properties) throws Exception {
        Objects.requireNonNull(properties.getUrl(), "imagekit.opensearch.url must be configured");

        URI uri = URI.create(properties.getUrl());
        String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
        int port = uri.getPort() != -1 ? uri.getPort() : 9200;
        HttpHost host = new HttpHost(scheme, uri.getHost(), port);

        SSLContext sslContext = buildSslContext(properties);
        boolean verifySsl = properties.isVerifySsl();

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (properties.getUsername() != null) {
            credentialsProvider.setCredentials(
                    new AuthScope(host),
                    new UsernamePasswordCredentials(
                            properties.getUsername(),
                            properties.getPassword() != null ? properties.getPassword().toCharArray() : new char[0]));
        }

        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper())
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

                    if ("https".equalsIgnoreCase(scheme)) {
                        ClientTlsStrategyBuilder tlsBuilder = ClientTlsStrategyBuilder.create()
                                .setSslContext(sslContext)
                                // TlsDetails 콜백을 명시하지 않으면 일부 환경에서 ALPN/HTTP2 협상 이슈가 발생할 수 있어
                                // HTTP/1.1 로 고정한다.
                                .setTlsDetailsFactory(sslEngine ->
                                        new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol()));
                        if (!verifySsl) {
                            tlsBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                        }
                        httpClientBuilder.setConnectionManager(
                                PoolingAsyncClientConnectionManagerBuilder.create()
                                        .setTlsStrategy(tlsBuilder.build())
                                        .build());
                    }
                    return httpClientBuilder;
                });

        OpenSearchTransport transport = builder.build();
        return new OpenSearchClient(transport);
    }

    private SSLContext buildSslContext(OpenSearchProperties properties) throws Exception {
        if (!properties.isVerifySsl()) {
            return buildTrustAllContext();
        }
        if (properties.getCaCertPath() == null || properties.getCaCertPath().isBlank()) {
            // verifySsl=true 이고 CA 경로가 없으면 JVM 기본 truststore 사용.
            return SSLContext.getDefault();
        }
        return buildCaTrustingContext(properties.getCaCertPath());
    }

    private SSLContext buildCaTrustingContext(String caCertPath) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate caCert;
        try (InputStream caInput = new FileInputStream(caCertPath)) {
            caCert = certificateFactory.generateCertificate(caInput);
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("opensearch-ca", caCert);

        javax.net.ssl.TrustManagerFactory trustManagerFactory =
                javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private SSLContext buildTrustAllContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    @Nullable
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        return sslContext;
    }

}
