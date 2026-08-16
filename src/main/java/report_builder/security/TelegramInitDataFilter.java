package report_builder.security;

import java.io.IOException;
import io.github.sanvew.tg.init.data.InitDataUtils;
import io.github.sanvew.tg.init.data.exception.TelegramInitDataException;
import io.github.sanvew.tg.init.data.type.InitData;
import io.github.sanvew.tg.init.data.type.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TelegramInitDataFilter extends OncePerRequestFilter {

    public static final String TELEGRAM_USER_ATTR = "telegramUser";
    public static final String TELEGRAM_INIT_DATA_ATTR = "telegramInitData";

    @Value("${bot.token}")
    private String botToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String initData = request.getHeader("X-Telegram-Init-Data");

        if (initData == null || initData.isBlank()) {
            sendForbidden(response, "Access only from Telegram Mini App");
            return;
        }

        try {
            InitDataUtils.validate(initData, botToken);

            InitData parsed = InitDataUtils.parse(initData);
            User user = parsed.getUser();

            if (user == null) {
                sendForbidden(response, "User data is missing");
                return;
            }

            request.setAttribute(TELEGRAM_USER_ATTR, user);
            request.setAttribute(TELEGRAM_INIT_DATA_ATTR, parsed);

            filterChain.doFilter(request, response);

        } catch (TelegramInitDataException e) {
            sendForbidden(response, "Invalid Telegram init data");
        } catch (Exception e) {
            sendForbidden(response, "Authentication failed");
        }
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}