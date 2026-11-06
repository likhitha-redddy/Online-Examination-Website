"use strict";
/* Shared helpers. Every page loads this file first. */

const $ = (id) => document.getElementById(id);

const PAGES = {
  login: "/pages/login.html",
  exam: "/pages/exam.html",
  result: "/pages/result.html"
};

/* Data kept between pages lives in sessionStorage. */
const Store = {
  get(key)      { return sessionStorage.getItem(key); },
  set(key, val) { sessionStorage.setItem(key, val); },
  getJson(key)  { try { return JSON.parse(sessionStorage.getItem(key)); } catch { return null; } },
  setJson(key, val) { sessionStorage.setItem(key, JSON.stringify(val)); },
  remove(key)   { sessionStorage.removeItem(key); },
  clear()       { sessionStorage.clear(); }
};

/* Send the visitor to the login page when nobody is logged in. */
function requireLogin() {
  if (!Store.get("token")) { location.replace(PAGES.login); return false; }
  return true;
}

function logout() {
  Store.clear();
  location.href = PAGES.login;
}
