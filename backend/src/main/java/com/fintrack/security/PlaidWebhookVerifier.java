package com.fintrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies Plaid webhook authenticity per Plaid's documented scheme:
 * <ol>
 *   <li>The {@code Plaid-Verification} header is a JWT (ES256) signed by Plaid.</li>
 *   <li>Its {@code kid} identifies a public key fetched from
 *       {@code /webhook_verification_key/get} (cached).</li>
 *   <li>After verifying the signature we confirm the SHA-256 of the exact raw
 *       request body matches the {@code request_body_sha256} claim, and that the
 *       token was issued recently.</li>
 * </ol>
 * Any failure returns {@code false}; callers must reject the request. Without a
 * configured Plaid client we cannot fetch keys, so verification always fails —
 * a deployment without Plaid has no legitimate webhooks to receive.
 */
@Component
@Slf4j
public class PlaidWebhookVerifier {

    /** Reject tokens older than this to blunt replay of captured webhooks. */
    private static final long MAX_AGE_SECONDS = 300;
    /** Bound the key cache so forged requests with random kids can't force unbounded key fetches. */
    private static final int MAX_CACHED_KEYS = 25;

    private final WebClient plaidWebClient;

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    public PlaidWebhookVerifier(@Qualifier("plaidWebClient") WebClient plaidWebClient) {
        this.plaidWebClient = plaidWebClient;
    }

    public boolean verify(String verificationHeader, byte[] rawBody) {
        if (verificationHeader == null || verificationHeader.isBlank() || rawBody == null) {
            return false;
        }
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
            log.warn("Rejecting Plaid webhook: Plaid is not configured, cannot verify signature");
            return false;
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(header -> {
                        if (!(header instanceof JwsHeader jwsHeader)) {
                            throw new JwtException("Not a signed JWT");
                        }
                        if (!"ES256".equals(jwsHeader.getAlgorithm())) {
                            throw new JwtException("Unexpected JWT algorithm: " + jwsHeader.getAlgorithm());
                        }
                        return resolveKey(jwsHeader.getKeyId());
                    })
                    .build()
                    .parseSignedClaims(verificationHeader);

            Claims claims = jws.getPayload();

            Date issuedAt = claims.getIssuedAt();
            if (issuedAt == null
                    || issuedAt.toInstant().isBefore(Instant.now().minusSeconds(MAX_AGE_SECONDS))) {
                log.warn("Rejecting Plaid webhook: verification token missing or too old");
                return false;
            }

            Object expected = claims.get("request_body_sha256");
            if (!(expected instanceof String expectedHash) || expectedHash.isBlank()) {
                return false;
            }

            String actualHash = sha256Hex(rawBody);
            // Constant-time comparison to avoid leaking hash bytes via timing.
            boolean matches = MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    actualHash.getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                log.warn("Rejecting Plaid webhook: body hash mismatch");
            }
            return matches;
        } catch (Exception e) {
            log.warn("Plaid webhook verification failed: {}", e.getMessage());
            return false;
        }
    }

    private PublicKey resolveKey(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new JwtException("Missing key id");
        }
        PublicKey cached = keyCache.get(kid);
        if (cached != null) {
            return cached;
        }
        if (keyCache.size() >= MAX_CACHED_KEYS) {
            throw new JwtException("Verification key cache is full; refusing to fetch new key");
        }
        PublicKey fetched = fetchKey(kid);
        keyCache.put(kid, fetched);
        return fetched;
    }

    @SuppressWarnings("unchecked")
    private PublicKey fetchKey(String kid) {
        Map<String, Object> resp = plaidWebClient.post()
                .uri("/webhook_verification_key/get")
                .bodyValue(Map.of("client_id", clientId, "secret", secret, "key_id", kid))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(15));

        if (resp == null || !(resp.get("key") instanceof Map)) {
            throw new JwtException("No verification key returned for kid " + kid);
        }
        Map<String, Object> jwk = (Map<String, Object>) resp.get("key");
        String x = (String) jwk.get("x");
        String y = (String) jwk.get("y");
        if (x == null || y == null) {
            throw new JwtException("Verification key JWK missing coordinates");
        }
        return buildEcPublicKey(x, y);
    }

    private PublicKey buildEcPublicKey(String xB64Url, String yB64Url) {
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            BigInteger x = new BigInteger(1, decoder.decode(xB64Url));
            BigInteger y = new BigInteger(1, decoder.decode(yB64Url));

            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

            ECPublicKeySpec keySpec = new ECPublicKeySpec(new ECPoint(x, y), ecSpec);
            return KeyFactory.getInstance("EC").generatePublic(keySpec);
        } catch (Exception e) {
            throw new JwtException("Failed to build EC public key from JWK", e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new JwtException("SHA-256 unavailable", e);
        }
    }
}
