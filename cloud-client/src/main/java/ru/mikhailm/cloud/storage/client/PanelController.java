package ru.mikhailm.cloud.storage.client;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoSender;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class PanelController {
    @FXML
    private Label locationLabel;

    @FXML
    private ComboBox<String> disksBox;

    @FXML
    private TextField pathField;

    @FXML
    private TableView<FileInfo> filesTable;

    private boolean isRemote;

    private String location;

    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public TableView<FileInfo> getFilesTable() {
        return filesTable;
    }

    public TextField getPathField() {
        return pathField;
    }

    public void setIsRemote(boolean isRemote) {
        this.isRemote = isRemote;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void init() {
        locationLabel.setText(location);
        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(24);

        TableColumn<FileInfo, String> filenameColumn = new TableColumn<>("Имя");
        filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getName()));
        filenameColumn.setPrefWidth(240);

        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        fileSizeColumn.setCellFactory(column -> new TableCell<FileInfo, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = String.format("%,d bytes", item);
                    if (item == -1L) {
                        text = "[DIR]";
                    }
                    setText(text);
                }
            }
        });
        fileSizeColumn.setPrefWidth(120);

        filesTable.getColumns().addAll(fileTypeColumn, filenameColumn, fileSizeColumn);
        filesTable.getSortOrder().add(fileTypeColumn);
        filesTable.setPlaceholder(new Label("Каталог пуст"));

        disksBox.getItems().clear();
        if (isRemote) {
            disksBox.getItems().add("0:\\");
            disksBox.setDisable(true);
        } else {
            for (Path p : FileSystems.getDefault().getRootDirectories()) {
                disksBox.getItems().add(p.toString());
            }
        }
        disksBox.getSelectionModel().select(0);

        if (!isRemote) {
            updateLocalList(Paths.get("."));
        }
    }

    public void btnPathUpAction() {
        if (!isRemote) {
            Path upperPath = Paths.get(pathField.getText()).getParent();
            if (upperPath != null) {
                updateLocalList(upperPath);
            }
        } else {

            if (!pathField.getText().equals("")) {
                String upDirectory = pathField.getText().substring(0, pathField.getText().lastIndexOf("\\"));
                ProtoSender.updateFileList(Network.getInstance().getCurrentChannel(), upDirectory);
                pathField.setText(upDirectory);
            }
        }
    }

    public void selectDiskAction(ActionEvent actionEvent) {
        if (!isRemote) {
            ComboBox<String> element = (ComboBox<String>) actionEvent.getSource();
            updateLocalList(Paths.get(element.getSelectionModel().getSelectedItem()));
        }
    }

    public void updateLocalList(Path path) {
        try {
            pathField.setText(path.normalize().toAbsolutePath().toString());
            filesTable.getItems().clear();
            filesTable.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            filesTable.sort();
        } catch (IOException e) {
            mainController.showDialog("Не удалось обновить список файлов");
        }
    }

    public void updateRemoteList(List<FileInfo> files) {
        filesTable.getItems().clear();
        filesTable.getItems().addAll(files);
    }

    public String getSelectedFilename() {
        if (!filesTable.isFocused()) {
            return null;
        }
        return filesTable.getSelectionModel().getSelectedItem().getName();
    }

    public String getCurrentPath() {
        return pathField.getText();
    }
}
