package com.mtsharpgrain.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class WorldGit {
    private WorldGit() {}

    public static Path worldsRoot() {
        var dir = Path.of(System.getProperty("user.home"), ".mtsharpgrain", "worlds");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create " + dir, e);
        }
        return dir;
    }

    public static Path worldDir(String name) {
        var safe = sanitize(name);
        var dir = worldsRoot().resolve(safe);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create world dir: " + dir, e);
        }
        return dir;
    }

    public static List<String> listWorlds() {
        try (var stream = Files.list(worldsRoot())) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean isGit(Path dir) {
        return Files.isDirectory(dir.resolve(".git"));
    }

    public static String sanitize(String name) {
        if (name == null) throw new IllegalArgumentException("empty name");
        var n = name.trim();
        if (!n.matches("[A-Za-z0-9_\\-]{1,40}")) {
            throw new IllegalArgumentException("name must be 1-40 letters, digits, _ or -");
        }
        return n;
    }

    /** Accepts https://github.com/owner/repo or https://github.com/owner/repo.git */
    public static void checkGithubUrl(String url) {
        if (url == null) throw new IllegalArgumentException("empty url");
        var u = url.trim();
        if (u.contains(" ") || u.contains("..") || u.contains("\\") || u.contains("@")) {
            throw new IllegalArgumentException("bad url");
        }
        if (!u.startsWith("https://github.com/") || u.chars().filter(c -> c == '/').count() < 4) {
            throw new IllegalArgumentException("only https://github.com/owner/repo");
        }
    }

    public static String nameFromUrl(String url) {
        var u = url.trim();
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        var slash = u.lastIndexOf('/');
        return sanitize(u.substring(slash + 1));
    }

    public static void cloneWorld(String gitUrl, String worldName) throws IOException, InterruptedException {
        checkGithubUrl(gitUrl);
        var name = sanitize(worldName);
        var dest = worldsRoot().resolve(name);
        if (Files.exists(dest) && Files.list(dest).findAny().isPresent()) {
            throw new IOException("world already exists: " + name);
        }
        Files.createDirectories(dest.getParent());
        var pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl.trim(), dest.toString());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        var code = pb.start().waitFor();
        if (code != 0) {
            deleteTree(dest);
            throw new IOException("git clone failed (exit " + code + "). Is git installed?");
        }
    }

    public static void pull(Path dir) throws IOException, InterruptedException {
        if (!isGit(dir)) throw new IOException("not a git world");
        var pb = new ProcessBuilder("git", "pull", "--ff-only");
        pb.directory(dir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        var code = pb.start().waitFor();
        if (code != 0) throw new IOException("git pull failed (exit " + code + ")");
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}