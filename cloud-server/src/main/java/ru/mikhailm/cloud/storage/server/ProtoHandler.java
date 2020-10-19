package ru.mikhailm.cloud.storage.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
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

public class ProtoHandler extends ChannelInboundHandlerAdapter {
    public enum State {
        IDLE, NAME_LENGTH, NAME, FILE_LENGTH, FILE, REQUEST_FILE_DOWNLOAD_1, REQUEST_FILE_DOWNLOAD_2
    }

    private State currentState = State.IDLE;
    private int nextLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byte readed = buf.readByte();
                System.out.println(readed);
                switch (readed) {
                    case 25:       //Получение файла
                        currentState = State.NAME_LENGTH;
                        receivedFileLength = 0L;
                        System.out.println("STATE: Start file receiving");
                        break;
                    case 26:       //Запрос на отправку списка файлов
                        System.out.println("STATE: File list request");
                        sendFileList(ctx);
                        break;
                    case 52:       //Запрос на отправку файла
                        currentState = State.REQUEST_FILE_DOWNLOAD_1;
                        System.out.println("STATE: File download request");
                        break;
                    default:
                        System.out.println("ERROR: Invalid first byte - " + readed);
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
                    System.out.println("STATE: Filename received - _" + new String(fileName, "UTF-8"));
                    out = new BufferedOutputStream(new FileOutputStream("server_directory\\" + new String(fileName)));
                    currentState = State.FILE_LENGTH;
                }
            }

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
                    ProtoFileSender.sendFile(Paths.get("server_directory", new String(fileName)), ctx.channel(), channelFuture -> {
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
        System.out.println("sendFileList()");
        List<Path> files = Files.list(Paths.get("server_directory"))
                .filter(n -> !Files.isDirectory(n))
                .collect(Collectors.toList());

        //Отправляем сигнальный байт с кодом команды
        ByteBuf outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
        outBuf.writeByte(62);
        ctx.writeAndFlush(outBuf);
        System.out.println(62);

        //Отправляем количество файлов в списке
        outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
        outBuf.writeInt(files.size());
        ctx.writeAndFlush(outBuf);
        System.out.println(files.size());

        if (files.size() == 0) return;

        //Проходимся по всем файлам и отправляем длину имени, имя и размер файла
        for (Path file : files) {
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
            outBuf.writeInt(file.getFileName().toString().length());
            ctx.writeAndFlush(outBuf);
            System.out.println(file.getFileName().toString().length());

            byte[] filenameBytes = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
            outBuf.writeBytes(filenameBytes);
            ctx.writeAndFlush(outBuf);
            System.out.println(file.getFileName().toString());

            outBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
            outBuf.writeLong(Files.size(file));
            ctx.writeAndFlush(outBuf);
            System.out.println(Files.size(file));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
