package edu.escuelaing.campusseguro.events.config;

import edu.escuelaing.campusseguro.events.security.InternalServiceAuthenticationFilter;
import edu.escuelaing.campusseguro.events.security.RestAccessDeniedHandler;
import edu.escuelaing.campusseguro.events.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Security of the internal routes (Standards section 5): they are authorized by the calling
 * service with {@code X-Service-Name}, not by the permissions of a user.
 *
 * <p>It has its own chain, ordered before the user chains, and only serves the alert type catalog
 * that MS03 replicates.
 */
@Configuration
public class InternalSecurityConfig {

    /** Internal route consumed by MS03 to replicate the alert type catalog. */
    public static final String ALERT_TYPES_PATH = "/api/v1/tipos-alerta";

    /**
     * Chain of the internal alert type catalog.
     *
     * @param http                 HTTP security configuration
     * @param authenticationEntryPoint standard 401 response
     * @param accessDeniedHandler  standard 403 response
     * @return the filter chain of the internal route
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalAlertTypesFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .securityMatcher(ALERT_TYPES_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalServiceAuthenticationFilter("ms03"),
                        AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest()
                        .hasAuthority(InternalServiceAuthenticationFilter.AUTHORITY));

        return http.build();
    }
}
