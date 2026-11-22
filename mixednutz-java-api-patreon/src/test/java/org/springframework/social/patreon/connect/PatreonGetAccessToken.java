package org.springframework.social.patreon.connect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.social.connect.Connection;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.OAuth2Operations;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.social.patreon.api.PatreonClient;

/**
 * Command-line utility that walks through Patreon's OAuth2 flow by hand and prints
 * the resulting access token, expiry and refresh token.
 * <p>
 * Credentials are never stored in this file. Supply them as environment variables
 * (the same names app-server uses) or as JVM system properties:
 * <pre>
 *   PATREON_CLIENT_ID      / -Dpatreon.clientId      (required)
 *   PATREON_CLIENT_SECRET  / -Dpatreon.clientSecret  (required)
 *   PATREON_REDIRECT_URL   / -Dpatreon.redirectUrl   (optional, default https://localhost:8443/patreon)
 * </pre>
 * The redirect URL must be registered on the Patreon OAuth client. After you approve
 * access in the browser, Patreon redirects to it with {@code ?code=...}. Copy that
 * code value and paste it at the prompt. Nothing needs to be listening at the
 * redirect URL.
 */
public class PatreonGetAccessToken {

	private static final String DEFAULT_REDIRECT_URL = "https://localhost:8443/patreon";

	public static void main(String[] args) throws IOException {
		String clientId = setting("PATREON_CLIENT_ID", "patreon.clientId", null);
		String clientSecret = setting("PATREON_CLIENT_SECRET", "patreon.clientSecret", null);
		String redirectUrl = setting("PATREON_REDIRECT_URL", "patreon.redirectUrl", DEFAULT_REDIRECT_URL);
		if (clientId == null || clientSecret == null) {
			System.err.println("Set PATREON_CLIENT_ID and PATREON_CLIENT_SECRET (environment variables), "
					+ "or -Dpatreon.clientId and -Dpatreon.clientSecret.");
			System.exit(1);
		}

		PatreonConnectionFactory connectionFactory = new PatreonConnectionFactory(clientId, clientSecret);
		OAuth2Operations oauth = connectionFactory.getOAuthOperations();

		OAuth2Parameters oauthParams = new OAuth2Parameters();
		oauthParams.setRedirectUri(redirectUrl);

		String authUrl = oauth.buildAuthenticateUrl(oauthParams);
		System.out.println("Put this URL into a browser:");
		System.out.println(authUrl);
		System.out.println();

		System.out.print("Enter code:");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String authCode = reader.readLine();

		AccessGrant grant = oauth.exchangeForAccess(authCode, redirectUrl, oauthParams);
		System.out.println("Access Token: "+grant.getAccessToken());
		System.out.println("Current Time: "+LocalDateTime.now());
		System.out.println("Expire Time: "+grant.getExpireTime()+"("+Instant.ofEpochMilli(grant.getExpireTime())+")");
		System.out.println("Refresh Token: "+grant.getRefreshToken());

		Connection<PatreonClient> connection = connectionFactory.createConnection(grant);
		if (connection.hasExpired()) {
			System.out.println("Expired!");
			connection.refresh();
			if (connection.hasExpired()) {
				System.out.println("Still Expired!");
			}
		}
	}

	/** Environment variable first, then system property, then the default. */
	private static String setting(String envName, String propertyName, String defaultValue) {
		String value = System.getenv(envName);
		if (value == null || value.isEmpty()) {
			value = System.getProperty(propertyName);
		}
		return (value == null || value.isEmpty()) ? defaultValue : value;
	}

}
