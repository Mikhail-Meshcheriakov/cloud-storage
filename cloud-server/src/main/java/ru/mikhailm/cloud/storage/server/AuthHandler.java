package ru.mikhailm.cloud.storage.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.mikhailm.cloud.storage.common.CommandCode;

import java.nio.charset.StandardCharsets;


//Handler для обработки логина и пароля
public class AuthHandler extends ChannelInboundHandlerAdapter {
    private State currentState = State.IDLE;
    private int length;
    private String login;
    byte commandCode;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                commandCode = buf.readByte();
                if (commandCode == CommandCode.AUTHORIZATION || commandCode == CommandCode.REGISTRATION) {
                    currentState = State.LOGIN_LENGTH;
                } else {
                    System.out.println("ERROR: Invalid first byte - " + commandCode);
                }
            }

            if (currentState == State.LOGIN_LENGTH) {
                if (buf.readableBytes() >= 4) {
                    length = buf.readInt();
                    currentState = State.LOGIN;
                }
            }

            if (currentState == State.LOGIN) {
                if (buf.readableBytes() >= length) {
                    byte[] bytes = new byte[length];
                    buf.readBytes(bytes);
                    login = new String(bytes, StandardCharsets.UTF_8);
                    currentState = State.PASSWORD_LENGTH;
                }
            }

            if (currentState == State.PASSWORD_LENGTH) {
                if (buf.readableBytes() >= 4) {
                    length = buf.readInt();
                    currentState = State.PASSWORD;
                }
            }

            if (currentState == State.PASSWORD) {
                if (buf.readableBytes() >= length) {
                    byte[] bytes = new byte[length];
                    buf.readBytes(bytes);
                    String password = new String(bytes, StandardCharsets.UTF_8);

                    //Получение данных из БД
                    String user;
                    SqlClient.connect();
                    if (commandCode == CommandCode.AUTHORIZATION) {
                        System.out.println("login");
                        user = SqlClient.getUserLogin(login, password);
                    } else {
                        System.out.println("registration");
                        user = SqlClient.userRegistration(login, password);
                    }
                    SqlClient.disconnect();

                    ByteBuf byteBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
                    if (user != null) {
                        if (commandCode == CommandCode.AUTHORIZATION){
                            byteBuf.writeByte(CommandCode.AUTHORIZATION_SUCCESS);
                        } else {
                            byteBuf.writeByte(CommandCode.REGISTRATION_SUCCESS);
                        }
                        ctx.writeAndFlush(byteBuf);
                        ctx.pipeline().addLast(new ProtoHandler(user))
                                .remove(this);
                    } else {
                        if (commandCode == CommandCode.AUTHORIZATION){
                            byteBuf.writeByte(CommandCode.AUTHORIZATION_FAIL);
                        } else {
                            byteBuf.writeByte(CommandCode.REGISTRATION_FAIL);
                        }
                        ctx.writeAndFlush(byteBuf);
                    }
                    currentState = State.IDLE;
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}
