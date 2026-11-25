package dev.mustafa.issues;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class JsonBodyLimit extends OncePerRequestFilter {
  static final int LIMIT = 16_384;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/")
        || request.getContentType() == null
        || !request
            .getContentType()
            .toLowerCase(java.util.Locale.ROOT)
            .startsWith("application/json")) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > LIMIT) {
      reject(response);
      return;
    }
    byte[] bytes = request.getInputStream().readNBytes(LIMIT + 1);
    if (bytes.length > LIMIT) {
      reject(response);
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
              @Override
              public int read() {
                return input.read();
              }

              @Override
              public int read(byte[] b, int off, int len) {
                return input.read(b, off, len);
              }

              @Override
              public boolean isFinished() {
                return input.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous requests only");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
          }
        },
        response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType("application/json");
    response.getWriter().write("{\"message\":\"Request is too large. Keep it below 16 KiB.\"}");
  }
}
