package ru.mikhailm.cloud.storage.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.mikhailm.cloud.storage.common.CommandCode.FILE_LIST;

public class ProtoSender {
    public static void sendFile(Path path, Channel channel, ChannelFutureListener finishListener) {
        try {
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
        } catch (IOException e) {
            e.printStackTrace();
            ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
            buf.writeByte(CommandCode.FILE_FAIL);
            channel.write(buf);
        }
    }

    public static void sendFileList(String directory, Channel channel) {
        try (Stream<Path> files = Files.list(Paths.get(directory))) {
            List<FileInfo> fileList = files.map(FileInfo::new).collect(Collectors.toList());

            //Отправляем сигнальный байт с кодом команды
            ByteBuf outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
            outBuf.writeByte(FILE_LIST);
            channel.write(outBuf);

            //Отправляем количество файлов в списке
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
            outBuf.writeInt(fileList.size());
            System.out.println("FILE_LIST: количество файлов " + fileList.size());
            channel.writeAndFlush(outBuf);

            if (fileList.size() == 0) return;

            //Проходимся по всем файлам и отправляем длину имени, имя и размер файла
            for (FileInfo file : fileList) {
                outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
                if (file.getType() == FileInfo.FileType.FILE) {
                    outBuf.writeByte(0);
                    System.out.println("FILE_LIST: файл");
                } else {
                    outBuf.writeByte(1);
                    System.out.println("FILE_LIST: директория");
                }
                channel.write(outBuf);

                byte[] filenameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
                outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
                outBuf.writeInt(filenameBytes.length);
                System.out.println("FILE_LIST: длина имени файла " + filenameBytes.length);
                channel.write(outBuf);

                outBuf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
                outBuf.writeBytes(filenameBytes);
                System.out.println("FILE_LIST: имя файла " + new String(filenameBytes));
                channel.write(outBuf);

                outBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
                outBuf.writeLong(file.getSize());
                System.out.println("FILE_LIST: размер файла " + file.getSize());
                channel.writeAndFlush(outBuf);
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    public static void updateFileList(Channel channel, String directory) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.REQUEST_FILE_LIST);
        channel.write(buf);

        //Отправка длины имени каталога
        byte[] directoryName = directory.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(directoryName.length);
        channel.write(buf);

        //Отправка имени каталога
        buf = ByteBufAllocator.DEFAULT.directBuffer(directoryName.length);
        buf.writeBytes(directoryName);
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

    public static void createDirectory(String name, Channel channel) {
        ByteBuf buf;

        //Отправка сигнального байта команды
        buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(CommandCode.CREATE_DIRECTORY);
        channel.write(buf);

        //Отправка длины имени файла
        byte[] filenameBytes = name.getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4);
        buf.writeInt(filenameBytes.length);
        channel.write(buf);

        //Отправка имени файла
        buf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
        buf.writeBytes(filenameBytes);
        channel.writeAndFlush(buf);
    }

    public static void userRegistration(boolean registered, String login, String password, Channel channel) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        if (registered) {
            buf.writeByte(CommandCode.AUTHORIZATION);
        } else {
            buf.writeByte(CommandCode.REGISTRATION);
        }
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