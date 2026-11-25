"use strict";
const $ = (id) => document.getElementById(id);
const names = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
  ACCESS: "Access",
  NETWORK: "Network",
  APPLICATION: "Application",
  DEVICE: "Device",
};
const state = {
  session: null,
  page: 0,
  listRequest: 0,
  detailRequest: 0,
  detail: null,
  assignees: [],
  loginPending: false,
};
let toastTimer;
function node(tag, text, className) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = text;
  if (className) el.className = className;
  return el;
}
function error(id, message) {
  $(id).textContent = message || "";
  $(id).hidden = !message;
}
function toast(message) {
  clearTimeout(toastTimer);
  $("toast").textContent = message;
  $("toast").hidden = false;
  toastTimer = setTimeout(() => {
    $("toast").hidden = true;
  }, 5000);
}
function busy(form, active) {
  form.querySelectorAll("input,select,textarea,button").forEach((el) => {
    el.disabled = active;
  });
  form.setAttribute("aria-busy", String(active));
}
function date(value) {
  return new Date(value).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
function pill(value, type) {
  return node(
    "span",
    names[value] || value,
    `pill ${type}-${value.toLowerCase()}`,
  );
}
function closeDialogs() {
  document.querySelectorAll("dialog[open]").forEach((el) => el.close());
  state.detailRequest++;
  state.detail = null;
}
async function api(path, { method = "GET", body, form = false } = {}) {
  const headers = { Accept: "application/json" };
  if (method !== "GET") {
    headers[state.session.csrf.headerName] = state.session.csrf.token;
    if (body !== undefined)
      headers["Content-Type"] = form
        ? "application/x-www-form-urlencoded"
        : "application/json";
  }
  let response;
  try {
    response = await fetch("/api" + path, {
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      body:
        body === undefined
          ? undefined
          : form
            ? new URLSearchParams(body)
            : JSON.stringify(body),
    });
  } catch {
    throw new Error(
      "The workspace could not be reached. Check your connection and try again.",
    );
  }
  const data =
    response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && path !== "/login" && state.session?.user) {
      state.listRequest++;
      closeDialogs();
      state.session.user = null;
      showLogin();
      error("login-error", "Your session ended. Sign in again to continue.");
      try {
        state.session = await api("/session");
      } catch {
        /* The sign-in form can retry the session request. */
      }
    }
    const fields = data?.fields
      ? Object.entries(data.fields)
          .map(([key, value]) => `${key}: ${value}`)
          .join("; ")
      : "";
    const failure = new Error(
      fields ||
        data?.message ||
        `The request could not be completed (${response.status}).`,
    );
    failure.status = response.status;
    throw failure;
  }
  return data;
}
function showLogin() {
  $("boot").hidden = true;
  $("workspace").hidden = true;
  $("login-screen").hidden = false;
  $("demo-accounts").hidden = !state.session?.demo;
  $("issue-rows").replaceChildren();
  $("comments").replaceChildren();
  $("activity").replaceChildren();
}
async function openWorkspace() {
  const user = state.session.user;
  $("boot").hidden = true;
  $("login-screen").hidden = true;
  $("workspace").hidden = false;
  $("user-name").textContent = user.displayName;
  $("user-role").textContent =
    user.role === "ADMIN" ? "Administrator" : "Member";
  $("user-initials").textContent = user.displayName
    .split(" ")
    .map((word) => word[0])
    .slice(0, 2)
    .join("");
  $("scope-description").textContent =
    user.role === "ADMIN"
      ? "A shared view of what needs attention, who owns it, and what comes next."
      : "Your reports, conversations, and progress — all in one place.";
  $("demo-badge").hidden = !state.session.demo;
  state.assignees = [];
  if (user.role === "ADMIN") {
    try {
      state.assignees = await api("/assignees");
    } catch (e) {
      toast(e.message);
    }
  }
  clearFilters();
}
async function boot() {
  $("boot-retry").hidden = true;
  $("boot-message").textContent = "Opening your workspace…";
  try {
    state.session = await api("/session");
    if (state.session.user) await openWorkspace();
    else showLogin();
  } catch (e) {
    $("boot-message").textContent = e.message;
    $("boot-retry").hidden = false;
  }
}
async function login(username, password) {
  if (state.loginPending) return;
  state.loginPending = true;
  busy($("login-form"), true);
  document.querySelectorAll(".demo-account").forEach((el) => {
    el.disabled = true;
  });
  error("login-error");
  try {
    state.session = await api("/session");
    await api("/login", {
      method: "POST",
      body: { username, password },
      form: true,
    });
    state.session = await api("/session");
    $("password").value = "";
    await openWorkspace();
  } catch (e) {
    error("login-error", e.message);
  } finally {
    state.loginPending = false;
    busy($("login-form"), false);
    document.querySelectorAll(".demo-account").forEach((el) => {
      el.disabled = false;
    });
  }
}
function selectView(status) {
  $("status-filter").value = status;
  state.page = 0;
  refresh();
}
function clearFilters() {
  $("search").value = "";
  $("status-filter").value = "";
  $("priority-filter").value = "";
  state.page = 0;
  refresh();
}
async function refresh() {
  const request = ++state.listRequest;
  const params = new URLSearchParams({
    q: $("search").value.trim(),
    page: state.page,
  });
  const status = $("status-filter").value;
  if (status) params.set("status", status);
  if ($("priority-filter").value)
    params.set("priority", $("priority-filter").value);
  $("issues-title").textContent = status
    ? names[status] + " issues"
    : "All issues";
  $("view-context").textContent =
    "Workspace / " + $("issues-title").textContent;
  document.querySelectorAll(".nav-item").forEach((el) => {
    el.classList.toggle("active", el.dataset.view === status);
    el.setAttribute(
      "aria-current",
      el.dataset.view === status ? "page" : "false",
    );
  });
  $("list-status").textContent = "Loading issues…";
  $("list-error").hidden = true;
  $("table-wrap").hidden = true;
  $("empty-state").hidden = true;
  $("previous-page").disabled = true;
  $("next-page").disabled = true;
  try {
    const [page, summary] = await Promise.all([
      api("/issues?" + params),
      api("/summary"),
    ]);
    if (request !== state.listRequest || !state.session.user) return;
    if (!page.items.length && state.page > 0) {
      state.page = 0;
      return refresh();
    }
    for (const [key, value] of Object.entries({
      total: summary.total,
      open: summary.open,
      progress: summary.inProgress,
      resolved: summary.resolved,
    })) {
      $("stat-" + key).textContent = value;
      $("nav-" + key).textContent = value;
    }
    const rows = page.items.map((issue) => {
      const row = node("tr");
      const subject = node("td");
      const link = node("button", issue.title, "issue-link");
      link.type = "button";
      link.addEventListener("click", () => openIssue(issue.id));
      subject.append(
        link,
        node(
          "span",
          `ISS-${String(issue.id).padStart(3, "0")} · ${names[issue.category]} · ${issue.owner.displayName}`,
          "row-meta",
        ),
      );
      const statusCell = node("td");
      statusCell.dataset.label = "Status";
      statusCell.append(pill(issue.status, "status"));
      const priority = node("td");
      priority.dataset.label = "Priority";
      priority.append(pill(issue.priority, "priority"));
      const assignee = node(
        "td",
        issue.assignee?.displayName || "Unassigned",
        "assignee",
      );
      assignee.dataset.label = "Assigned to";
      const updated = node("td", date(issue.updatedAt), "updated");
      updated.dataset.label = "Updated";
      updated.title = new Date(issue.updatedAt).toLocaleString();
      row.append(subject, statusCell, priority, assignee, updated);
      return row;
    });
    $("issue-rows").replaceChildren(...rows);
    $("table-wrap").hidden = !rows.length;
    $("empty-state").hidden = !!rows.length;
    $("result-count").textContent =
      `${page.total} ${page.total === 1 ? "issue" : "issues"}`;
    $("list-status").textContent = "";
    $("page-label").textContent =
      `Page ${state.page + 1} of ${Math.max(1, Math.ceil(page.total / page.pageSize))}`;
    $("previous-page").disabled = state.page === 0;
    $("next-page").disabled = (state.page + 1) * page.pageSize >= page.total;
  } catch (e) {
    if (request !== state.listRequest) return;
    $("list-status").textContent = "";
    $("list-error").querySelector("p").textContent = e.message;
    $("list-error").hidden = false;
  }
}
function switchTab(name) {
  document.querySelectorAll("[data-tab]").forEach((el) => {
    const active = el.dataset.tab === name;
    el.setAttribute("aria-selected", active);
    el.tabIndex = active ? 0 : -1;
  });
  $("discussion-panel").hidden = name !== "discussion";
  $("activity-panel").hidden = name !== "activity";
}
function renderDetail(detail) {
  state.detail = detail;
  const issue = detail.issue;
  $("detail-id").textContent =
    `ISS-${String(issue.id).padStart(3, "0")} · ${names[issue.category]}`;
  $("detail-title").textContent = issue.title;
  $("detail-description").textContent = issue.description;
  $("detail-badges").replaceChildren(
    pill(issue.status, "status"),
    pill(issue.priority, "priority"),
  );
  $("detail-owner").textContent = issue.owner.displayName;
  $("detail-created").textContent = date(issue.createdAt);
  $("detail-assignee").textContent =
    issue.assignee?.displayName || "Unassigned";
  $("update-form").hidden = state.session.user.role !== "ADMIN";
  busy($("update-form"), false);
  $("edit-status").value = issue.status;
  $("edit-priority").value = issue.priority;
  $("edit-assignee").replaceChildren(
    new Option("Unassigned", ""),
    ...state.assignees.map(
      (person) => new Option(person.displayName, person.id),
    ),
  );
  $("edit-assignee").value = issue.assignee?.id || "";
  $("comments").replaceChildren(
    ...detail.comments.map((comment) => {
      const article = node("article", undefined, "comment");
      const header = node("header");
      const time = node("time", date(comment.createdAt));
      time.dateTime = comment.createdAt;
      header.append(node("strong", comment.author.displayName), time);
      article.append(header, node("p", comment.body));
      return article;
    }),
  );
  if (!detail.comments.length)
    $("comments").append(
      node("p", "No comments yet. Share the first update.", "no-comments"),
    );
  $("activity").replaceChildren(
    ...detail.activity.map((event) => {
      const item = node("li");
      const time = node("time", date(event.createdAt));
      time.dateTime = event.createdAt;
      item.append(
        node("strong", event.actor.displayName),
        node("p", event.detail),
        time,
      );
      return item;
    }),
  );
  $("detail-content").hidden = false;
  $("detail-reload").hidden = true;
  error("detail-error");
}
async function openIssue(id, retainTab = false) {
  const request = ++state.detailRequest;
  state.detail = null;
  error("detail-error");
  error("comment-error");
  $("detail-reload").hidden = true;
  $("detail-content").hidden = true;
  $("detail-title").textContent = "Loading issue…";
  $("detail-id").textContent = `ISS-${String(id).padStart(3, "0")}`;
  if (!retainTab) {
    $("comment-form").reset();
    switchTab("discussion");
  }
  if (!$("issue-dialog").open) $("issue-dialog").showModal();
  $("detail-reload").onclick = () => openIssue(id, true);
  try {
    const detail = await api("/issues/" + id);
    if (
      request === state.detailRequest &&
      $("issue-dialog").open &&
      state.session.user
    )
      renderDetail(detail);
  } catch (e) {
    if (request !== state.detailRequest) return;
    $("detail-title").textContent = "Issue unavailable";
    error("detail-error", e.message);
    $("detail-reload").hidden = false;
  }
}
$("boot-retry").addEventListener("click", boot);
$("login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  login($("username").value.trim(), $("password").value);
});
document
  .querySelectorAll(".demo-account")
  .forEach((el) =>
    el.addEventListener("click", () =>
      login(el.dataset.user, "IssueDesk-demo-2026!"),
    ),
  );
