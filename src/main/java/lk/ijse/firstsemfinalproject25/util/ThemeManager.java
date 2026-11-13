package lk.ijse.firstsemfinalproject25.util;

import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Switches the whole application (every window, dialog and pop-up) between Dark and Light. */
public final class ThemeManager {

    public enum Theme { DARK, LIGHT }

    private static final String LIGHT_CLASS = "light";
    private static final Set<Window> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());

    private static Theme current = Theme.DARK;
    private static boolean started = false;
    private static String lightCss;

    private ThemeManager() { }

    /** Call once at start-up (AppInitializer). Safe to call again. */
    public static void init() {
        if (started) {
            return;
        }
        started = true;

        current = "light".equalsIgnoreCase(AppSettings.get(AppSettings.THEME, "dark"))
                ? Theme.LIGHT : Theme.DARK;

        URL css = ThemeManager.class.getResource("/styles/light.css");
        lightCss = css == null ? null : css.toExternalForm();

        // every window that opens from now on (dialogs, pop-ups, new stages) gets the theme
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window window : change.getAddedSubList()) {
                        hook(window);
                    }
                }
            }
        });
        for (Window window : new ArrayList<>(Window.getWindows())) {
            hook(window);
        }
    }

    public static Theme current() {
        return current;
    }

    /** Changes the theme everywhere and remembers it. */
    public static void setTheme(Theme theme) {
        init();
        current = theme;
        AppSettings.set(AppSettings.THEME, theme == Theme.LIGHT ? "light" : "dark");
        try {
            AppSettings.save();
        } catch (IOException ex) {
            ex.printStackTrace();   // the theme still changes, it just will not be remembered
        }
        for (Window window : new ArrayList<>(Window.getWindows())) {
            apply(window.getScene());
        }
    }

    private static void hook(Window window) {
        apply(window.getScene());
        if (HOOKED.add(window)) {
            window.sceneProperty().addListener((obs, old, scene) -> apply(scene));
        }
    }

    private static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        Parent root = scene.getRoot();
        if (root == null) {
            return;
        }
        if (current == Theme.LIGHT) {
            if (!root.getStyleClass().contains(LIGHT_CLASS)) {
                root.getStyleClass().add(LIGHT_CLASS);
            }
            if (lightCss != null && !scene.getStylesheets().contains(lightCss)) {
                scene.getStylesheets().add(lightCss);
            }
        } else {
            root.getStyleClass().remove(LIGHT_CLASS);
            if (lightCss != null) {
                scene.getStylesheets().remove(lightCss);
            }
        }
    }
}