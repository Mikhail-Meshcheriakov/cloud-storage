package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import ru.mikhailm.cloud.storage.common.CommandCode;

import java.io.IOException;

public class AuthorizationHandler extends ChannelInboundHandlerAdapter {
    AuthorizationController authorizationController;

    public AuthorizationHandler(AuthorizationController authorizationController) {
        this.authorizationController = authorizationController;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            byte commandCode = buf.readByte();

            //При успешной авторизации добавляем в pipeline ProtoHandler и удаляем AuthorizationHandler
            if (commandCode == CommandCode.AUTHORIZATION_SUCCESS) {
                Platform.runLater(() -> {
                    try {
                        authorizationController.authSuccess();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                ctx.pipeline().addLast(new ProtoHandler())
                        .remove(this);
            }

            //При неудачной авторизации сообщаем об этом пользователю
            if (commandCode == CommandCode.AUTHORIZATION_FAIL) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("");
                    alert.setHeaderText(null);
                    alert.setContentText("Неверный логин или пароль");
                    alert.show();
                });
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
