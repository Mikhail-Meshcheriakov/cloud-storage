package ru.mikhailm.cloud.storage.client;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import ru.mikhailm.cloud.storage.common.FileInfo;

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

    public void setIsRemote(boolean isRemote) {
        this.isRemote = isRemote;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void init() {
        locationLabel.setText(location);

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

        filesTable.getColumns().addAll(filenameColumn, fileSizeColumn);
        filesTable.getSortOrder().add(filenameColumn);

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

        filesTable.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    if (filesTable.getSelectionModel().getSelectedItem() != null) {
                        Path path = Paths.get(pathField.getText()).resolve(filesTable.getSelectionModel().getSelectedItem().getName());
                        if (Files.isDirectory(path)) {
                            updateLocalList(path);
                        }
                    }
                }
            }
        });

        if (!isRemote) {
            updateLocalList(Paths.get("."));
        }
    }

    public void btnPathUpAction(ActionEvent actionEvent) {
        if (!isRemote) {
            Path upperPath = Paths.get(pathField.getText()).getParent();
            if (upperPath != null) {
                updateLocalList(upperPath);
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
            Alert alert = new Alert(Alert.AlertType.WARNING, "По какой-то причине не удалось обновить список файлов", ButtonType.OK);
            alert.showAndWait();
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
