"use strict";

// Already logged in? Continue where the student left off.
if (Store.get("token")) {
  location.replace(Store.get("result") ? PAGES.result : PAGES.exam);
}

$("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("loginError").textContent = "";
  const body = new URLSearchParams({
    username: $("username").value,
    password: $("password").value
  });
  try {
    const res = await fetch("/api/login", { method: "POST", body });
    const data = await res.json();
    if (!res.ok) { $("loginError").textContent = data.error; return; }

    Store.clear();
    Store.set("token", data.token);
    Store.set("name", data.name);
    location.href = PAGES.exam;
  } catch {
    $("loginError").textContent = "Cannot reach the server. Is it running?";
  }
});
