package ru.mikhailm.cloud.storage.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class StorageClient extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception{
        this.primaryStage = primaryStage;
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        this.primaryStage.setTitle("Java Cloud Storage");
        this.primaryStage.setScene(new Scene(root, 1280, 600));
        this.primaryStage.setMinWidth(800);
        this.primaryStage.setMinHeight(400);
        this.primaryStage.setOnCloseRequest(event -> controller.exitAction());
        this.primaryStage.show();
        controller.loginDialog("");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
