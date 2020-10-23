package ru.mikhailm.cloud.storage.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.mikhailm.cloud.storage.common.ProtoFileSender;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static ru.mikhailm.cloud.storage.common.CommandCode.*;

public class ProtoHandler extends ChannelInboundHandlerAdapter {

    private final String user;
    private State currentState = State.IDLE;
    private int nextLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;

    public ProtoHandler(String user) {
        this.user = user;

        //Проверка каталога пользователя
        if (!Files.exists(Paths.get(user))) {
            try {
                Files.createDirectories(Paths.get(user));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byte commandCode = buf.readByte();
                System.out.println(commandCode);
                switch (commandCode) {
                    case FILE:       //Получение файла
                        currentState = State.NAME_LENGTH;
                        receivedFileLength = 0L;
                        System.out.println("STATE: Start file receiving");
                        break;
                    case REQUEST_FILE_LIST:       //Запрос на отправку списка файлов
                        System.out.println("STATE: File list request");
                        sendFileList(ctx);
                        break;
                    case REQUEST_FILE:       //Запрос на отправку файла
                        currentState = State.REQUEST_FILE_DOWNLOAD_1;
                        System.out.println("STATE: File download request");
                        break;
                    default:
                        System.out.println("ERROR: Invalid first byte - " + commandCode);
                }
            }

            if (currentState == State.NAME_LENGTH) {
                if (buf.readableBytes() >= 4) {
                    System.out.println("STATE: Get filename length");
                    nextLength = buf.readInt();
                    currentState = State.NAME;
                }
            }

            if (currentState == State.NAME) {
                if (buf.readableBytes() >= nextLength) {
                    byte[] fileName = new byte[nextLength];
                    buf.readBytes(fileName);
                    System.out.println("STATE: Filename received - _" + new String(fileName, StandardCharsets.UTF_8));
                    out = new BufferedOutputStream(new FileOutputStream(user + "\\" + new String(fileName)));
                    currentState = State.FILE_LENGTH;
                }
            }

            //TODO: реализовать обработку файлов с нулевым размером
            if (currentState == State.FILE_LENGTH) {
                if (buf.readableBytes() >= 8) {
                    fileLength = buf.readLong();
                    System.out.println("STATE: File length received - " + fileLength);
                    currentState = State.FILE;
                }
            }

            if (currentState == State.FILE) {
                while (buf.readableBytes() > 0) {
                    byte b = buf.readByte();
                    out.write(b);
                    receivedFileLength++;
                    if (fileLength == receivedFileLength) {
                        currentState = State.IDLE;
                        System.out.println("File received");
                        out.close();

                        //Отправляем клиенту команду об успешной загрузке файла
                        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
                        byteBuf.writeByte(FILE_SUCCESS);
                        ctx.writeAndFlush(byteBuf);

                        sendFileList(ctx);  //После завершения загрузки файла отправля обновленный список файлов на клиент
                        break;
                    }
                }
            }

            //Получаем длину имени файла
            if (currentState == State.REQUEST_FILE_DOWNLOAD_1) {
                if (buf.readableBytes() >= 4) {
                    nextLength = buf.readInt();
                    currentState = State.REQUEST_FILE_DOWNLOAD_2;
                }
            }
            //Получаем имя файла и отправляем его клиенту
            if (currentState == State.REQUEST_FILE_DOWNLOAD_2) {
                if (buf.readableBytes() >= nextLength) {
                    byte[] fileName = new byte[nextLength];
                    buf.readBytes(fileName);
                    ProtoFileSender.sendFile(Paths.get(user, new String(fileName)), ctx.channel(), channelFuture -> {
                        if (!channelFuture.isSuccess()) {
                            channelFuture.cause().printStackTrace();
                        }
                        if (channelFuture.isSuccess()) {
                            System.out.println("Файл успешно передан");
                        }
                    });
                    currentState = State.IDLE;
                }
            }

        }
        if (buf.readableBytes() == 0) {
            buf.release();
        }

    }

    //Метод для отправки списка файлов на клиент
    private void sendFileList(ChannelHandlerContext ctx) throws IOException {
        List<Path> files = Files.list(Paths.get(user))
                .filter(n -> !Files.isDirectory(n))
                .collect(Collectors.toList());

        //Отправляем сигнальный байт с кодом команды
        ByteBuf outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
        outBuf.writeByte(FILE_LIST);
        ctx.writeAndFlush(outBuf);

        //Отправляем количество файлов в списке
        outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
        outBuf.writeInt(files.size());
        ctx.writeAndFlush(outBuf);

        if (files.size() == 0) return;

        //Проходимся по всем файлам и отправляем длину имени, имя и размер файла
        for (Path file : files) {
            byte[] filenameBytes = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
            outBuf.writeInt(filenameBytes.length);
            ctx.writeAndFlush(outBuf);

            outBuf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
            outBuf.writeBytes(filenameBytes);
            ctx.writeAndFlush(outBuf);

            outBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
            outBuf.writeLong(Files.size(file));
            ctx.writeAndFlush(outBuf);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
