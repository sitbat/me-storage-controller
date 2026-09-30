package dev.mestorage.controller.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** A client preference, independent of worlds, servers and common configuration. */
public final class ClientAppearance {
    private static boolean dark = load();
    private ClientAppearance() {}

    public static boolean isDark() { return dark; }

    public static void setDark(boolean value) {
        dark = value;
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "# ME Storage Controller client appearance\ntheme=" + (dark ? "dark" : "light") + "\n", StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            // A read-only game directory should not prevent changing the current session's appearance.
        }
    }

    private static Path path() { return FMLPaths.CONFIGDIR.get().resolve("me-storage-controller-client.properties"); }
    private static boolean load() {
        var values = new Properties();
        try (var reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) { values.load(reader); }
        catch (java.io.IOException | IllegalArgumentException ignored) { return false; }
        return "dark".equalsIgnoreCase(values.getProperty("theme", "light"));
    }
}
