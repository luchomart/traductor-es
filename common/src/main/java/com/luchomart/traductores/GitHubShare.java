package com.luchomart.traductores;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Manda las traducciones del jugador al repo de la comunidad como un pull request, con su propia cuenta de GitHub.
 * <p>
 * Inicio de sesión: "device flow" de GitHub (como en los televisores): el mod muestra un código, el jugador lo pega en
 * github.com/login/device y autoriza. No hace falta servidor ni secreto: el Client ID es público.
 * <p>
 * Envío: fork del repo (si no es el dueño) → rama nueva desde el main del repo → archivos → commit → pull request.
 * index.json no va: lo rehace GitHub solo al aceptar el pull request. No usa nada de Minecraft: se prueba con un
 * GitHub falso.
 */
public final class GitHubShare {
    /** Client ID de la OAuth App "Traductor ES" (público; el device flow no usa secreto). */
    public static final String CLIENT_ID = "Ov23liw2eFQxGCDrozy2";
    public static final String OWNER = "luchomart";
    public static final String REPO = "traductor-es-traducciones";
    /** Permiso para hacer el fork, subir la rama y abrir el pull request en repos públicos. */
    static final String SCOPE = "public_repo";
    /** Tamaño de cada tanda de archivos al armar el árbol del commit (los tests lo achican). */
    int treeBatchChars = 1_000_000;
    private static final String API = "https://api.github.com";

    public record Response(int status, String body) {
        boolean ok() { return status >= 200 && status < 300; }
        JsonObject json() {
            JsonElement e = JsonParser.parseString(body.isBlank() ? "{}" : body);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        }
    }

    /** Una petición HTTP con cuerpo JSON. En el juego usa HTTP de verdad; en los tests, un GitHub falso. */
    @FunctionalInterface
    public interface Http {
        Response send(String method, String url, String token, String jsonBody) throws IOException;
    }

