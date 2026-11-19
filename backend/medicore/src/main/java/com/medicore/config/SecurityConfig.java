package com.medicore.config;

import com.medicore.repository.RefreshTokenRepository;
import com.medicore.repository.UserAccountRepository;
import com.medicore.security.AccessCookieAuthenticationFilter;
import com.medicore.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(@Value("${medicore.security.cookie-secure:false}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    AccessCookieAuthenticationFilter accessCookieAuthenticationFilter(
            JwtService jwtService, UserAccountRepository users, RefreshTokenRepository tokens) {
        return new AccessCookieAuthenticationFilter(jwtService, users, tokens);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                           CookieCsrfTokenRepository csrfTokenRepository,
                                           AccessCookieAuthenticationFilter accessFilter,
                                           ApiSecurityErrorWriter errorWriter) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/api/v1/auth/csrf",
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(request, response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(request, response,
                                HttpStatus.FORBIDDEN, "FORBIDDEN", "The requested action is not allowed.")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(accessFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
