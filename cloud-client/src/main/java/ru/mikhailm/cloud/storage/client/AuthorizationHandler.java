package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
import ru.mikhailm.cloud.storage.common.CommandCode;

public class AuthorizationHandler extends ChannelInboundHandlerAdapter {
    private final ChannelInboundListener listener;

    public AuthorizationHandler(ChannelInboundListener listener) {
        this.listener = listener;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            byte commandCode = buf.readByte();

            //При успешной авторизации добавляем в pipeline ProtoHandler и удаляем AuthorizationHandler
            if (commandCode == CommandCode.AUTHORIZATION_SUCCESS || commandCode == CommandCode.REGISTRATION_SUCCESS) {
                Platform.runLater(listener::authSuccess);
                if (commandCode == CommandCode.REGISTRATION_SUCCESS) {
                    Platform.runLater(() -> listener.showDialog("Регистрация прошла успешно"));
                } else {
                    Platform.runLater(() -> listener.showDialog("Вход выполнен"));
                }
                ctx.pipeline().addLast(new ProtoHandler())
                        .remove(this);
            }

            //При неудачной авторизации сообщаем об этом пользователю
            if (commandCode == CommandCode.AUTHORIZATION_FAIL) {
                Platform.runLater(listener::authFail);
            }
            if (commandCode == CommandCode.REGISTRATION_FAIL) {
                Platform.runLater(listener::registrationFail);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
