package llc.feelingfroggy.finances.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder security configuration for the M0 scaffold.
 *
 * <p>This currently permits everything so the status endpoints are reachable while the app has no
 * users. It is NOT a starting point for the real thing — see docs/DECISIONS.md D-12, which is an
 * OPEN decision (self-issued JWT vs. Keycloak vs. hosted IdP). Whatever wins, the legacy jjwt 0.9.1
 * approach does not come back.
 *
 * <p>Do not deploy this anywhere reachable until D-12 is resolved.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
