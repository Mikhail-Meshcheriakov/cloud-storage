package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.util.Pair;
import ru.mikhailm.cloud.storage.common.CommandCode;
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoFileSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class MainController implements ChannelInboundListener {
    @FXML
    private VBox leftPanel, rightPanel;

    private PanelController localPanelController, remotePanelController;
    private String currentLocalDirectory;

    @FXML
    private void initialize() {
        new Thread(() -> {
            Network.getInstance().setListener(this);
            Network.getInstance().start();
        }).start();

        localPanelController = (PanelController) rightPanel.getProperties().get("ctrl");
        remotePanelController = (PanelController) leftPanel.getProperties().get("ctrl");

        localPanelController.setIsRemote(false);
        localPanelController.setLocation("Local");
        remotePanelController.setIsRemote(true);
        remotePanelController.setLocation("Remote");

        remotePanelController.getFilesTable().setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (remotePanelController.getFilesTable().getSelectionModel().getSelectedItem() != null && remotePanelController.getFilesTable().getSelectionModel().getSelectedItem().getType() == FileInfo.FileType.DIRECTORY) {
                    remotePanelController.getPathField().setText(remotePanelController.getCurrentPath() + "\\" + remotePanelController.getFilesTable().getSelectionModel().getSelectedItem().getName());
                    ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel(), remotePanelController.getCurrentPath());
                }
            }
        });

        localPanelController.getFilesTable().setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (localPanelController.getFilesTable().getSelectionModel().getSelectedItem() != null && localPanelController.getFilesTable().getSelectionModel().getSelectedItem().getType() == FileInfo.FileType.DIRECTORY) {
                    Path path = Paths.get(localPanelController.getCurrentPath()).resolve(localPanelController.getFilesTable().getSelectionModel().getSelectedItem().getName());
                    localPanelController.updateLocalList(path);
                }
            }
        });
    }

    public String getCurrentLocalDirectory() {
        return currentLocalDirectory;
    }

    public void loginDialog(String message) {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login Dialog");
        dialog.initStyle(StageStyle.UTILITY);

// Set the button types.
        ButtonType loginButtonType = new ButtonType("Вход", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, cancelButtonType);

// Create the username and password labels and fields.
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        Label lblErrorNotification = new Label(message);
        lblErrorNotification.setTextFill(Color.RED);
        TextField textField = new TextField();
        textField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(textField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(lblErrorNotification, 1, 2);

// Enable/Disable login button depending on whether a username was entered.
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

// Do some validation (using the Java 8 lambda syntax).
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });

        dialog.getDialogPane().setContent(grid);

// Request focus on the username field by default.
        Platform.runLater(() -> textField.requestFocus());

// Convert the result to a username-password-pair when the login button is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(textField.getText(), passwordField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        if (!result.isPresent()) {
            exitAction();
        }

        result.ifPresent(usernamePassword -> {
            login(usernamePassword.getKey(), usernamePassword.getValue());
        });
    }

    public void login(String login, String password) {
        Channel channel = Network.getInstance().getCurrentChannel();

        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.AUTHORIZATION);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        byte[] bytes = login.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(bytes.length);
        buf.writeBytes(bytes);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        bytes = password.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(bytes.length);
        buf.writeBytes(bytes);
        channel.writeAndFlush(buf);
    }

    @Override
    public void showDialog(String message) {
        Dialog dialog = new Dialog();
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setContentText(message);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.show();
    }

    public void btnCopyFile(ActionEvent actionEvent) throws IOException {
        if (localPanelController.getSelectedFilename() == null && remotePanelController.getSelectedFilename() == null) {
            showDialog("Не выбран ни один файл");
        }

        if (localPanelController.getSelectedFilename() != null) {
            ProtoFileSender.sendFile(Paths.get(localPanelController.getCurrentPath(), localPanelController.getSelectedFilename()), Network.getInstance().getCurrentChannel(), future -> {
                if (!future.isSuccess()) {
                    future.cause().printStackTrace();
                }
                if (future.isSuccess()) {
                    System.out.println("Файл успешно передан");
                }
            });
        }

        if (remotePanelController.getSelectedFilename() != null) {
            currentLocalDirectory = localPanelController.getCurrentPath();
            ProtoFileSender.sendRequestFileDownload(remotePanelController.getSelectedFilename(), Network.getInstance().getCurrentChannel());
        }

    }

    public void btnRenameFile(ActionEvent actionEvent) {
        if (localPanelController.getSelectedFilename() == null && remotePanelController.getSelectedFilename() == null) {
            showDialog("Не выбран ни один файл");
        }

        String oldFileName = (localPanelController.getSelectedFilename() != null) ? localPanelController.getSelectedFilename() : remotePanelController.getSelectedFilename();

        Dialog<String> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.UTILITY);

