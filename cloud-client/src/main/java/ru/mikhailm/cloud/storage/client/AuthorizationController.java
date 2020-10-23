package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.mikhailm.cloud.storage.common.CommandCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthorizationController {
    public TextField loginField;
    public PasswordField passwordField;
    Main main;
    Stage authStage;

    @FXML
    private void initialize() {
        new Thread(() -> {
            Network.getInstance().setAuthorizationController(this);
            Network.getInstance().start();
        }).start();
    }

    public void setAuthStage(Stage authStage) {
        this.authStage = authStage;
    }

    public void setMain(Main main) {
        this.main = main;
    }

    //При удачной авторизации закрываем текущее окно и запусаем окно клиента
    public void authSuccess() throws IOException {
        main.showClient();
        authStage.close();
    }

    public void exitAction() {
        Network.getInstance().stop();
        Platform.exit();
    }


    //Отправка логина и пароля на сервер
    public void login() {
        String login = loginField.getText();
        String password = passwordField.getText();
        if (login.equals("") || password.equals("")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Warning");
            alert.setHeaderText("Заполните все поля");
            alert.setContentText("Заполните все поля");
            alert.showAndWait();
            return;
        }
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
}
