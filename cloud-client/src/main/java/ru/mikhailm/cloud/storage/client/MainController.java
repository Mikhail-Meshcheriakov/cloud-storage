package ru.mikhailm.cloud.storage.client;

import javafx.application.Platform;
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
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoSender;

import java.io.IOException;
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

        localPanelController.setMainController(this);
        remotePanelController.setMainController(this);

        localPanelController.setIsRemote(false);
        localPanelController.setLocation("Local");
        remotePanelController.setIsRemote(true);
        remotePanelController.setLocation("Remote");

        remotePanelController.getFilesTable().setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                if (remotePanelController.getFilesTable().getSelectionModel().getSelectedItem() != null && remotePanelController.getFilesTable().getSelectionModel().getSelectedItem().getType() == FileInfo.FileType.DIRECTORY) {
                    remotePanelController.getPathField().setText(remotePanelController.getCurrentPath() + "\\" + remotePanelController.getFilesTable().getSelectionModel().getSelectedItem().getName());
                    ProtoSender.updateFileList(Network.getInstance().getCurrentChannel(), remotePanelController.getCurrentPath());
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

        ButtonType loginButtonType = new ButtonType("Вход", ButtonBar.ButtonData.OK_DONE);
        ButtonType registrationButtonType = new ButtonType("Регистрация", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, registrationButtonType, cancelButtonType);

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

        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        textField.textProperty().addListener((observable, oldValue, newValue) -> loginButton.setDisable(newValue.trim().isEmpty()));

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(textField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(textField.getText(), passwordField.getText());
            }
            if (dialogButton == registrationButtonType) {
                return new Pair<>(null, null);
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        if (!result.isPresent()) {
            System.out.println("result = null login");
            exitAction();
        }

        result.ifPresent(usernamePassword -> {
            System.out.println("userRegistration login");
            if (usernamePassword.getKey() == null || usernamePassword.getValue() == null) {
                registrationDialog("");
                dialog.close();
            } else {
                ProtoSender.userRegistration(true, usernamePassword.getKey(), usernamePassword.getValue(), Network.getInstance().getCurrentChannel());
            }
        });
    }

    public void registrationDialog(String message) {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Регистрация");
        dialog.initStyle(StageStyle.UTILITY);

        ButtonType registrationButtonType = new ButtonType("Регистрация", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(registrationButtonType, cancelButtonType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        Label lblErrorNotification = new Label(message);
        lblErrorNotification.setTextFill(Color.RED);
        TextField loginField = new TextField();
        loginField.setPromptText("Логин");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Пароль ещё раз");

        grid.add(new Label("Логин:"), 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(new Label("Пароль:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Пароль еще раз:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);
        grid.add(lblErrorNotification, 1, 3);

        Node registrationButton = dialog.getDialogPane().lookupButton(registrationButtonType);
        registrationButton.setDisable(true);

        loginField.textProperty().addListener((observable, oldValue, newValue) -> registrationButton.setDisable(newValue.trim().isEmpty() || passwordField.getText().trim().isEmpty() || confirmPasswordField.getText().trim().isEmpty()));

        passwordField.textProperty().addListener((observable, oldValue, newValue) -> registrationButton.setDisable(newValue.trim().isEmpty() || loginField.getText().trim().isEmpty() || confirmPasswordField.getText().trim().isEmpty()));

        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> registrationButton.setDisable(newValue.trim().isEmpty() || loginField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty()));

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(loginField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == registrationButtonType) {
                return new Pair<>(loginField.getText(), passwordField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        if (!result.isPresent()) {
            System.out.println("result = null");
            exitAction();
        }

        result.ifPresent(usernamePassword -> {
            if (passwordField.getText().equals(confirmPasswordField.getText())) {
                System.out.println("userRegistration");
                ProtoSender.userRegistration(false, usernamePassword.getKey(), usernamePassword.getValue(), Network.getInstance().getCurrentChannel());
            } else {
                registrationDialog("Пароли не совпадают");
            }
        });
    }

    @Override
    public void showDialog(String message) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setContentText(message);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.show();
    }

    public void btnCopyFile() {
        if (localPanelController.getSelectedFilename() == null && remotePanelController.getSelectedFilename() == null) {
            showDialog("Не выбран ни один файл");
        }

        if (localPanelController.getSelectedFilename() != null && localPanelController.getFilesTable().getSelectionModel().getSelectedItem().getType() != FileInfo.FileType.DIRECTORY) {
            ProtoSender.sendFile(Paths.get(localPanelController.getCurrentPath(), localPanelController.getSelectedFilename()), Network.getInstance().getCurrentChannel(), future -> {
                if (!future.isSuccess()) {
                    future.cause().printStackTrace();
                }
                if (future.isSuccess()) {
                    System.out.println("Файл успешно передан");
                }
            });
        }

        if (remotePanelController.getSelectedFilename() != null && remotePanelController.getFilesTable().getSelectionModel().getSelectedItem().getType() != FileInfo.FileType.DIRECTORY) {
            currentLocalDirectory = localPanelController.getCurrentPath();
            ProtoSender.sendRequestFileDownload(remotePanelController.getSelectedFilename(), Network.getInstance().getCurrentChannel());
        }

    }

    public void btnRenameFile() {
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
            result.ifPresent(newFileName -> ProtoSender.renameFile(oldFileName, newFileName, Network.getInstance().getCurrentChannel()));
        }
    }

    public void btnDeleteFile() {
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
            ProtoSender.deleteFile(remotePanelController.getSelectedFilename(), Network.getInstance().getCurrentChannel());
        }
    }

    @Override
    public void authSuccess() {
        localPanelController.init();
        remotePanelController.init();
        ProtoSender.updateFileList(Network.getInstance().getCurrentChannel(), "");
    }

    @Override
    public void authFail() {
        loginDialog("Неверный логин или пароль");
    }

    @Override
    public void registrationFail() {
        registrationDialog("Пользователь с таким лгином уже существует");
    }

    @Override
    public void updateRemoteList(List<FileInfo> files) {
        remotePanelController.updateRemoteList(files);
        remotePanelController.getFilesTable().sort();
    }

    @Override
    public void updateLocalList() {
        localPanelController.updateLocalList(Paths.get(localPanelController.getCurrentPath()));
    }

    public void exitAction() {
        Network.getInstance().stop();
        Platform.exit();
    }

    public void updateAllList() {
        ProtoSender.updateFileList(Network.getInstance().getCurrentChannel(), remotePanelController.getCurrentPath());
        localPanelController.updateLocalList(Paths.get(localPanelController.getCurrentPath()));
    }

    public void createDirectory() {
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

        result.ifPresent(newFileName -> ProtoSender.createDirectory(newFileName, Network.getInstance().getCurrentChannel()));
    }
}
