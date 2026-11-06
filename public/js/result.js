"use strict";

if (requireLogin()) {
  const r = Store.getJson("result");
  const questions = Store.getJson("questions");
  if (!r || !questions) location.replace(PAGES.exam);   // nothing submitted yet
  else showResult(r, questions);
}

function showResult(r, questions) {
  $("resultNote").textContent = r.late
      ? "Submitted after the time limit - score not counted."
      : r.auto ? "Time was up, so your exam was submitted automatically."
               : `Well done, ${r.name}. Your exam has been submitted.`;
  $("scoreNum").textContent = r.score;
  $("scoreTotal").textContent = " / " + r.total;
  $("resultPercent").textContent = r.percentage + "%";

  const st = $("resultStatus");
  st.textContent = r.passed ? "Passed" : "Not passed";
  st.className = "status " + (r.passed ? "pass" : "fail");

  const list = $("reviewList");
  list.innerHTML = "";
  r.review.forEach((item, i) => {
    const q = questions[i];
    const li = document.createElement("li");
    li.className = item.isCorrect ? "right" : "wrong";

    const title = document.createElement("div");
    title.className = "q";
    title.textContent = `${i + 1}. ${q.text}`;

    const ans = document.createElement("div");
    ans.className = "a";
    const yours = item.chosen === -1 ? "Not answered" : q.options[item.chosen];
    ans.textContent = item.isCorrect
        ? `Your answer: ${yours}`
        : `Your answer: ${yours}  |  Correct answer: ${q.options[item.correct]}`;

    li.append(title, ans);
    list.appendChild(li);
  });
}

$("logoutBtn").addEventListener("click", logout);
