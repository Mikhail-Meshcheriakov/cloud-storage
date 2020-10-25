package ru.mikhailm.cloud.storage.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProtoFileSender {
    public static void sendFile(Path path, Channel channel, ChannelFutureListener finishListener) throws IOException {
        FileRegion region = new DefaultFileRegion(path.toFile(), 0, Files.size(path));

        ByteBuf buf;
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.FILE);
        channel.write(buf);

        byte[] filenameBytes = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.write(buf);

        buf = ByteBufAllocator.DEFAULT.directBuffer(8);
        buf.writeLong(Files.size(path));
        channel.write(buf);

        ChannelFuture transferOperationFuture = channel.writeAndFlush(region);
        if (finishListener != null) {
            transferOperationFuture.addListener(finishListener);
        }
    }

    //Метод для отправки запроса на скачивание файла
    public static void sendRequestFileDownload(String fileName, Channel channel) {
        ByteBuf buf;

        //Отправка сигнального байта команды
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.REQUEST_FILE);
        channel.write(buf);

        //Отправка длины имени файла
        byte[] filenameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        //Отправка имени файла
        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.writeAndFlush(buf);
    }

    public static void updateFileList(Channel channel) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.REQUEST_FILE_LIST);
        channel.writeAndFlush(buf);
    }

    public static void renameFile(String oldFileName, String newFileName, Channel channel) {
        ByteBuf buf;

        //Отправка сигнального байта команды
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.REQUEST_FILE_RENAME);
        channel.write(buf);

        //Отправка длины имени файла
        byte[] filenameBytes = oldFileName.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        //Отправка имени файла
        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.write(buf);

        filenameBytes = newFileName.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        //Отправка имени файла
        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.writeAndFlush(buf);
    }

    public static void deleteFile(String fileName, Channel channel) {
        ByteBuf buf;

        //Отправка сигнального байта команды
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.REQUEST_FILE_DELETE);
        channel.write(buf);

        //Отправка длины имени файла
        byte[] filenameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        //Отправка имени файла
        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.writeAndFlush(buf);
    }
}