package ru.mikhailm.cloud.storage.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.mikhailm.cloud.storage.common.FileInfo;
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

    private State currentState = State.IDLE;
    private int nextLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private Path source;
    private String userDirectory;
    private String currentDirectory;
    boolean littleData;
    ByteBuf buf;

    public ProtoHandler(String user) {
        userDirectory = Paths.get(".", "server_storage", user).toString();

        //Проверка каталога пользователя
        if (!Files.exists(Paths.get(".", "server_storage", user))) {
            try {
                Files.createDirectories(Paths.get(".", "server_storage", user));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!littleData) {
            buf = (ByteBuf) msg;
        } else if ((buf.capacity() - buf.writerIndex()) >= ((ByteBuf) msg).writerIndex()) {
            buf.capacity(((ByteBuf) msg).writerIndex() + buf.capacity());
            buf.writeBytes((ByteBuf) msg);
        } else {
            buf.writeBytes((ByteBuf) msg);
        }

        littleData = false;

        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byte commandCode = buf.readByte();
                System.out.println(commandCode);
                switch (commandCode) {
                    case FILE:       //Получение файла
                        currentState = State.FILE;
                        receivedFileLength = 0L;
                        System.out.println("STATE: Start file receiving");
                        break;
                    case REQUEST_FILE_LIST:       //Запрос на отправку списка файлов
                        currentState = State.REQUEST_FILE_LIST;
                        System.out.println("STATE: File list request");
                        break;
                    case REQUEST_FILE:       //Запрос на отправку файла
                        currentState = State.REQUEST_FILE_DOWNLOAD;
                        System.out.println("STATE: File download request");
                        break;
                    case REQUEST_FILE_RENAME:
                        currentState = State.REQUEST_FILE_RENAME;
                        System.out.println("STATE: File rename request");
                        break;
                    case REQUEST_FILE_DELETE:
                        currentState = State.REQUEST_FILE_DELETE;
                        System.out.println("STATE: File delete request");
                        break;
                    case CREATE_DIRECTORY:
                        currentState = State.CREATE_DIRECTORY;
                        System.out.println("STATE: Create directory request");
                    default:
                        System.out.println("ERROR: Invalid first byte - " + commandCode);
                }
            }

            if (currentState == State.FILE) {
                file(ctx, buf);
            }

            //Получаем длину имени файла
            if (currentState == State.REQUEST_FILE_DOWNLOAD) {
                fileDownload(ctx, buf);
            }

            if (currentState == State.REQUEST_FILE_RENAME) {
                fileRename(ctx, buf);
            }

            if (currentState == State.REQUEST_FILE_DELETE) {
                fileDelete(ctx, buf);
            }

            if (currentState == State.REQUEST_FILE_LIST) {
                sendFileList(ctx);
            }

            if (currentState == State.CREATE_DIRECTORY) {
                createDirectory(ctx, buf);
            }
        }

        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void createDirectory(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                Files.createDirectory(Paths.get(currentDirectory, new String(fileName, StandardCharsets.UTF_8)));
                currentState.setNumberOperation(0);
                currentState = State.IDLE;
                sendFileList(ctx);
            } else {
                littleData = true;
            }
        }
    }

    private void fileDelete(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                Files.deleteIfExists(Paths.get(currentDirectory, new String(fileName, StandardCharsets.UTF_8)));
                currentState.setNumberOperation(0);
                currentState = State.IDLE;
                sendFileList(ctx);
            } else {
                littleData = true;
            }
        }
    }

    private void fileRename(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {

        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                source = Paths.get(currentDirectory, new String(fileName, StandardCharsets.UTF_8));
                currentState.setNumberOperation(2);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 2) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(3);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 3) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                String newName = new String(fileName, StandardCharsets.UTF_8);
                Files.move(source, source.resolveSibling(newName));
                currentState.setNumberOperation(0);
                currentState = State.IDLE;
                sendFileList(ctx);
            } else {
                littleData = true;
            }
        }
    }

    private void fileDownload(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }
        //Получаем имя файла и отправляем его клиенту
        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                ProtoFileSender.sendFile(Paths.get(currentDirectory, new String(fileName)), ctx.channel(), channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        channelFuture.cause().printStackTrace();
                    }
                    if (channelFuture.isSuccess()) {
                        System.out.println("Файл успешно передан");
                    }
                });
                currentState.setNumberOperation(0);
                currentState = State.IDLE;
            } else {
                littleData = true;
            }
        }
    }

    private void file(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                System.out.println("STATE: Get filename length");
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                System.out.println("STATE: Filename received - " + new String(fileName, StandardCharsets.UTF_8));
                out = new BufferedOutputStream(new FileOutputStream(currentDirectory + "\\" + new String(fileName)));
                currentState.setNumberOperation(2);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 2) {
            if (buf.readableBytes() >= 8) {
                fileLength = buf.readLong();
                System.out.println("STATE: File length received - " + fileLength);
                if (fileLength == 0) {
                    currentState.setNumberOperation(0);
                    currentState = State.IDLE;
                    System.out.println("File received");
                    out.close();
                    sendFileList(ctx);
                } else {
                    currentState.setNumberOperation(3);
                }
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 3) {
            while (buf.readableBytes() > 0) {
                byte b = buf.readByte();
                out.write(b);
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState.setNumberOperation(0);
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
    }

    //Метод для отправки списка файлов на клиент
    private void sendFileList(ChannelHandlerContext ctx) throws IOException {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] directory = new byte[nextLength];
                buf.readBytes(directory);
                currentDirectory = Paths.get(userDirectory, new String(directory, StandardCharsets.UTF_8)).toString();
                currentState.setNumberOperation(0);
                currentState = State.IDLE;
            } else {
                littleData = true;
            }
        }

        System.out.println(currentDirectory);
        List<FileInfo> files = Files.list(Paths.get(currentDirectory))
                .map(FileInfo::new)
                .collect(Collectors.toList());

        //Отправляем сигнальный байт с кодом команды
        ByteBuf outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
        outBuf.writeByte(FILE_LIST);
        ctx.write(outBuf);

        //Отправляем количество файлов в списке
        outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
        outBuf.writeInt(files.size());
        System.out.println("FILE_LIST: количество файлов " + files.size());
        ctx.writeAndFlush(outBuf);

        if (files.size() == 0) return;

        //Проходимся по всем файлам и отправляем длину имени, имя и размер файла
        for (FileInfo file : files) {
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(1);
            if (file.getType() == FileInfo.FileType.FILE) {
                outBuf.writeByte(0);
                System.out.println("FILE_LIST: файл");
            } else {
                outBuf.writeByte(1);
                System.out.println("FILE_LIST: директория");
            }
            ctx.write(outBuf);

            byte[] filenameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
            outBuf = ByteBufAllocator.DEFAULT.directBuffer(4);
            outBuf.writeInt(filenameBytes.length);
            System.out.println("FILE_LIST: длина имени файла " + filenameBytes.length);
            ctx.write(outBuf);

            outBuf = ByteBufAllocator.DEFAULT.directBuffer(filenameBytes.length);
            outBuf.writeBytes(filenameBytes);
            System.out.println("FILE_LIST: имя файла " + new String(filenameBytes));
            ctx.write(outBuf);

            outBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
            outBuf.writeLong(file.getSize());
            System.out.println("FILE_LIST: размер файла " + file.getSize());
            ctx.writeAndFlush(outBuf);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
