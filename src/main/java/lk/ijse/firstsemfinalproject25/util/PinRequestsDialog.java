package lk.ijse.firstsemfinalproject25.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import lk.ijse.firstsemfinalproject25.model.PasswordResetModel;
import lk.ijse.firstsemfinalproject25.model.PasswordResetModel.PinRequest;

import java.net.URL;
import java.util.List;

/**
 * SUPER ADMIN: the "PIN Requests" inbox.
 * Users who pressed "Request PIN" on the Forgot Password page appear here (live, every 5 seconds).
 * Press "Generate PIN" -> the PIN window opens -> tells/sends the plain 6-digit PIN to that user.
 */
public class PinRequestsDialog {

    private final Window owner;
    private final PasswordResetModel resetModel = new PasswordResetModel();

    private final VBox list = new VBox(8);
    private final Label lblSummary = new Label();
    private DialogPane pane;
    private Timeline refresh;

    public PinRequestsDialog(Window owner) {
        this.owner = owner;
    }

    public void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("PIN Requests");

        pane = dialog.getDialogPane();
        styleDialog(pane);
        pane.setPrefWidth(560);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Label title = new Label("🔔  PIN Requests");
        title.getStyleClass().add("form-title");

        Label help = new Label("Users who forgot their password appear here. Press Generate PIN, "
                + "then tell the PIN privately to that user (or let them view it on their dashboard).");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #b5b5ba;");

        lblSummary.setStyle("-fx-text-fill: #fbbf24; -fx-font-weight: bold;");

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(320);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(10, title, help, lblSummary, scroll);
        content.setPadding(new Insets(6, 4, 4, 4));
        pane.setContent(content);

        reload();

        // LIVE: new requests show up automatically every 5 seconds
        refresh = new Timeline(new KeyFrame(Duration.seconds(5), e -> reload()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        dialog.setOnHidden(e -> refresh.stop());

        dialog.showAndWait();
    }

    /* ======================= list ======================= */
    private void reload() {
        try {
            List<PinRequest> requests = resetModel.getPendingRequests();
            list.getChildren().clear();

            if (requests.isEmpty()) {
                lblSummary.setText("No pending requests 🎉");
                Label none = new Label("Nobody is waiting for a PIN right now.");
                none.setStyle("-fx-text-fill: #8a8a90;");
                list.getChildren().add(none);
                return;
            }
            lblSummary.setText(requests.size() + (requests.size() == 1 ? " request waiting" : " requests waiting"));
            for (PinRequest r : requests) {
                list.getChildren().add(buildRow(r));
            }
        } catch (Exception ex) {
            lblSummary.setText("Could not load requests: " + ex.getMessage());
        }
    }

    private HBox buildRow(PinRequest r) {
        Label avatar = new Label(initials(r.fullName()));
        avatar.setStyle("-fx-background-color: linear-gradient(to bottom right, #2563eb, #3b82f6);"
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20;"
                + "-fx-min-width: 40; -fx-min-height: 40; -fx-alignment: center;");

        Label name = new Label(r.fullName());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label meta = new Label("@" + r.username() + "   •   " + r.role() + "   •   " + ago(r.minutesAgo()));
        meta.setStyle("-fx-text-fill: #8a8a90; -fx-font-size: 11px;");
        VBox info = new VBox(2, name, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnGenerate = new Button("🔑  Generate PIN");
        btnGenerate.getStyleClass().add("form-save");
        btnGenerate.setOnAction(e -> {
            new ResetPinDialog(pane.getScene().getWindow(), r.userId()).show();
            reload(); // the request disappears once a PIN is generated
        });

        Button btnDismiss = new Button("✖");
        btnDismiss.getStyleClass().addAll("action-button", "delete-button");
        btnDismiss.setOnAction(e -> {
            try {
                resetModel.dismissRequest(r.userId());
            } catch (Exception ex) {
                lblSummary.setText(ex.getMessage());
            }
            reload();
        });

        HBox row = new HBox(10, avatar, info, spacer, btnGenerate, btnDismiss);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setStyle("-fx-background-color: #19191c; -fx-background-radius: 10;");
        return row;
    }

    /* ======================= helpers ======================= */
    private String ago(int minutes) {
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        return (minutes / 60) + " h ago";
    }

    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }

    private void styleDialog(DialogPane p) {
        URL css = getClass().getResource("/styles/bike.css");
        if (css != null) {
            p.getStylesheets().add(css.toExternalForm());
        }
        p.getStyleClass().add("dark-dialog");
    }
}