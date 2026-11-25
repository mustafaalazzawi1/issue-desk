package dev.mustafa.issues;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

class Models {
  enum Status {
    OPEN,
    IN_PROGRESS,
    RESOLVED
  }

  enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
  }

  enum Category {
    ACCESS,
    NETWORK,
    APPLICATION,
    DEVICE
  }

  record Person(long id, String displayName) {}

  record Identity(long id, String username, String displayName, String role) {}

  record Issue(
      long id,
      String title,
      String description,
      Category category,
      Priority priority,
      Status status,
      Person owner,
      Person assignee,
      int version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  record Comment(long id, String body, Person author, OffsetDateTime createdAt) {}

  record Activity(long id, String action, String detail, Person actor, OffsetDateTime createdAt) {}

  record Detail(Issue issue, List<Comment> comments, List<Activity> activity) {}

  record Page(List<Issue> items, long total, int page, int pageSize) {}

  record Summary(long total, long open, long inProgress, long resolved, long urgent) {}

  record CreateIssue(
      @NotBlank @Size(min = 3, max = 120) String title,
      @NotBlank @Size(min = 10, max = 5000) String description,
      @NotNull Category category,
      @NotNull Priority priority) {
    CreateIssue {
      if (title != null) title = title.strip();
      if (description != null) description = description.strip();
    }
  }

  record UpdateIssue(
      @NotNull Status status,
      @NotNull Priority priority,
      @Positive Long assigneeId,
      @NotNull @Min(0) Integer version) {}

  record NewComment(@NotBlank @Size(max = 2000) String body) {}
}
