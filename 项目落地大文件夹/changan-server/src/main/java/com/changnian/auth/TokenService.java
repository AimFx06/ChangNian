package com.changnian.auth;

import com.changnian.common.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TokenService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String secret;
    private final long tokenTtlHours;

    public TokenService(
            @Value("${app.jwt-secret}") String secret,
            @Value("${app.token-ttl-hours}") long tokenTtlHours
    ) {
        this.secret = secret;
        this.tokenTtlHours = tokenTtlHours;
    }

    public String create(CurrentUser user) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", user.id());
            payload.put("familyId", user.familyId());
            payload.put("role", user.role().name());
            payload.put("username", user.username());
            payload.put("displayName", user.displayName());
            payload.put("exp", Instant.now().plusSeconds(tokenTtlHours * 3600).getEpochSecond());
            String body = base64Url(MAPPER.writeValueAsBytes(payload));
            return body + "." + sign(body);
        } catch (Exception exception) {
            throw new BizException("生成登录凭证失败");
        }
    }

    public CurrentUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
                throw new BizException("登录凭证无效");
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> payload = MAPPER.readValue(json, new TypeReference<>() {
            });
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                throw new BizException("登录已过期");
            }
            return new CurrentUser(
                    ((Number) payload.get("id")).longValue(),
                    ((Number) payload.get("familyId")).longValue(),
                    Role.valueOf((String) payload.get("role")),
                    (String) payload.get("username"),
                    (String) payload.get("displayName")
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("登录凭证无效");
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
