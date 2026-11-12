package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compartir lo traducido con la comunidad: "Enviar" (pull request con la cuenta de GitHub del jugador) o
 * "Exportar carpeta" (para subirla a mano desde la página del repo, sin conectar la cuenta).
 */
public class ShareScreen extends BaseScreen {
    private static final Path LOGIN_FILE = Shared.DIR.resolve("github.json");
    private static final Path SENT_FILE = Config.DIR.resolve("enviados.json");
    private static final Path EXPORT_DIR = Config.DIR.resolve("para-compartir");
    private static final String UPLOAD_URL = "https://github.com/" + GitHubShare.OWNER + "/" + GitHubShare.REPO + "/upload/main";

    private enum Step { LOADING, READY, LOGIN, SENDING, DONE, ERROR }

    private final ConfigScreen parent;
    private final String target;
    private volatile Step step = Step.LOADING;
    private volatile CommunityExport.Result result;
    private volatile GitHubShare.Login login = GitHubShare.loadLogin(LOGIN_FILE);
    private volatile GitHubShare.DeviceCode code;
    private volatile String prUrl;
    private volatile String message;
    private volatile boolean cancelLogin;
    private int W, left;

    public ShareScreen(ConfigScreen parent, String target) {
        super(Component.literal("Compartir con la comunidad"));
        this.parent = parent;
        this.target = target;
        background("Traductor ES - compartir", this::load);
    }

    // ---------- Trabajo en segundo plano ----------

    private void background(String name, Runnable work) {
        Thread t = new Thread(() -> {
            try {
                work.run();
            } catch (Exception e) {
                TraductorES.LOG.error("[Traductor ES] Compartir", e);
                fail("Algo salió mal: " + e.getMessage());
            }
            Minecraft.getInstance().execute(this::rebuildWidgets);
        }, name);
        t.setDaemon(true);
        t.start();
    }

    private void fail(String msg) {
        message = msg;
        step = Step.ERROR;
    }

    /** Baja lo último del repo (para no pisar nada) y arma los archivos con lo nuevo. */
    private void load() {
        Path cacheRoot = TranslationCache.ROOT;
        Path sharedDir = Config.DIR.resolve("compartidas");
        try {
            for (Map.Entry<String, Set<String>> v : namespacesByVariant(cacheRoot).entrySet()) {
                new SharedTranslations(sharedDir, v.getKey(), List.of()).update(v.getValue(), SharedTranslations.BASE_URL, SharedTranslations.HTTP);
            }
            result = CommunityExport.build(cacheRoot, sharedDir, Corrections.load(), target).withoutSent(sent());
            step = Step.READY;
        } catch (IOException e) {
            fail("No se pudieron leer las traducciones: " + e.getMessage());
        }
    }