// Настраиваем кнопки.
        ButtonType okButtonType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

// Создаем поле для ввода нового имени файла.
        BorderPane borderPane = new BorderPane();
        TextField fileName = new TextField();
        fileName.setText(oldFileName);

        borderPane.setCenter(fileName);

// Делаем кнопку неактивной, если поле для ввода пустое .
        Node renameButton = dialog.getDialogPane().lookupButton(okButtonType);
        renameButton.setDisable(true);
        fileName.textProperty().addListener((observable, oldValue, newValue) -> renameButton.setDisable(newValue.trim().isEmpty()));
        dialog.getDialogPane().setContent(borderPane);

//            Platform.runLater(fileName::requestFocus);

// Обрабатываем результат диалогового окна.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return fileName.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (localPanelController.getSelectedFilename() != null) {
            result.ifPresent(newFileName -> {
                try {
                    Files.move(Paths.get(localPanelController.getCurrentPath(), oldFileName), Paths.get(localPanelController.getCurrentPath(), oldFileName).resolveSibling(newFileName));
                    updateLocalList();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        if (remotePanelController.getSelectedFilename() != null) {
            result.ifPresent(newFileName -> ProtoFileSender.renameFile(oldFileName, newFileName, Network.getInstance().getCurrentChannel()));
        }
    }

    public void btnDeleteFile(ActionEvent actionEvent) {
        if (localPanelController.getSelectedFilename() == null && remotePanelController.getSelectedFilename() == null) {
            showDialog("Не выбран ни один файл");
        }

        if (localPanelController.getSelectedFilename() != null) {
            try {
                Files.deleteIfExists(Paths.get(localPanelController.getCurrentPath(), localPanelController.getSelectedFilename()));
                updateLocalList();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (remotePanelController.getSelectedFilename() != null) {
            ProtoFileSender.deleteFile(remotePanelController.getSelectedFilename(), Network.getInstance().getCurrentChannel());
        }
    }

    @Override
    public void authSuccess() {
        localPanelController.init();
        remotePanelController.init();
        ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel(), "");
    }

    @Override
    public void authFail() {
        loginDialog("Неверный логин или пароль");
    }

    @Override
    public void updateRemoteList(List<FileInfo> files) {
        remotePanelController.updateRemoteList(files);
    }

    @Override
    public void updateLocalList() {
        localPanelController.updateLocalList(Paths.get(localPanelController.getCurrentPath()));
    }

    public void exitAction() {
        Network.getInstance().stop();
        Platform.exit();
    }

    public void updateAllList(ActionEvent actionEvent) {
        ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel(), remotePanelController.getCurrentPath());
        localPanelController.updateLocalList(Paths.get(localPanelController.getCurrentPath()));
    }

    public void createDirectory(ActionEvent actionEvent) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.UTILITY);

// Настраиваем кнопки.
        ButtonType okButtonType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

// Создаем поле для ввода нового имени файла.
        BorderPane borderPane = new BorderPane();
        TextField fileName = new TextField();

        borderPane.setCenter(fileName);

// Делаем кнопку неактивной, если поле для ввода пустое .
        Node renameButton = dialog.getDialogPane().lookupButton(okButtonType);
        renameButton.setDisable(true);
        fileName.textProperty().addListener((observable, oldValue, newValue) -> renameButton.setDisable(newValue.trim().isEmpty()));
        dialog.getDialogPane().setContent(borderPane);

        Platform.runLater(fileName::requestFocus);

// Обрабатываем результат диалогового окна.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return fileName.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(newFileName -> ProtoFileSender.createDirectory(newFileName, Network.getInstance().getCurrentChannel()));
    }
}
