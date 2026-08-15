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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TelegramAuthValidator {
    public static boolean isInitDataValid(String initData, String botToken) {
        try {
            Map<String, String> params = new LinkedHashMap<>();

            for (String part : initData.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;

                String key = URLDecoder.decode(part.substring(0, eq).replace("+", "%2B"),
                        StandardCharsets.UTF_8);
                String value = URLDecoder.decode(part.substring(eq + 1).replace("+", "%2B"),
                        StandardCharsets.UTF_8);
                params.put(key, value);
            }

            String hash = params.remove("hash");
            params.remove("signature");

            if (hash == null) return false;

            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            StringBuilder dataCheckString = new StringBuilder();
            for (String key : keys) {
                if (!dataCheckString.isEmpty()) {
                    dataCheckString.append('\n');
                }
                dataCheckString.append(key).append('=').append(params.get(key));
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] calculated = mac.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));

            String calculatedHex = bytesToHex(calculated);

            return calculatedHex.equalsIgnoreCase(hash);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
}
