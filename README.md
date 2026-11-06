# Online Examination Website

HTML + CSS + JavaScript front end, Java back end (no external libraries).

## Folder structure

    online-exam/
    |-- src/
    |   `-- ExamServer.java       Java server: login, questions, timer check, scoring
    |-- public/                   Everything the browser loads
    |   |-- index.html            Entry point, redirects to the login page
    |   |-- pages/                One HTML file per page
    |   |   |-- login.html
    |   |   |-- exam.html
    |   |   |-- result.html
    |   |   `-- _template.html    Copy this to start a new page
    |   |-- css/
    |   |   |-- base.css          Shared colours, buttons, cards, form fields
    |   |   |-- login.css  exam.css  result.css   One file per page
    |   |-- js/
    |   |   |-- common.js         Shared helpers (session storage, logout)
    |   |   |-- login.js  exam.js  result.js      One file per page
    |   `-- images/               Logos, icons, pictures
    |-- .gitignore
    `-- README.md

## Run (JDK 17 or newer)

Run from the online-exam folder (the server looks for ./public):

    java src/ExamServer.java

Open http://localhost:8080
Demo logins: student1 / pass123, student2 / pass456, alice / alice@123

In IntelliJ: right-click `src` > Mark Directory as > Sources Root, then run
ExamServer with Working directory set to the project folder.

## Adding a new page (example: instructions)

1. Copy `public/pages/_template.html` to `public/pages/instructions.html`.
2. Create `public/css/instructions.css` and `public/js/instructions.js`, and
   uncomment the two lines in the new HTML file that link to them.
3. Add the page to `PAGES` in `public/js/common.js`:
   `instructions: "/pages/instructions.html"`
4. Link to it, e.g. from `login.js`: `location.href = PAGES.instructions;`

## Customising
- Questions and answer key: QUESTIONS in ExamServer.java (last number = index of correct option)
- Duration: DURATION_SECONDS; pass mark: PASS_PERCENT; students: USERS