$("logout").addEventListener("click", async () => {
  $("logout").disabled = true;
  try {
    await api("/logout", { method: "POST" });
    state.listRequest++;
    closeDialogs();
    state.session = await api("/session");
    showLogin();
    $("username").focus();
  } catch (e) {
    toast(e.message);
  } finally {
    $("logout").disabled = false;
  }
});
document
  .querySelectorAll("[data-view]")
  .forEach((el) =>
    el.addEventListener("click", () => selectView(el.dataset.view)),
  );
$("filters").addEventListener("submit", (event) => {
  event.preventDefault();
  state.page = 0;
  refresh();
});
["status-filter", "priority-filter"].forEach((id) =>
  $(id).addEventListener("change", () => {
    state.page = 0;
    refresh();
  }),
);
["clear-filters", "empty-clear"].forEach((id) =>
  $(id).addEventListener("click", clearFilters),
);
$("list-retry").addEventListener("click", refresh);
$("previous-page").addEventListener("click", () => {
  state.page--;
  refresh();
});
$("next-page").addEventListener("click", () => {
  state.page++;
  refresh();
});
$("help-open").addEventListener("click", () => $("help-dialog").showModal());
document
  .querySelectorAll("[data-close]")
  .forEach((el) =>
    el.addEventListener("click", () => $(el.dataset.close).close()),
  );
