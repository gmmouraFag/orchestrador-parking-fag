package br.edu.fag.parking;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final String origins, apiKey;
    public WebConfiguration(@Value("${parking.cors-origins}") String origins,
                            @Value("${parking.api-key}") String apiKey) { this.origins = origins; this.apiKey = apiKey; }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-API-Key");
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!apiKey.isBlank() && "POST".equals(request.getMethod())) {
                    String received = request.getHeader("X-API-Key");
                    if (received == null || !MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8))) {
                        response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Credencial de ingestão inválida\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}
