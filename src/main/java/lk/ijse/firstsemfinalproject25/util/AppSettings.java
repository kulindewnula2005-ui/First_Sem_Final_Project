package lk.ijse.firstsemfinalproject25.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Small settings file kept on this computer (theme, garage details, last backup). */
public final class AppSettings {

    public static final String THEME = "theme";
    public static final String GARAGE_NAME = "garage.name";
    public static final String GARAGE_ADDRESS = "garage.address";
    public static final String GARAGE_PHONE = "garage.phone";
    public static final String GARAGE_EMAIL = "garage.email";
    public static final String LAST_BACKUP = "backup.last";
    public static final String BACKUP_FOLDER = "backup.folder";

    private static final Path FILE =
            Paths.get(System.getProperty("user.home"), ".bike-garage", "settings.properties");
    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private AppSettings() { }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                PROPS.load(reader);
            } catch (IOException ignore) {
                // unreadable file: start with the defaults
            }
        }
    }

    public static synchronized String get(String key, String defaultValue) {
        load();
        return PROPS.getProperty(key, defaultValue);
    }

    public static synchronized void set(String key, String value) {
        load();
        if (value == null) {
            PROPS.remove(key);
        } else {
            PROPS.setProperty(key, value);
        }
    }

    /** Writes the settings to disk. Call it after set(...). */
    public static synchronized void save() throws IOException {
        load();
        Files.createDirectories(FILE.getParent());
        try (Writer out = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            PROPS.store(out, "Bike Garage Management System settings");
        }
    }
}