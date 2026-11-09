package edu.escuelaing.campusseguro.events.config;

import edu.escuelaing.campusseguro.events.security.JwtRoleConverter;
import edu.escuelaing.campusseguro.events.security.RestAccessDeniedHandler;
import edu.escuelaing.campusseguro.events.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API security: OAuth2 resource server with JWT, no session.
 *
 * <ul>
 *   <li>Every request needs a token, except the documentation (Swagger/OpenAPI)
 *       and CORS {@code OPTIONS} requests.</li>
 *   <li>Roles are read from the token with {@link JwtRoleConverter}.</li>
 *   <li>Role permissions are declared on each endpoint with {@code @PreAuthorize}
 *       (that is why method security is enabled).</li>
 *   <li>401 and 403 errors use the standard API format.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@Profile({"local", "test"})
public class SecurityConfig {

    /**
     * Main filter chain. It has the lowest priority so more specific chains
     * (like the {@code /local/**} one in the local profile) are checked first.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtRoleConverter jwtRoleConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtRoleConverter)));
        return http.build();
    }
}

