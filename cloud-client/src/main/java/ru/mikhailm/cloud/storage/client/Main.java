package ru.mikhailm.cloud.storage.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        showAuthorizationWindow();
    }

    public void showAuthorizationWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/authorization.fxml"));
        Parent root = loader.load();
        Stage authStage = new Stage();
        authStage.setTitle("Cloud storage authorization");
        authStage.setScene(new Scene(root, 300, 300));
        AuthorizationController controller = loader.getController();
        controller.setMain(this);
        controller.setAuthStage(authStage);
        authStage.setOnCloseRequest(event -> controller.exitAction());
        authStage.show();
    }

    public void showClient() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/client.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Cloud storage client");
        primaryStage.setScene(new Scene(root, 600, 600));
        ClientController clientController = loader.getController();
        clientController.setMain(this);
        primaryStage.setOnCloseRequest(event -> clientController.exitAction());
        primaryStage.show();
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