    private static Map<String, Set<String>> namespacesByVariant(Path cacheRoot) throws IOException {
        Map<String, Set<String>> out = new HashMap<>();
        if (!Files.isDirectory(cacheRoot)) return out;
        try (Stream<Path> files = Files.walk(cacheRoot, 2)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String variant = p.getParent().getFileName().toString();
                if (!SpanishTarget.CODES.contains(variant)) continue;
                String name = p.getFileName().toString();
                out.computeIfAbsent(variant, k -> new HashSet<>()).add(name.substring(0, name.length() - 5).replace("__", "/"));
            }
        }
        return out;
    }

    private void send() {
        if (login == null) {
            step = Step.LOGIN;
            cancelLogin = false;
            background("Traductor ES - GitHub", this::loginThenSend);
        } else {
            step = Step.SENDING;
            background("Traductor ES - enviar", this::doSend);
        }
    }

    private void loginThenSend() {
        GitHubShare gh = GitHubShare.real();
        try {
            GitHubShare.DeviceCode c = gh.startLogin();
            code = c;
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(c.userCode());
                Util.getPlatform().openUri(c.verificationUri());
                rebuildWidgets();
            });
            GitHubShare.Login l = gh.waitForLogin(c, () -> cancelLogin);
            GitHubShare.saveLogin(LOGIN_FILE, l);
            login = l;
            code = null;
            step = Step.SENDING;
            Minecraft.getInstance().execute(this::rebuildWidgets);
            doSend();
        } catch (IOException e) {
            code = null;
            if (cancelLogin) { step = Step.READY; return; }
            fail(e.getMessage());
        }
    }

    private void doSend() {
        CommunityExport.Result r = result;
        try {
            prUrl = GitHubShare.real().share(login, r.files(), CommunityExport.title(r), body(r));
            markSent(r);
            result = new CommunityExport.Result(Map.of(), Map.of());
            step = Step.DONE;
        } catch (GitHubShare.LoginNeeded e) {
            forgetLogin();
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    private static List<String> variants(CommunityExport.Result r) {
        return r.files().keySet().stream().map(p -> p.substring(0, p.indexOf('/'))).distinct().toList();
    }

    private static String body(CommunityExport.Result r) {
        StringBuilder sb = new StringBuilder("Enviado desde Traductor ES.\n\n");
        sb.append(r.newAi()).append(" de la IA y ").append(r.newRevised())
                .append(r.newRevised() == 1 ? " revisada" : " revisadas").append(" por una persona.\n\n");
        int n = 0;
        for (Map.Entry<String, int[]> e : r.added().entrySet()) {
            if (n++ == 60) { sb.append("- …\n"); break; }
            sb.append("- `").append(e.getKey()).append("`: ").append(e.getValue()[0] + e.getValue()[1]).append('\n');
        }
        return sb.toString();
    }

    /** Arma la carpeta para subir a mano, la abre y abre la página de subida del repo. */
    private void exportFolder() {
        try {
            deleteExportDir();
            CommunityExport.writeTo(EXPORT_DIR, result.files());
            Util.getPlatform().openFile(EXPORT_DIR.toFile());
            Util.getPlatform().openUri(UPLOAD_URL);
            minecraft.keyboardHandler.setClipboard(CommunityExport.title(result));
            message = "Listo. En la página que se abrió, arrastrá la carpeta " + String.join(" y ", variants(result))
                    + " (está en config/traductor_es/para-compartir). El título ya está copiado: pegalo con Ctrl+V en el primer "
                    + "campo de abajo y tocá «Propose changes».";
        } catch (IOException e) {
            message = "No se pudo exportar: " + e.getMessage();
        }
    }

    /** Solo la carpeta que arma este botón: así no quedan archivos de una exportación anterior. */
    private static void deleteExportDir() throws IOException {
        if (!Files.isDirectory(EXPORT_DIR)) return;
        try (Stream<Path> walk = Files.walk(EXPORT_DIR)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    private void forgetLogin() {
        try {
            GitHubShare.forget(LOGIN_FILE);
        } catch (IOException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo borrar {}: {}", LOGIN_FILE, e.toString());
        }
        login = null;
    }

    private static Set<String> sent() {
        try {
            if (!Files.exists(SENT_FILE)) return Set.of();
            List<String> l = new Gson().fromJson(Files.readString(SENT_FILE, StandardCharsets.UTF_8), new TypeToken<List<String>>() {}.getType());
            return l == null ? Set.of() : new HashSet<>(l);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static void markSent(CommunityExport.Result r) {
        List<String> all = new ArrayList<>(sent());
        for (String content : r.files().values()) all.add(CommunityExport.hash(content));
        try {
            Files.writeString(SENT_FILE, new Gson().toJson(all), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo guardar {}: {}", SENT_FILE, e.toString());
        }
    }

    // ---------- Pantalla ----------

    @Override
    protected void init() {
        W = Math.min(width - 16, 380);
        left = (width - W) / 2;
        int mid = width / 2, y = 150;
        addRenderableWidget(Widgets.button(Component.literal("Volver"), b -> onClose()).bounds(mid - 50, height - 28, 100, 20).build());
        CommunityExport.Result r = result;
        switch (step) {
            case READY -> {
                if (r == null || r.isEmpty()) break;
                var sendBtn = addRenderableWidget(Widgets.button(Component.literal("Enviar a la comunidad").withStyle(ChatFormatting.GOLD), b -> {
                    message = null;
                    send();
                    rebuildWidgets();
                }).bounds(mid - 154, y, 150, 20).tooltip(Component.literal(
                        "Abre un pull request en GitHub con tu cuenta. La primera vez te pide conectarla con un código.")).build());
                if (!GitHubShare.configured()) {
                    sendBtn.active = false;
                    message = "El envío directo todavía no está disponible: usá «Exportar carpeta».";
                }
                addRenderableWidget(Widgets.button(Component.literal("Exportar carpeta"), b -> exportFolder())
                        .bounds(mid + 4, y, 150, 20).tooltip(Component.literal(
                                "Sin conectar la cuenta: arma la carpeta y abre la página del repo para que la subas arrastrándola.")).build());
                if (login != null) {
                    addRenderableWidget(Widgets.button(Component.literal("Desconectar GitHub"), b -> {
                        forgetLogin();
                        rebuildWidgets();
                    }).bounds(mid - 70, y + 52, 140, 20).build());
                }
            }
            case LOGIN -> {
                GitHubShare.DeviceCode c = code;
                if (c != null) {
                    addRenderableWidget(Widgets.button(Component.literal("Copiar código"), b -> minecraft.keyboardHandler.setClipboard(c.userCode()))
                            .bounds(mid - 154, y, 100, 20).build());
                    addRenderableWidget(Widgets.button(Component.literal("Abrir GitHub"), b -> Util.getPlatform().openUri(c.verificationUri()))
                            .bounds(mid - 50, y, 100, 20).build());
                }
                addRenderableWidget(Widgets.button(Component.literal("Cancelar"), b -> {
                    cancelLogin = true;
                    step = Step.READY;
                    rebuildWidgets();
                }).bounds(mid + 54, y, 100, 20).build());
            }
            case DONE -> addRenderableWidget(Widgets.button(Component.literal("Ver en GitHub"), b -> Util.getPlatform().openUri(prUrl))
                    .bounds(mid - 60, y, 120, 20).build());
            case ERROR -> addRenderableWidget(Widgets.button(Component.literal("Probar de nuevo"), b -> {
                message = null;
                step = Step.LOADING;
                rebuildWidgets();
                background("Traductor ES - compartir", this::load);
            }).bounds(mid - 60, y, 120, 20).build());
            default -> {}
        }
    }

    @Override
    protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {
        int mid = width / 2;
        g.drawCenteredString(font, "Compartir con la comunidad", mid, 12, UiKit.GOLD);
        UiKit.wrappedCentered(g, font, Component.literal("Lo que tradujiste le puede ahorrar horas a otro jugador. Se suma al repo "
                + GitHubShare.OWNER + "/" + GitHubShare.REPO + " en GitHub, que el mod baja antes de usar la IA."), mid, 30, W, UiKit.MUTED);
        CommunityExport.Result r = result;
        int y = 80;
        switch (step) {
            case LOADING -> g.drawCenteredString(font, "Buscando qué hay de nuevo…", mid, y, UiKit.MUTED);
            case READY -> {
                if (r == null || r.isEmpty()) {
                    UiKit.wrappedCentered(g, font, Component.literal("No hay nada nuevo para compartir: todo lo tuyo ya está en el repo "
                            + "(o ya lo enviaste y falta que lo acepten)."), mid, y, W, UiKit.TEXT);
                    break;
                }
                g.drawCenteredString(font, Traductor.fmt(r.total()) + " traducciones nuevas en " + r.files().size()
                        + (r.files().size() == 1 ? " archivo" : " archivos"), mid, y, UiKit.WHITE);
                g.drawCenteredString(font, Traductor.fmt(r.newAi()) + " de la IA · " + Traductor.fmt(r.newRevised()) + " corregidas por vos",
                        mid, y + 14, UiKit.MUTED);
                UiKit.wrappedCentered(g, font, Component.literal("Se publican en GitHub con tu usuario. Solo van textos de mods y libros "
                        + "(no misiones), nada de tu PC."), mid, y + 34, W, UiKit.DIM);
                if (login != null) g.drawCenteredString(font, "Conectado a GitHub como @" + login.user(), mid, 180, UiKit.GREEN);
            }
            case LOGIN -> {
                GitHubShare.DeviceCode c = code;
                if (c == null) {
                    g.drawCenteredString(font, "Pidiendo un código a GitHub…", mid, y, UiKit.MUTED);
                } else {
                    UiKit.wrappedCentered(g, font, Component.literal("Se abrió GitHub en el navegador. Pegá este código (ya está copiado) "
                            + "y tocá «Authorize»:"), mid, y, W, UiKit.TEXT);
                    g.drawScaledCentered(font, c.userCode(), mid, y + 30, UiKit.GOLD, 2f);
                    g.drawCenteredString(font, "Esperando que autorices…", mid, y + 56, UiKit.DIM);
                }
            }
            case SENDING -> g.drawCenteredString(font, "Enviando tus traducciones…", mid, y, UiKit.MUTED);
            case DONE -> {
                g.drawCenteredString(font, "¡Enviado, gracias!", mid, y, UiKit.GREEN);
                UiKit.wrappedCentered(g, font, Component.literal("Cuando se acepte, las va a bajar cualquiera que use el mod. "
                        + "Podés seguir el pedido en GitHub."), mid, y + 16, W, UiKit.MUTED);
            }
            default -> {}
        }
        if (message != null) UiKit.wrappedCentered(g, font, Component.literal(message), mid, 176 + (login != null && step == Step.READY ? 26 : 0),
                W, step == Step.ERROR ? UiKit.RED : UiKit.YELLOW);
    }

    @Override
    public void onClose() {
        cancelLogin = true;
        minecraft.setScreen(parent);
    }
}
