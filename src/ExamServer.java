import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Online Examination Server (Java 17+, no external libraries).
 *
 *  POST /api/login      username, password        -> { token, name }
 *  GET  /api/exam       token                     -> { questions[], remaining }
 *  POST /api/submit     token, answers=0,2,-1,... -> { score, total, percentage, review[] }
 *
 * The answer key never leaves the server, the score is calculated here,
 * and the timer is enforced here too (the browser timer is only a display).
 */
public class ExamServer {

    static final int PORT = 8080;
    static final int DURATION_SECONDS = 300;      // 5 minutes
    static final int GRACE_SECONDS = 5;           // network delay allowance
    static final int PASS_PERCENT = 40;
    static final Path PUBLIC_DIR = Path.of("public").toAbsolutePath().normalize();

    // ---- Demo student accounts ------------------------------------------------
    static final Map<String, String> USERS = Map.of(
            "student1", "pass123",
            "student2", "pass456",
            "alice", "alice@123");

    // ---- Question bank ---------------------------------------------------------
    record Question(String text, String[] options, int answer) {}

    static final List<Question> QUESTIONS = List.of(
            new Question("Which language runs inside the web browser to make pages interactive?",
                    new String[]{"Java", "JavaScript", "C++", "Python"}, 1),
            new Question("What does CSS stand for?",
                    new String[]{"Creative Style Sheets", "Computer Style Sheets", "Cascading Style Sheets", "Colorful Style Syntax"}, 2),
            new Question("Which HTML tag is used to create a hyperlink?",
                    new String[]{"<link>", "<a>", "<href>", "<url>"}, 1),
            new Question("Which keyword is used to create a class in Java?",
                    new String[]{"object", "new", "struct", "class"}, 3),
            new Question("Which of these is NOT a primitive data type in Java?",
                    new String[]{"int", "boolean", "String", "double"}, 2),
            new Question("Which CSS property changes the text colour of an element?",
                    new String[]{"font-color", "color", "text-style", "foreground"}, 1),
            new Question("What is the default port number of HTTP?",
                    new String[]{"21", "25", "443", "80"}, 3),
            new Question("Which JavaScript method selects an element by its id?",
                    new String[]{"getElementById()", "querySelectorAll()", "getElementsByTag()", "selectId()"}, 0),
            new Question("Which of these is the entry point of a Java application?",
                    new String[]{"start()", "main()", "run()", "init()"}, 1),
            new Question("Which HTTP method is normally used to send a login form securely to a server?",
                    new String[]{"GET", "HEAD", "POST", "TRACE"}, 2)
    );

    // ---- Sessions --------------------------------------------------------------
    static class Session {
        final String user;
        long startMillis = 0;       // set the first time the exam is opened
        boolean submitted = false;
        String cachedResult;        // result JSON, returned again if resubmitted
        Session(String user) { this.user = user; }
    }

    static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    static final Set<String> FINISHED_USERS = ConcurrentHashMap.newKeySet();  // one attempt per student

    // ---- Main ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/login", ExamServer::handleLogin);
        server.createContext("/api/exam", ExamServer::handleExam);
        server.createContext("/api/submit", ExamServer::handleSubmit);
        server.createContext("/", ExamServer::handleStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("Exam server running at http://localhost:" + PORT);
    }

    // ---- API handlers ----------------------------------------------------------
    static void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use POST")); return; }
        Map<String, String> form = parseForm(readBody(ex));
        String user = form.getOrDefault("username", "").trim();
        String pass = form.getOrDefault("password", "");