    /** Lo que muestra el juego mientras el jugador autoriza. */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int intervalSeconds, int expiresInSeconds) {}

    /** Sesión guardada: token y usuario. */
    public record Login(String token, String user) {}

    /** El usuario no autorizó, el código venció o el token ya no vale: hay que volver a conectar. */
    public static final class LoginNeeded extends IOException {
        LoginNeeded(String msg) { super(msg); }
    }

    private final Http http;
    private final LongConsumer sleep;

    public GitHubShare(Http http, LongConsumer sleep) {
        this.http = http;
        this.sleep = sleep;
    }

    /** Con HTTP de verdad y esperas de verdad. */
    public static GitHubShare real() {
        return new GitHubShare(HTTP, ms -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static boolean configured() {
        return !CLIENT_ID.isBlank();
    }

    // ---------- Inicio de sesión ----------

    public DeviceCode startLogin() throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("client_id", CLIENT_ID);
        req.addProperty("scope", SCOPE);
        Response r = http.send("POST", "https://github.com/login/device/code", null, req.toString());
        JsonObject o = r.json();
        if (!r.ok() || !o.has("device_code")) throw new IOException("GitHub no dio un código (" + describe(r) + ")");
        return new DeviceCode(o.get("device_code").getAsString(), o.get("user_code").getAsString(),
                o.get("verification_uri").getAsString(),
                o.has("interval") ? o.get("interval").getAsInt() : 5,
                o.has("expires_in") ? o.get("expires_in").getAsInt() : 900);
    }

    /**
     * Espera a que el jugador autorice (consultando cada pocos segundos, como pide GitHub) y devuelve la sesión.
     * cancelled se consulta en cada vuelta.
     */
    public Login waitForLogin(DeviceCode code, java.util.function.BooleanSupplier cancelled) throws IOException {
        long interval = Math.max(1, code.intervalSeconds()) * 1000L;
        long deadline = System.currentTimeMillis() + code.expiresInSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.getAsBoolean()) throw new LoginNeeded("Cancelado");
            sleep.accept(interval);
            JsonObject req = new JsonObject();
            req.addProperty("client_id", CLIENT_ID);
            req.addProperty("device_code", code.deviceCode());
            req.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            JsonObject o = http.send("POST", "https://github.com/login/oauth/access_token", null, req.toString()).json();
            if (o.has("access_token")) {
                String token = o.get("access_token").getAsString();
                return new Login(token, user(token));
            }
            String error = o.has("error") ? o.get("error").getAsString() : "";
            switch (error) {
                case "authorization_pending" -> {}
                case "slow_down" -> interval += 5000; // GitHub pide ir más despacio
                case "access_denied" -> throw new LoginNeeded("No autorizaste el acceso en GitHub.");
                case "expired_token" -> throw new LoginNeeded("El código venció: probá de nuevo.");
                default -> throw new IOException("GitHub respondió: " + (error.isEmpty() ? o : error));
            }
        }
        throw new LoginNeeded("El código venció: probá de nuevo.");
    }

    private String user(String token) throws IOException {
        Response r = http.send("GET", API + "/user", token, null);
        if (r.status() == 401) throw new LoginNeeded("GitHub no aceptó la sesión: conectá de nuevo.");
        if (!r.ok()) throw new IOException("No se pudo leer tu usuario de GitHub (" + describe(r) + ")");
        return r.json().get("login").getAsString();
    }

    // ---------- Envío ----------

    /**
     * Sube los archivos en una rama nueva y abre el pull request. Devuelve su link.
     *
     * @param files ruta en el repo ("es_ar/ae2.json") → contenido
     */
    public String share(Login login, Map<String, String> files, String title, String body) throws IOException {
        String token = login.token();
        String user = user(token); // de paso comprueba que la sesión sigue valiendo
        String upstream = OWNER + "/" + REPO;
        String base = call("GET", "/repos/" + upstream + "/git/ref/heads/main", token, null, "leer el repo")
                .getAsJsonObject("object").get("sha").getAsString();
        String baseTree = call("GET", "/repos/" + upstream + "/git/commits/" + base, token, null, "leer el repo")
                .getAsJsonObject("tree").get("sha").getAsString();

        // El dueño trabaja en su repo; los demás, en su fork.
        String repo = upstream;
        if (!user.equalsIgnoreCase(OWNER)) {
            JsonObject fork = new JsonObject();
            fork.addProperty("default_branch_only", true);
            repo = call("POST", "/repos/" + upstream + "/forks", token, fork.toString(), "crear tu copia del repo")
                    .get("full_name").getAsString();
            // Un fork viejo puede estar atrasado: se pone al día (si falla no importa, la rama sale del main del repo).
            JsonObject sync = new JsonObject();
            sync.addProperty("branch", "main");
            http.send("POST", API + "/repos/" + repo + "/merge-upstream", token, sync.toString());
        }

        String branch = "traducciones-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        createBranch(repo, branch, base, token);

        // El contenido va dentro del árbol (sin un pedido por archivo, que con muchos mods tardaba minutos).
        // En tandas de ~1 MB, cada una encima de la anterior, para no mandar un pedido gigante.
        String treeSha = baseTree;
        JsonArray tree = new JsonArray();
        int size = 0;
        for (Map.Entry<String, String> f : files.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("path", f.getKey());
            item.addProperty("mode", "100644");
            item.addProperty("type", "blob");
            item.addProperty("content", f.getValue());
            tree.add(item);
            size += f.getValue().length();
            if (size >= treeBatchChars) {
                treeSha = tree(repo, treeSha, tree, token);
                tree = new JsonArray();
                size = 0;
            }
        }
        if (tree.size() > 0) treeSha = tree(repo, treeSha, tree, token);

        JsonObject commit = new JsonObject();
        commit.addProperty("message", title);
        commit.addProperty("tree", treeSha);
        JsonArray parents = new JsonArray();
        parents.add(base);
        commit.add("parents", parents);
        String commitSha = call("POST", "/repos/" + repo + "/git/commits", token, commit.toString(), "armar el envío")
                .get("sha").getAsString();

        JsonObject move = new JsonObject();
        move.addProperty("sha", commitSha);
        call("PATCH", "/repos/" + repo + "/git/refs/heads/" + branch, token, move.toString(), "armar el envío");

        JsonObject pr = new JsonObject();
        pr.addProperty("title", title);
        pr.addProperty("head", repo.substring(0, repo.indexOf('/')) + ":" + branch);
        pr.addProperty("base", "main");
        pr.addProperty("body", body);
        pr.addProperty("maintainer_can_modify", true);
        return call("POST", "/repos/" + upstream + "/pulls", token, pr.toString(), "abrir el pull request")
                .get("html_url").getAsString();
    }

    private String tree(String repo, String base, JsonArray items, String token) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("base_tree", base);
        req.add("tree", items);
        return call("POST", "/repos/" + repo + "/git/trees", token, req.toString(), "armar el envío").get("sha").getAsString();
    }

    /** Crea la rama. Un fork recién creado tarda unos segundos en estar listo: se reintenta hasta ~1 minuto. */
    private void createBranch(String repo, String branch, String sha, String token) throws IOException {
        JsonObject ref = new JsonObject();
        ref.addProperty("ref", "refs/heads/" + branch);
        ref.addProperty("sha", sha);
        Response last = null;
        for (int i = 0; i < 20; i++) {
            last = http.send("POST", API + "/repos/" + repo + "/git/refs", token, ref.toString());
            if (last.ok()) return;
            if (last.status() == 401) throw new LoginNeeded("GitHub no aceptó la sesión: conectá de nuevo.");
            if (last.status() != 404 && last.status() != 409 && last.status() != 422) break;
            sleep.accept(3000);
        }
        throw new IOException("No se pudo crear la rama en " + repo + " (" + describe(last) + ")");
    }

    private JsonObject call(String method, String path, String token, String body, String what) throws IOException {
        Response r = http.send(method, API + path, token, body);
        if (r.status() == 401) throw new LoginNeeded("GitHub no aceptó la sesión: conectá de nuevo.");
        if (!r.ok()) throw new IOException("No se pudo " + what + " (" + describe(r) + ")");
        return r.json();
    }

    private static String describe(Response r) {
        if (r == null) return "sin respuesta";
        String msg = "";
        try {
            JsonObject o = r.json();
            if (o.has("message")) msg = ": " + o.get("message").getAsString();
            else if (o.has("error_description")) msg = ": " + o.get("error_description").getAsString();
        } catch (Exception ignored) {
        }
        return "HTTP " + r.status() + msg;
    }

    // ---------- Sesión guardada (en ~/.traductor_es, nunca en el modpack) ----------

    public static Login loadLogin(Path file) {
        try {
            if (!Files.exists(file)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("token") && o.has("user") ? new Login(o.get("token").getAsString(), o.get("user").getAsString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveLogin(Path file, Login login) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            try { // solo el usuario lo puede leer (Linux y Mac); en Windows la carpeta del usuario ya es privada
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException ignored) {
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("token", login.token());
        o.addProperty("user", login.user());
        Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
    }

    public static void forget(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    // ---------- HTTP de verdad ----------

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    public static final Http HTTP = (method, url, token, jsonBody) -> {
        java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("Accept", url.startsWith(API) ? "application/vnd.github+json" : "application/json")
                .header("User-Agent", "Traductor-ES")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (jsonBody != null) {
            b.header("Content-Type", "application/json")
                    .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            b.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
        }
        try {
            java.net.http.HttpResponse<String> r = CLIENT.send(b.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrumpido", e);
        }
    };
}
