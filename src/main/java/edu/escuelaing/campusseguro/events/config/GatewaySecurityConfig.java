package edu.escuelaing.campusseguro.events.config;

import edu.escuelaing.campusseguro.events.security.IdentityHeadersAuthenticationFilter;
import edu.escuelaing.campusseguro.events.security.RestAccessDeniedHandler;
import edu.escuelaing.campusseguro.events.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * API security when MS06 runs behind the API Gateway. The Gateway validates the JWT and removes
 * it; MS06 trusts the {@code X-User-Id}/{@code X-User-Role} headers it injects.
 */
@Configuration
@EnableMethodSecurity
@Profile("!local & !test")
public class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(
            HttpSecurity http,
            IdentityHeadersAuthenticationFilter identityHeadersFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(identityHeadersFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
