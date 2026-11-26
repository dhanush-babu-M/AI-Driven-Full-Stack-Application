package com.pathpilot.security;

import com.pathpilot.config.AppProperties;
import com.pathpilot.user.User;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Signs and verifies stateless HS256 JWTs using the configured server secret.
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;
    private final long expirationSeconds;

    public JwtService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String secret = appProperties.jwt() != null ? appProperties.jwt().secret() : null;
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured and at least 32 bytes long.");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = appProperties.jwt().expirationMinutes() * 60L;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String encodedHeader = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = sign(signingInput);
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT.", ex);
        }
    }

    public Optional<AuthUserPrincipal> parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = URL_DECODER.decode(sign(signingInput));
            byte[] providedSig = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, providedSig)) {
                return Optional.empty();
            }

            Map<String, Object> header = objectMapper.readValue(URL_DECODER.decode(parts[0]), MAP_TYPE);
            if (!"HS256".equals(header.get("alg"))) {
                return Optional.empty();
            }

            Map<String, Object> claims = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            Object expObj = claims.get("exp");
            if (!(expObj instanceof Number expNum)) {
                return Optional.empty();
            }
            if (Instant.now().getEpochSecond() >= expNum.longValue()) {
                return Optional.empty();
            }

            Object subObj = claims.get("sub");
            Object emailObj = claims.get("email");
            Object roleObj = claims.get("role");
            if (subObj == null || emailObj == null) {
                return Optional.empty();
            }

            Long userId = Long.parseLong(subObj.toString());
            String email = emailObj.toString();
            String role = roleObj != null ? roleObj.toString() : "USER";
            return Optional.of(new AuthUserPrincipal(userId, email, role));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
        byte[] rawSig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return URL_ENCODER.encodeToString(rawSig);
    }
}
