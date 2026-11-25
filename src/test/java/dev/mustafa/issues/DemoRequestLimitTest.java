package dev.mustafa.issues;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DemoRequestLimitTest {
  @Test
  void budgetReopensAtTheExactWindowBoundary() {
    var now = new AtomicLong();
    var budget = new DemoRequestLimit.Budget(2, 2, 2, now::get);
    assertEquals(0, budget.acquire(false, false));
    assertEquals(0, budget.acquire(false, false));
    assertEquals(60, budget.acquire(false, false));
    now.set(59_999_999_999L);
    assertEquals(1, budget.acquire(false, false));
    now.set(60_000_000_000L);
    assertEquals(0, budget.acquire(false, false));
  }

  @Test
  void loginAndWriteLimitsLeaveReadsAvailable() {
    var budget = new DemoRequestLimit.Budget(6, 1, 2, () -> 0L);
    assertEquals(0, budget.acquire(true, false));
    assertEquals(60, budget.acquire(true, false));
    assertEquals(0, budget.acquire(false, true));
    assertEquals(0, budget.acquire(false, true));
    assertEquals(60, budget.acquire(false, true));
    // Rejected traffic does not consume remaining read capacity.
    for (int i = 0; i < 3; i++) assertEquals(0, budget.acquire(false, false));
    assertEquals(60, budget.acquire(false, false));
  }

  @Test
  void parallelRequestsCannotOverspendTheBudget() throws Exception {
    var budget = new DemoRequestLimit.Budget(25, 25, 25, () -> 0L);
    try (var workers = Executors.newFixedThreadPool(12)) {
      var tasks = new ArrayList<Callable<Integer>>();
      for (int i = 0; i < 100; i++) tasks.add(() -> budget.acquire(false, true));
      int accepted = 0;
      for (var result : workers.invokeAll(tasks)) if (result.get() == 0) accepted++;
      assertEquals(25, accepted);
    }
  }

  @Test
  void newSessionsAndForwardedHeadersDoNotBypassTheSharedBudget() throws Exception {
    var filter = new DemoRequestLimit(true, true, 2, 2, 2);
    var passed = new AtomicInteger();
    for (int i = 0; i < 3; i++) {
      var request = new MockHttpServletRequest("GET", "/api/session");
      request.addHeader("X-Forwarded-For", "192.0.2." + i);
      request.getSession();
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (a, b) -> passed.incrementAndGet());
      if (i == 2) {
        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("demo_rate_limit"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        int seconds = Integer.parseInt(response.getHeader("Retry-After"));
        assertTrue(seconds >= 1 && seconds <= 60);
      }
    }
    assertEquals(2, passed.get());
  }

  @Test
  void issueMutationsShareAWriteBudgetWithoutBlockingReadsOrLogout() throws Exception {
    var filter = new DemoRequestLimit(true, true, 20, 5, 2);
    var passed = new AtomicInteger();
    String[][] calls = {
      {"POST", "/api/issues"},
      {"PATCH", "/api/issues/1"},
      {"POST", "/api/issues/1/comments"},
      {"GET", "/api/issues"},
      {"POST", "/api/logout"}
    };
    for (int i = 0; i < calls.length; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest(calls[i][0], calls[i][1]),
          response,
          (a, b) -> passed.incrementAndGet());
      assertEquals(i == 2 ? 429 : 200, response.getStatus());
    }
    assertEquals(4, passed.get());
  }

  @Test
  void healthLogoutAndStaticFilesRemainReachable() throws Exception {
    var filter = new DemoRequestLimit(true, true, 1, 1, 1);
    var passed = new AtomicInteger();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/session"),
        new MockHttpServletResponse(),
        (a, b) -> {});
    for (String path : new String[] {"/api/health", "/api/logout", "/", "/styles.css"}) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("POST", path), response, (a, b) -> passed.incrementAndGet());
      assertNotEquals(429, response.getStatus());
    }
    assertEquals(4, passed.get());
  }

  @Test
  void onlyDemoModeEnablesThisPolicy() throws Exception {
    for (boolean[] flags : new boolean[][] {{false, true}, {true, false}}) {
      var filter = new DemoRequestLimit(flags[0], flags[1], 1, 1, 1);
      var passed = new AtomicInteger();
      for (int i = 0; i < 3; i++)
        filter.doFilter(
            new MockHttpServletRequest("GET", "/api/session"),
            new MockHttpServletResponse(),
            (a, b) -> passed.incrementAndGet());
      assertEquals(3, passed.get());
    }
  }

  @Test
  void invalidConfigurationFailsEarly() {
    assertThrows(
        IllegalArgumentException.class, () -> new DemoRequestLimit.Budget(0, 1, 1, () -> 0L));
    assertThrows(
        IllegalArgumentException.class, () -> new DemoRequestLimit.Budget(1, -1, 1, () -> 0L));
    assertThrows(
        IllegalArgumentException.class, () -> new DemoRequestLimit.Budget(1, 1, 0, () -> 0L));
  }
}
