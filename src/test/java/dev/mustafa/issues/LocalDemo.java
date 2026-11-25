package dev.mustafa.issues;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;

/** Development-only launcher. Never included in the production JAR. */
public class LocalDemo {
  public static void main(String[] args) throws Exception {
    File data = new File(".local/postgres");
    data.mkdirs();
    var database =
        EmbeddedPostgres.builder()
            .setServerConfig("unix_socket_directories", "")
            .setDataDirectory(data)
            .setCleanDataDirectory(false)
            .setPort(0)
            .start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    database.close();
                  } catch (Exception ignored) {
                  }
                }));
    System.setProperty("spring.datasource.url", database.getJdbcUrl("postgres", "postgres"));
    System.setProperty("spring.datasource.username", "postgres");
    System.setProperty("spring.datasource.password", "");
    System.setProperty("app.demo.enabled", "true");
    IssueDeskApplication.main(args);
  }
}
