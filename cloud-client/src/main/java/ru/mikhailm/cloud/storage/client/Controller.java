package ru.mikhailm.cloud.storage.client;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoFileSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class Controller {
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

    //Обработка нажатия кнопки "Отправить"
    public void send(ActionEvent actionEvent) throws IOException {
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

    public void initialize() {
        new Thread(() -> {
            Network.getInstance().setController(this);
            Network.getInstance().start();
        }).start();

        //Настраиваем TableView
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
    public void download(ActionEvent actionEvent) {
        FileInfo fileInfo = filesTable.getSelectionModel().getSelectedItem();
        if (fileInfo != null) {
            ProtoFileSender.sendRequestFileDownload(fileInfo.getName(), Network.getInstance().getCurrentChannel());
        }
    }

    public void update(ActionEvent actionEvent) {
        ProtoFileSender.updateFileList(Network.getInstance().getCurrentChannel());
    }
}
