package ru.mikhailm.cloud.storage.client;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoFileSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class ClientController {
    private Main main;

    @FXML
    private TableView<FileInfo> filesTable;

    @FXML
    private TableColumn<FileInfo, String> fileNameColumn;

    @FXML
    private TableColumn<FileInfo, Long> fileSizeColumn;

    public void setMain(Main main) {
        this.main = main;
    }

//    public Main getMain() {
//        return main;
//    }

    //Обработка нажатия кнопки "Отправить"
    public void send() throws IOException {
        //Открываем диалоговое окно выбора файла
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(main.getPrimaryStage());
        if (file != null) {
            //Отправляем выбранный файл
            ProtoFileSender.sendFile(Paths.get(file.getAbsolutePath()), Network.getInstance().getCurrentChannel(), future -> {
                if (!future.isSuccess()) {
                    future.cause().printStackTrace();
                }
                if (future.isSuccess()) {
                    System.out.println("Файл успешно передан");
                }
            });
        }
    }

    @FXML
    private void initialize() {
        Network.getInstance().setClientController(this);
        ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel());

        //Настраиваем TableView
        filesTable.setPlaceholder(new Label("Ваша папка пуста"));
        fileNameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getName()));
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
    }

    //Обновление данных в TableView
    public void updateList(List<FileInfo> files) {
        filesTable.getItems().clear();
        filesTable.getItems().addAll(files);
    }

    public void exitAction() {
        Network.getInstance().stop();
        Platform.exit();
    }

    //Обработка нажатия кнопки "Скачать"
    public void download() {
        FileInfo fileInfo = filesTable.getSelectionModel().getSelectedItem();
        if (fileInfo != null) {
            ProtoFileSender.sendRequestFileDownload(fileInfo.getName(), Network.getInstance().getCurrentChannel());
        }
    }

    public void update() {
        ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel());
    }

    public void rename() {
        FileInfo fileInfo = filesTable.getSelectionModel().getSelectedItem();
        if (fileInfo != null) {
            String oldFileName = fileInfo.getName();

            Dialog<String> dialog = new Dialog<>();
            dialog.initStyle(StageStyle.UTILITY);

// Set the button types.
            ButtonType okButtonType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

// Create the username and password labels and fields.
            BorderPane borderPane = new BorderPane();
            TextField fileName = new TextField();
            fileName.setText(oldFileName);

            borderPane.setCenter(fileName);

// Enable/Disable login button depending on whether a username was entered.
            Node renameButton = dialog.getDialogPane().lookupButton(okButtonType);
            renameButton.setDisable(true);

// Do some validation (using the Java 8 lambda syntax).
            fileName.textProperty().addListener((observable, oldValue, newValue) -> renameButton.setDisable(newValue.trim().isEmpty()));

            dialog.getDialogPane().setContent(borderPane);

// Request focus on the username field by default.
//            Platform.runLater(fileName::requestFocus);

// Convert the result to a username-password-pair when the login button is clicked.
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == okButtonType) {
                    return fileName.getText();
                }
                return null;
            });

            Optional<String> result = dialog.showAndWait();

            result.ifPresent(newFileName -> ProtoFileSender.renameFile(oldFileName, newFileName, Network.getInstance().getCurrentChannel()));
        }
    }

    public void delete() {
        FileInfo fileInfo = filesTable.getSelectionModel().getSelectedItem();
        if (fileInfo != null) {
            ProtoFileSender.deleteFile(fileInfo.getName(), Network.getInstance().getCurrentChannel());
        }
    }
}
