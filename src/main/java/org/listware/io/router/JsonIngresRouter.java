package org.listware.io.router;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.flink.statefun.sdk.io.IngressIdentifier;
import org.apache.flink.statefun.sdk.io.IngressSpec;
import org.apache.flink.statefun.sdk.io.Router;
import org.apache.flink.statefun.sdk.kafka.KafkaIngressBuilder;
import org.apache.flink.statefun.sdk.reqreply.generated.TypedValue;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.listware.io.utils.Constants;
import org.listware.io.utils.TypedValueDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonIngresRouter implements Router<TypedValue> {
	private static final Logger LOG = LoggerFactory.getLogger(JsonIngresRouter.class);

	private static final String INGRESS_TOPIC_NAME = "router.system";
	private static final String GROUP_ID = "group.system";

	public static final IngressIdentifier<TypedValue> INGRESS = new IngressIdentifier<>(TypedValue.class,
			"ui.srview.app", INGRESS_TOPIC_NAME);

	public static IngressSpec<TypedValue> INGRESS_SPEC = KafkaIngressBuilder.forIdentifier(INGRESS)
			.withProperty(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID).withKafkaAddress(Constants.Kafka.Addr())
			.withTopic(INGRESS_TOPIC_NAME).withDeserializer(TypedValueDeserializer.class).build();

	private KeycloakDeployment keycloakDeployment = null;

	private static String host = "https://localhost:8443";
	private static String realm = "master";

	public JsonIngresRouter() {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllCerts, new SecureRandom());

			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
			CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

			AdapterConfig adapterConfig = new AdapterConfig();
			adapterConfig.setRealm(realm);
			adapterConfig.setAuthServerUrl(host);
			adapterConfig.setResource("");

			keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);
			keycloakDeployment.setClient(httpclient);

		} catch (NoSuchAlgorithmException e) {
			LOG.error(e.getLocalizedMessage());
		} catch (KeyManagementException e) {
			LOG.error(e.getLocalizedMessage());
		}

	}

	@Override
	public void route(TypedValue message, Downstream<TypedValue> downstream) {

		if (keycloakDeployment != null) {
			try {
				String token = "";

				JWSInput jwsInput = new JWSInput(token);

				AccessToken accessToken = AdapterTokenVerifier.verifyToken(jwsInput.getWireString(),
						keycloakDeployment);

				LOG.info("User " + accessToken.getPreferredUsername() + " scope " + accessToken.getScope());
			} catch (Exception e) {
				LOG.error(e.getLocalizedMessage());
			}
		}

	}

	private static TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		}

		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		}
	} };

}
