package ru.mikhailm.cloud.storage.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class StorageClient extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        primaryStage.setTitle("Java Cloud Storage");
        primaryStage.setScene(new Scene(root, 1280, 600));
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(400);
        primaryStage.setOnCloseRequest(event -> controller.exitAction());
        primaryStage.show();
        controller.loginDialog("");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
