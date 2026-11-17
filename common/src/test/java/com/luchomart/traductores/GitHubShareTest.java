package com.luchomart.traductores;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class GitHubShareTest {
    /** GitHub falso: respuestas por "MÉTODO ruta" (varias en cola: se usan en orden y la última se repite). */
    private static final class FakeGitHub implements GitHubShare.Http {
        final Map<String, Deque<GitHubShare.Response>> routes = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        final Map<String, String> bodies = new HashMap<>();

        FakeGitHub on(String route, int status, String body) {
            routes.computeIfAbsent(route, k -> new ArrayDeque<>()).add(new GitHubShare.Response(status, body));
            return this;
        }

        @Override
        public GitHubShare.Response send(String method, String url, String token, String jsonBody) throws IOException {
            String path = url.replace("https://api.github.com", "").replace("https://github.com", "");
            String route = method + " " + path;
            calls.add(route);
            if (jsonBody != null) bodies.put(route, jsonBody);
            Deque<GitHubShare.Response> q = routes.get(route);
            if (q == null) {
                // Los blobs tienen todos la misma ruta: un sha por llamada.
                if (route.endsWith("/git/blobs")) return new GitHubShare.Response(201, "{\"sha\": \"blob" + calls.size() + "\"}");
                // La rama lleva la hora en el nombre.
                if (route.startsWith("PATCH ") && route.contains("/git/refs/heads/traducciones-")) return new GitHubShare.Response(200, "{}");
                return new GitHubShare.Response(404, "{\"message\": \"Not Found\"}");
            }
            return q.size() > 1 ? q.poll() : q.peek();
        }

        /** Lo común a cualquier envío. */
        FakeGitHub repoBase(String user) {
            on("GET /user", 200, "{\"login\": \"" + user + "\"}");
            on("GET /repos/luchomart/traductor-es-traducciones/git/ref/heads/main", 200, "{\"object\": {\"sha\": \"base1\"}}");
            on("GET /repos/luchomart/traductor-es-traducciones/git/commits/base1", 200, "{\"tree\": {\"sha\": \"tree1\"}}");
            on("POST /repos/luchomart/traductor-es-traducciones/pulls", 201, "{\"html_url\": \"https://github.com/luchomart/traductor-es-traducciones/pull/7\"}");
            return this;
        }

        FakeGitHub commitsIn(String repo) {
            on("POST /repos/" + repo + "/git/trees", 201, "{\"sha\": \"tree2\"}");
            on("POST /repos/" + repo + "/git/commits", 201, "{\"sha\": \"commit2\"}");
            return this;
        }
    }

    private static final Map<String, String> FILES = new TreeMap<>(Map.of(
            "es_ar/ae2.json", "{\"revisadas\": {}, \"ia\": {\"a\": \"b\"}}",
            "es_ar/create.json", "{\"revisadas\": {}, \"ia\": {\"c\": \"d\"}}"));

    private final List<Long> sleeps = new ArrayList<>();

    private GitHubShare share(FakeGitHub gh) {
        return new GitHubShare(gh, sleeps::add);
    }

    @Test
    void otroJugadorHaceForkRamaYPullRequest() throws IOException {
        FakeGitHub gh = new FakeGitHub().repoBase("jugador").commitsIn("jugador/traductor-es-traducciones")
                .on("POST /repos/luchomart/traductor-es-traducciones/forks", 202, "{\"full_name\": \"jugador/traductor-es-traducciones\"}")
                .on("POST /repos/jugador/traductor-es-traducciones/merge-upstream", 200, "{}")
                // El fork recién creado todavía no está listo: la primera vez falla.
                .on("POST /repos/jugador/traductor-es-traducciones/git/refs", 422, "{\"message\": \"Object does not exist\"}")
                .on("POST /repos/jugador/traductor-es-traducciones/git/refs", 201, "{}");
        String url = share(gh).share(new GitHubShare.Login("tok", "jugador"), FILES, "Traducciones", "cuerpo");

        assertEquals("https://github.com/luchomart/traductor-es-traducciones/pull/7", url);
        assertFalse(gh.calls.stream().anyMatch(c -> c.endsWith("/git/blobs")), "el contenido va dentro del árbol");
        assertEquals(1, gh.calls.stream().filter(c -> c.endsWith("/git/trees")).count());
        assertTrue(gh.calls.stream().anyMatch(c -> c.startsWith("PATCH /repos/jugador/traductor-es-traducciones/git/refs/heads/traducciones-")));
        assertEquals(List.of(3000L), sleeps, "esperó una vez a que el fork esté listo");

        var pr = JsonParser.parseString(gh.bodies.get("POST /repos/luchomart/traductor-es-traducciones/pulls")).getAsJsonObject();
        assertTrue(pr.get("head").getAsString().startsWith("jugador:traducciones-"));
        assertEquals("main", pr.get("base").getAsString());
        var tree = JsonParser.parseString(gh.bodies.get("POST /repos/jugador/traductor-es-traducciones/git/trees")).getAsJsonObject();
        assertEquals("tree1", tree.get("base_tree").getAsString());
        assertEquals(2, tree.getAsJsonArray("tree").size());
        assertEquals("{\"revisadas\": {}, \"ia\": {\"a\": \"b\"}}",
                tree.getAsJsonArray("tree").get(0).getAsJsonObject().get("content").getAsString());
        assertFalse(gh.bodies.values().stream().anyMatch(b -> b.contains("index.json")), "index.json lo rehace GitHub");
    }

    @Test
    void muchosArchivosVanEnTandasEncadenadas() throws IOException {
        String repo = "luchomart/traductor-es-traducciones";
        FakeGitHub gh = new FakeGitHub().repoBase("luchomart")
                .on("POST /repos/" + repo + "/git/refs", 201, "{}")
                .on("POST /repos/" + repo + "/git/trees", 201, "{\"sha\": \"tree2\"}")
                .on("POST /repos/" + repo + "/git/trees", 201, "{\"sha\": \"tree3\"}")
                .on("POST /repos/" + repo + "/git/commits", 201, "{\"sha\": \"commit2\"}");
        List<String> bodies = new ArrayList<>();
        GitHubShare.Http spy = (m, url, token, body) -> {
            if (url.endsWith("/git/trees")) bodies.add(body);
            return gh.send(m, url, token, body);
        };
        GitHubShare share = new GitHubShare(spy, sleeps::add);
        share.treeBatchChars = 10; // cada archivo llena una tanda
        share.share(new GitHubShare.Login("tok", "luchomart"), FILES, "T", "B");

        assertEquals(2, bodies.size());
        assertEquals("tree1", JsonParser.parseString(bodies.get(0)).getAsJsonObject().get("base_tree").getAsString());
        assertEquals("tree2", JsonParser.parseString(bodies.get(1)).getAsJsonObject().get("base_tree").getAsString(),
                "la segunda tanda va encima de la primera");
        var commit = JsonParser.parseString(gh.bodies.get("POST /repos/" + repo + "/git/commits")).getAsJsonObject();
        assertEquals("tree3", commit.get("tree").getAsString());
    }

    @Test
    void elDuenoNoHaceForkYTrabajaEnSuRepo() throws IOException {
        FakeGitHub gh = new FakeGitHub().repoBase("luchomart").commitsIn("luchomart/traductor-es-traducciones")
                .on("POST /repos/luchomart/traductor-es-traducciones/git/refs", 201, "{}");
        share(gh).share(new GitHubShare.Login("tok", "luchomart"), FILES, "T", "B");
        assertFalse(gh.calls.stream().anyMatch(c -> c.contains("/forks")));
        var pr = JsonParser.parseString(gh.bodies.get("POST /repos/luchomart/traductor-es-traducciones/pulls")).getAsJsonObject();
        assertTrue(pr.get("head").getAsString().startsWith("luchomart:traducciones-"));
    }

    @Test
    void sesionVencidaPideConectarDeNuevo() {
        FakeGitHub gh = new FakeGitHub().on("GET /user", 401, "{\"message\": \"Bad credentials\"}");
        assertThrows(GitHubShare.LoginNeeded.class, () -> share(gh).share(new GitHubShare.Login("viejo", "x"), FILES, "T", "B"));
    }

    @Test
    void inicioDeSesionEsperaYRespetaSlowDown() throws IOException {
        FakeGitHub gh = new FakeGitHub()
                .on("POST /login/device/code", 200, "{\"device_code\": \"dev\", \"user_code\": \"ABCD-1234\", "
                        + "\"verification_uri\": \"https://github.com/login/device\", \"interval\": 5, \"expires_in\": 900}")
                .on("POST /login/oauth/access_token", 200, "{\"error\": \"authorization_pending\"}")
                .on("POST /login/oauth/access_token", 200, "{\"error\": \"slow_down\"}")
                .on("POST /login/oauth/access_token", 200, "{\"access_token\": \"tok\"}")
                .on("GET /user", 200, "{\"login\": \"jugador\"}");
        GitHubShare s = share(gh);
        GitHubShare.DeviceCode code = s.startLogin();
        assertEquals("ABCD-1234", code.userCode());
        GitHubShare.Login login = s.waitForLogin(code, () -> false);
        assertEquals(new GitHubShare.Login("tok", "jugador"), login);
        assertEquals(List.of(5000L, 5000L, 10000L), sleeps, "después de slow_down espera 5 segundos más");
    }

    @Test
    void siNoAutorizaSeAvisa() {
        FakeGitHub gh = new FakeGitHub().on("POST /login/oauth/access_token", 200, "{\"error\": \"access_denied\"}");
        GitHubShare.DeviceCode code = new GitHubShare.DeviceCode("dev", "X", "u", 1, 60);
        assertThrows(GitHubShare.LoginNeeded.class, () -> share(gh).waitForLogin(code, () -> false));
    }

    @Test
    void laSesionSeGuardaYSeBorra(@TempDir Path dir) throws IOException {
        Path f = dir.resolve(".traductor_es/github.json");
        assertNull(GitHubShare.loadLogin(f));
        GitHubShare.saveLogin(f, new GitHubShare.Login("tok", "jugador"));
        assertEquals(new GitHubShare.Login("tok", "jugador"), GitHubShare.loadLogin(f));
        GitHubShare.forget(f);
        assertNull(GitHubShare.loadLogin(f));
    }
}