$("issue-dialog").addEventListener("close", () => {
  state.detailRequest++;
  state.detail = null;
});
$("create-open").addEventListener("click", () => {
  $("create-form").reset();
  error("create-error");
  $("create-dialog").showModal();
});
$("create-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const body = Object.fromEntries(new FormData(event.currentTarget));
  busy(event.currentTarget, true);
  error("create-error");
  try {
    const issue = await api("/issues", { method: "POST", body });
    $("create-dialog").close();
    toast("Issue created. You can follow its progress here.");
    clearFilters();
    openIssue(issue.id);
  } catch (e) {
    error("create-error", e.message);
  } finally {
    busy(event.target, false);
  }
});
$("update-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.detail) return;
  const id = state.detail.issue.id;
  const request = state.detailRequest;
  const body = {
    status: $("edit-status").value,
    priority: $("edit-priority").value,
    assigneeId: $("edit-assignee").value
      ? Number($("edit-assignee").value)
      : null,
    version: state.detail.issue.version,
  };
  busy(event.currentTarget, true);
  error("detail-error");
  let conflict = false;
  try {
    await api("/issues/" + id, { method: "PATCH", body });
    if (request === state.detailRequest) {
      toast("Changes saved.");
      await openIssue(id, true);
    }
    refresh();
  } catch (e) {
    if (request === state.detailRequest) {
      error("detail-error", e.message);
      conflict = e.status === 409;
      $("detail-reload").hidden = !conflict;
      if (conflict) $("detail-reload").focus();
    }
  } finally {
    if (!conflict) busy(event.target, false);
  }
});
$("comment-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.detail) return;
  const id = state.detail.issue.id;
  const request = state.detailRequest;
  const body = $("comment-body").value;
  busy(event.currentTarget, true);
  error("comment-error");
  try {
    await api("/issues/" + id + "/comments", {
      method: "POST",
      body: { body },
    });
    if (request === state.detailRequest) {
      $("comment-form").reset();
      await openIssue(id, true);
      toast("Comment posted.");
    }
    refresh();
  } catch (e) {
    if (request === state.detailRequest) error("comment-error", e.message);
  } finally {
    busy(event.target, false);
  }
});
document.querySelectorAll("[data-tab]").forEach((el) => {
  el.addEventListener("click", () => switchTab(el.dataset.tab));
  el.addEventListener("keydown", (event) => {
    if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) {
      event.preventDefault();
      const tab =
        event.key === "Home"
          ? "discussion"
          : event.key === "End"
            ? "activity"
            : el.dataset.tab === "discussion"
              ? "activity"
              : "discussion";
      switchTab(tab);
      $(tab + "-tab").focus();
    }
  });
});
boot();
