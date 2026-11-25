package dev.mustafa.issues;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return username ->
        db
            .query(
                "SELECT * FROM app_users WHERE username = ?",
                (rs, n) ->
                    new AppUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("password_hash"),
                        rs.getString("role")),
                username)
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers(
                    "/",
                    "/index.html",
                    "/app.js",
                    "/styles.css",
                    "/icon.svg",
                    "/api/session",
                    "/api/login",
                    "/api/health",
                    "/error")
                .permitAll()
                .anyRequest()
                .authenticated());
    http.csrf(Customizer.withDefaults());
    http.formLogin(
        f ->
            f.loginProcessingUrl("/api/login")
                .successHandler(
                    (request, response, auth) -> {
                      response.setContentType("application/json");
                      response.getWriter().write("{\"authenticated\":true}");
                    })
                .failureHandler(
                    (request, response, failure) -> {
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response
                          .getWriter()
                          .write("{\"message\":\"Username or password is incorrect.\"}");
                    }));
    http.logout(
        l ->
            l.logoutUrl("/api/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)));
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (request, response, ex) -> {
                      response.setStatus(401);
                      response.setContentType("application/json");
                      response.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                    })
                .accessDeniedHandler(
                    (request, response, ex) -> {
                      response.setStatus(403);
                      response.setContentType("application/json");
                      response
                          .getWriter()
                          .write(
                              "{\"message\":\"This action is not allowed. Refresh the page if your"
                                  + " session changed.\"}");
                    }));
    http.headers(
        h ->
            h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; font-src 'self'; connect-src 'self'; object-src"
                                + " 'none'; base-uri 'none'; frame-ancestors 'none'; form-action"
                                + " 'self'"))
                .referrerPolicy(
                    r ->
                        r.policy(
                            org.springframework.security.web.header.writers
                                .ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
    return http.build();
  }
}