        if (!pass.equals(USERS.get(user))) {
            sendJson(ex, 401, error("Invalid username or password."));
            return;
        }
        if (FINISHED_USERS.contains(user)) {
            sendJson(ex, 403, error("This account has already completed the exam."));
            return;
        }
        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, new Session(user));
        sendJson(ex, 200, "{\"token\":\"" + token + "\",\"name\":\"" + esc(user) + "\"}");
    }

    static void handleExam(HttpExchange ex) throws IOException {
        Session s = SESSIONS.get(parseForm(ex.getRequestURI().getRawQuery()).get("token"));
        if (s == null) { sendJson(ex, 401, error("Session expired. Please log in again.")); return; }
        if (s.submitted) { sendJson(ex, 403, error("Exam already submitted.")); return; }

        if (s.startMillis == 0) s.startMillis = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder("{\"remaining\":" + remainingSeconds(s) + ",\"questions\":[");
        for (int i = 0; i < QUESTIONS.size(); i++) {
            Question q = QUESTIONS.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"text\":\"").append(esc(q.text())).append("\",\"options\":[");
            for (int j = 0; j < q.options().length; j++) {
                if (j > 0) sb.append(',');
                sb.append('"').append(esc(q.options()[j])).append('"');
            }
            sb.append("]}");               // NOTE: the correct answer is never sent
        }
        sb.append("]}");
        sendJson(ex, 200, sb.toString());
    }

    static void handleSubmit(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use POST")); return; }
        Map<String, String> form = parseForm(readBody(ex));
        Session s = SESSIONS.get(form.get("token"));
        if (s == null) { sendJson(ex, 401, error("Session expired. Please log in again.")); return; }

        synchronized (s) {
            if (s.submitted) { sendJson(ex, 200, s.cachedResult); return; }

            // Parse "1,-1,3,0,..." (-1 = unanswered)
            int total = QUESTIONS.size();
            int[] chosen = new int[total];
            Arrays.fill(chosen, -1);
            String[] parts = form.getOrDefault("answers", "").split(",");
            for (int i = 0; i < total && i < parts.length; i++) {
                try { chosen[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException ignored) {}
            }

            boolean late = remainingSeconds(s) < -GRACE_SECONDS;
            int score = 0;
            StringBuilder review = new StringBuilder("[");
            for (int i = 0; i < total; i++) {
                Question q = QUESTIONS.get(i);
                boolean ok = !late && chosen[i] == q.answer();
                if (ok) score++;
                if (i > 0) review.append(',');
                review.append("{\"chosen\":").append(chosen[i])
                      .append(",\"correct\":").append(q.answer())
                      .append(",\"isCorrect\":").append(ok).append('}');
            }
            review.append(']');

            int percentage = Math.round(score * 100f / total);
            s.cachedResult = "{\"name\":\"" + esc(s.user) + "\",\"score\":" + score + ",\"total\":" + total
                    + ",\"percentage\":" + percentage + ",\"passed\":" + (percentage >= PASS_PERCENT)
                    + ",\"late\":" + late + ",\"review\":" + review + "}";
            s.submitted = true;
            FINISHED_USERS.add(s.user);
            sendJson(ex, 200, s.cachedResult);
        }
    }

    // ---- Static files (HTML / CSS / JS) ----------------------------------------
    static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = PUBLIC_DIR.resolve(path.substring(1)).normalize();

        if (!file.startsWith(PUBLIC_DIR) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".css")  ? "text/css; charset=utf-8"
                    : name.endsWith(".js")   ? "application/javascript; charset=utf-8"
                    : name.endsWith(".png")  ? "image/png"
                    : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                    : name.endsWith(".svg")  ? "image/svg+xml"
                    : name.endsWith(".ico")  ? "image/x-icon"
                    : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    // ---- Helpers ---------------------------------------------------------------
    static long remainingSeconds(Session s) {
        long elapsed = (System.currentTimeMillis() - s.startMillis) / 1000;
        return DURATION_SECONDS - elapsed;
    }

    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static Map<String, String> parseForm(String data) {
        Map<String, String> map = new HashMap<>();
        if (data == null || data.isEmpty()) return map;
        for (String pair : data.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    static String error(String msg) { return "{\"error\":\"" + esc(msg) + "\"}"; }

    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
