"use strict";

const LETTERS = ["A", "B", "C", "D", "E"];
const state = { questions: [], answers: [], current: 0, deadline: 0, timerId: null, submitting: false };

async function init() {
  const res = await fetch("/api/exam?token=" + encodeURIComponent(Store.get("token")));
  const data = await res.json();
  if (!res.ok) { alert(data.error); logout(); return; }

  state.questions = data.questions;
  state.deadline = Date.now() + data.remaining * 1000;   // server decides the time left
  const saved = Store.getJson("answers");
  state.answers = saved && saved.length === state.questions.length ? saved : state.questions.map(() => -1);

  Store.setJson("questions", state.questions);           // needed by the result page
  $("studentName").textContent = Store.get("name");
  buildPalette();
  renderQuestion();
  startTimer();
}

/* ---------- Questions ---------- */
function renderQuestion() {
  const q = state.questions[state.current];
  $("qCount").textContent = `Question ${state.current + 1} of ${state.questions.length}`;
  $("qText").textContent = q.text;

  const box = $("options");
  box.innerHTML = "";
  q.options.forEach((text, i) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "option" + (state.answers[state.current] === i ? " selected" : "");
    btn.setAttribute("role", "radio");
    btn.setAttribute("aria-checked", state.answers[state.current] === i);

    const bubble = document.createElement("span");
    bubble.className = "bubble";
    bubble.textContent = LETTERS[i];
    const label = document.createElement("span");
    label.textContent = text;

    btn.append(bubble, label);
    btn.addEventListener("click", () => setAnswer(i));
    box.appendChild(btn);
  });

  $("prevBtn").disabled = state.current === 0;
  $("nextBtn").disabled = state.current === state.questions.length - 1;
  updatePalette();
}

function setAnswer(i) {
  state.answers[state.current] = i;
  Store.setJson("answers", state.answers);
  renderQuestion();
}

function buildPalette() {
  const p = $("palette");
  p.innerHTML = "";
  state.questions.forEach((_, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.textContent = i + 1;
    b.setAttribute("aria-label", "Go to question " + (i + 1));
    b.addEventListener("click", () => { state.current = i; renderQuestion(); });
    p.appendChild(b);
  });
}

function updatePalette() {
  [...$("palette").children].forEach((b, i) => {
    b.classList.toggle("answered", state.answers[i] !== -1);
    b.classList.toggle("current", i === state.current);
  });
  const done = state.answers.filter((a) => a !== -1).length;
  $("progressText").textContent = `${done}/${state.questions.length} answered`;
}

$("prevBtn").addEventListener("click", () => { state.current--; renderQuestion(); });
$("nextBtn").addEventListener("click", () => { state.current++; renderQuestion(); });
$("clearBtn").addEventListener("click", () => setAnswer(-1));

/* ---------- Countdown timer ---------- */
function startTimer() {
  clearInterval(state.timerId);
  tick();
  state.timerId = setInterval(tick, 250);
}

function tick() {
  const left = Math.max(0, Math.ceil((state.deadline - Date.now()) / 1000));
  $("timer").textContent = String(Math.floor(left / 60)).padStart(2, "0") + ":" + String(left % 60).padStart(2, "0");
  $("timer").classList.toggle("warning", left <= 60);
  if (left === 0) { clearInterval(state.timerId); submitExam(true); }   // time up: auto-submit
}

/* ---------- Submit ---------- */
$("submitBtn").addEventListener("click", () => {
  const n = state.answers.filter((a) => a === -1).length;
  $("confirmText").textContent = (n
      ? `You have ${n} unanswered question${n > 1 ? "s" : ""}. `
      : "You have answered every question. ") + "You cannot change answers after submitting.";
  $("confirmDialog").showModal();
});
$("cancelSubmit").addEventListener("click", () => $("confirmDialog").close());
$("confirmSubmit").addEventListener("click", () => { $("confirmDialog").close(); submitExam(false); });

async function submitExam(auto) {
  if (state.submitting) return;
  state.submitting = true;
  clearInterval(state.timerId);

  const body = new URLSearchParams({ token: Store.get("token"), answers: state.answers.join(",") });
  try {
    const res = await fetch("/api/submit", { method: "POST", body });
    const data = await res.json();
    if (!res.ok) { alert(data.error); logout(); return; }

    data.auto = auto;
    Store.setJson("result", data);
    location.href = PAGES.result;
  } catch {
    state.submitting = false;
    alert("Could not submit. Check your connection and press Submit again.");
    startTimer();
  }
}

/* ---------- Start ---------- */
if (requireLogin()) {
  if (Store.get("result")) location.replace(PAGES.result);
  else init();
}
