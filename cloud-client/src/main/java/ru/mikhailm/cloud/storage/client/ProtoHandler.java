package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.mikhailm.cloud.storage.common.FileInfo;
import ru.mikhailm.cloud.storage.common.ProtoFileSender;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProtoHandler extends ChannelInboundHandlerAdapter {
    public enum State {
        IDLE, NAME_LENGTH, NAME, FILE_LENGTH, FILE, FILE_LIST;
        private int numberOperation;

        State() {
            numberOperation = 0;
        }

        public int getNumberOperation() {
            return numberOperation;
        }

        public void setNumberOperation(int numberOperation) {
            this.numberOperation = numberOperation;
        }
    }

    private Controller controller;
    private State currentState = State.IDLE;
    private int nextLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private List<FileInfo> fileList = new ArrayList<>();
    private int countFiles;
    private int counter;
    private byte[] fileName;

    public ProtoHandler(Controller controller) {
        this.controller = controller;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ProtoFileSender.updateFileList(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byte readed = buf.readByte();
                System.out.println(readed);
                switch (readed) {
                    case 25:     //Получение файла
                        currentState = State.NAME_LENGTH;
                        receivedFileLength = 0L;
                        System.out.println("STATE: Start file receiving");
                        break;
                    case 62:     //Получение списка файлов с сервера
                        currentState = State.FILE_LIST;
                        System.out.println("STATE: File list request");
                        break;
                    default:
                        System.out.println("ERROR: Invalid first byte - " + readed);
                }
            }

            if (currentState == State.FILE_LIST) {
                //Получаем количество файлов в списке
                if (State.FILE_LIST.getNumberOperation() == 0)
                    if (buf.readableBytes() >= 4) {
                        countFiles = buf.readInt();
                        System.out.println("countFiles: " + countFiles);
                        State.FILE_LIST.setNumberOperation(1);

                        if (countFiles == 0) {
                            controller.updateList(fileList);
                            currentState = State.IDLE;
                            State.FILE_LIST.setNumberOperation(0);
                        }
                    }
                //Получаем список файлов в цикле, пока счетчик counter не достигнет количества файлов в списке
                while (buf.readableBytes() > 0) {
                    //Получаем длину имени файла
                    if (State.FILE_LIST.getNumberOperation() == 1) {
                        if (buf.readableBytes() >= 4) {
                            nextLength = buf.readInt();
                            State.FILE_LIST.setNumberOperation(2);
                            System.out.println("nextLength: " + nextLength);
                        }
                    }
                    //Получаем имя файла
                    if (State.FILE_LIST.getNumberOperation() == 2) {
                        if (buf.readableBytes() >= nextLength) {
                            fileName = new byte[nextLength];
                            buf.readBytes(fileName);
                            State.FILE_LIST.setNumberOperation(3);
                            System.out.println("fileName: " + new String(fileName));
                        }
                    }
                    //Получаем размер файла
                    if (State.FILE_LIST.getNumberOperation() == 3) {
                        if (buf.readableBytes() >= 8) {
                            long fileSize = buf.readLong();
                            //Добавляем данные о файле в список
                            fileList.add(new FileInfo(new String(fileName), fileSize));
                            counter++;
                            State.FILE_LIST.setNumberOperation(1);
                            System.out.println("fileSize: " + fileSize);

                            if (counter == countFiles) {
                                //Обновляем список файлов на клиенте
                                controller.updateList(fileList);
                                counter = 0;
                                fileList.clear();
                                State.FILE_LIST.setNumberOperation(0);
                                currentState = State.IDLE;
                                break;
                            }
                        }
                    }
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
                    out = new BufferedOutputStream(new FileOutputStream("client_directory\\" + new String(fileName)));
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
                        break;
                    }
                }
            }
        }
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
