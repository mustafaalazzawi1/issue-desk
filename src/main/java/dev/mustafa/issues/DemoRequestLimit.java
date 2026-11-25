package dev.mustafa.issues;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, instance-wide budgets for the single-replica public demo. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class DemoRequestLimit extends OncePerRequestFilter {
  private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
  private final boolean enabled;
  private final Budget budget;

  DemoRequestLimit(
      @Value("${app.demo.enabled:false}") boolean demo,
      @Value("${app.demo.rate-limit.enabled:true}") boolean enabled,
      @Value("${app.demo.rate-limit.requests-per-minute:240}") int requests,
      @Value("${app.demo.rate-limit.logins-per-minute:12}") int logins,
      @Value("${app.demo.rate-limit.writes-per-minute:30}") int writes) {
    this.enabled = demo && enabled;
    this.budget = new Budget(requests, logins, writes, System::nanoTime);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!enabled
        || !path.startsWith("/api/")
        || path.equals("/api/health")
        || path.equals("/api/logout")) {
      chain.doFilter(request, response);
      return;
    }
    boolean login = path.equals("/api/login") && request.getMethod().equals("POST");
    int retry = budget.acquire(login, !login && WRITES.contains(request.getMethod()));
    if (retry == 0) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader("Retry-After", Integer.toString(retry));
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            "{\"code\":\"demo_rate_limit\",\"message\":\"The shared demo is busy. Please wait "
                + retry
                + " seconds, then try again.\"}");
  }

  static final class Budget {
    private static final long WINDOW = TimeUnit.MINUTES.toNanos(1);
    private final int requestLimit, loginLimit, writeLimit;
    private final LongSupplier clock;
    private long started;
    private int requests, logins, writes;

    Budget(int requestLimit, int loginLimit, int writeLimit, LongSupplier clock) {
      if (requestLimit < 1 || loginLimit < 1 || writeLimit < 1)
        throw new IllegalArgumentException("Demo request limits must be positive");
      this.requestLimit = requestLimit;
      this.loginLimit = loginLimit;
      this.writeLimit = writeLimit;
      this.clock = clock;
      this.started = clock.getAsLong();
    }

    // One lock makes checking and consuming all applicable budgets atomic.
    // No client-supplied address or session can allocate a new budget.
    synchronized int acquire(boolean login, boolean write) {
      long now = clock.getAsLong();
      long elapsed = now - started;
      if (elapsed >= WINDOW) {
        started = now;
        requests = logins = writes = 0;
        elapsed = 0;
      }
      if (requests >= requestLimit
          || (login && logins >= loginLimit)
          || (write && writes >= writeLimit))
        return (int) Math.max(1, (WINDOW - elapsed + 999_999_999L) / 1_000_000_000L);
      requests++;
      if (login) logins++;
      if (write) writes++;
      return 0;
    }
  }
}
