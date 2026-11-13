package lk.ijse.firstsemfinalproject25.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Turns a TextField into a "type to search, click to pick" box. */
public class SearchPopup<T> {

    private static final int MAX_ROWS = 6;
    private static final double ROW_HEIGHT = 38;

    private final TextField field;
    private final Function<T, String> label;      // text shown in the list and in the field
    private final Function<T, String> haystack;   // text that the typed search is matched against
    private final Popup popup = new Popup();
    private final ListView<T> list = new ListView<>();

    private List<T> source = new ArrayList<>();
    private T selected;
    private boolean updating;
    private Consumer<T> onChange = item -> { };

    public SearchPopup(TextField field, Function<T, String> label, Function<T, String> haystack) {
        this.field = field;
        this.label = label;
        this.haystack = haystack;

        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setConsumeAutoHidingEvents(false);   // a click on another button still works
        popup.getContent().add(list);

        list.getStyleClass().add("search-list");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setFocusTraversable(false);
        URL css = getClass().getResource("/styles/service.css");
        if (css != null) {
            list.getStylesheets().add(css.toExternalForm());
        }
        list.setCellFactory(lv -> new ListCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : label.apply(item));
            }
        });

        list.setOnMouseClicked(e -> {
            T item = list.getSelectionModel().getSelectedItem();
            if (item != null) {
                choose(item);
            }
        });

        // typing: forget the previous pick and show matches
        field.textProperty().addListener((obs, old, text) -> {
            if (updating) {
                return;
            }
            if (selected != null) {
                selected = null;
                onChange.accept(null);
            }
            refresh(false);
        });

        // clicking the field shows the full list (or the matches)
        field.setOnMouseClicked(e -> refresh(selected != null));

        // keyboard: Down / Up / Enter / Esc
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!popup.isShowing()) {
                if (e.getCode() == javafx.scene.input.KeyCode.DOWN) {
                    refresh(selected != null);
                    e.consume();
                }
                return;
            }
            switch (e.getCode()) {
                case DOWN -> { move(1); e.consume(); }
                case UP -> { move(-1); e.consume(); }
                case ENTER -> {
                    T item = list.getSelectionModel().getSelectedItem();
                    if (item == null && !list.getItems().isEmpty()) {
                        item = list.getItems().get(0);
                    }
                    if (item != null) {
                        choose(item);
                    }
                    e.consume();
                }
                case ESCAPE -> { popup.hide(); e.consume(); }
                default -> { }
            }
        });

        field.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                Platform.runLater(() -> {
                    if (!list.isHover()) {
                        popup.hide();
                    }
                });
            }
        });

        // page changed / window closed while the list was open
        field.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                popup.hide();
            }
        });
    }

    /* ---------- public API ---------- */
    public void setSource(List<T> items) {
        this.source = new ArrayList<>(items);
    }

    public T getSelected() {
        return selected;
    }

    public void setOnChange(Consumer<T> onChange) {
        this.onChange = onChange;
    }

    /** Puts an item into the field. notify=false does not call onChange. */
    public void select(T item, boolean notify) {
        updating = true;
        selected = item;
        field.setText(item == null ? "" : label.apply(item));
        field.positionCaret(field.getText().length());
        updating = false;
        if (notify) {
            onChange.accept(item);
        }
    }

    public void clear() {
        select(null, false);
        popup.hide();
    }

    /* ---------- internals ---------- */
    private void choose(T item) {
        select(item, true);
        popup.hide();
        field.requestFocus();
    }

    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) {
            return;
        }
        int idx = list.getSelectionModel().getSelectedIndex();
        int next = Math.max(0, Math.min(size - 1, idx + delta));
        list.getSelectionModel().select(next);
        list.scrollTo(next);
    }

    private void refresh(boolean showAll) {
        String q = showAll || field.getText() == null ? "" : field.getText().trim().toLowerCase();

        List<T> matches = new ArrayList<>();
        for (T item : source) {
            if (q.isEmpty()
                    || haystack.apply(item).toLowerCase().contains(q)
                    || label.apply(item).toLowerCase().contains(q)) {
                matches.add(item);
            }
        }
        list.setItems(FXCollections.observableArrayList(matches));
        list.getSelectionModel().clearSelection();

        if (matches.isEmpty()) {
            popup.hide();
            return;
        }
        int rows = Math.min(matches.size(), MAX_ROWS);
        list.setPrefHeight(rows * ROW_HEIGHT + 6);
        list.setPrefWidth(field.getWidth());
        list.setMinWidth(field.getWidth());

        if (!popup.isShowing() && field.getScene() != null) {
            Bounds b = field.localToScreen(field.getBoundsInLocal());
            if (b != null) {
                popup.show(field, b.getMinX(), b.getMaxY() + 4);
            }
        }
    }
}