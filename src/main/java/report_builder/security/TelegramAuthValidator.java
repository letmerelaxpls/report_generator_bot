package report_builder.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelegramAuthValidator {
    public static boolean isInitDataValid(String initData, String botToken) {
        try {
            Map<String, String> params = parseQueryString(initData);
            String hash = params.remove("hash");
            if (hash == null) {
                return false;
            }

            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            StringBuilder builder = new StringBuilder();
            for (String key: keys) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(key).append("=").append(params.get(key));
            }

            byte[] secretKey = hmacSha256(botToken.trim().getBytes(StandardCharsets.UTF_8),
                    "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] calculatedHashBytes = hmacSha256(builder.toString().getBytes(StandardCharsets.UTF_8), secretKey);

            StringBuilder hexString = new StringBuilder();
            for (byte b: calculatedHashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            boolean isValid = hexString.toString().equalsIgnoreCase(hash);

            if (!isValid) {
                System.out.println("--- TG AUTH DEBUG ---");
                System.out.println("Data Check String:\n" + builder);
                System.out.println("Expected Hash:   " + hash);
                System.out.println("Calculated Hash: " + hexString);
                System.out.println("Used Bot Token:  " + (botToken != null ? botToken.substring(0, 5)
                        + "..." : "NULL"));
                System.out.println("---------------------");
            }

            return isValid;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Long extractUserId(String initData) {
        try {
            Map<String, String> params = parseQueryString(initData);
            String userJsonStr = params.get("user");
            if (userJsonStr == null) {
                return null;
            }
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(userJsonStr);
            return node.get("id").asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        try {
            for (String param: query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length > 1) {
                    String key = URLDecoder.decode(pair[0].replace("+", "%2B"),
                            StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair[1].replace("+", "%2B"),
                            StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse initData", e);
        }
        return params;
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data);
    }
}
