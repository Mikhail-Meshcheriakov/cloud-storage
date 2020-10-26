package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
import ru.mikhailm.cloud.storage.common.CommandCode;
import ru.mikhailm.cloud.storage.common.FileInfo;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ProtoHandler extends ChannelInboundHandlerAdapter {
    public enum State {
        IDLE, FILE, FILE_LIST;
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

    private State currentState = State.IDLE;
    private int nextLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private List<FileInfo> fileList = new ArrayList<>();
    private int countFiles;
    private int counter;
    private byte[] fileName;
    ChannelInboundListener listener;

    private int debug1;
    private int debug2;
    private int debug3;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        listener = Network.getInstance().getListener();
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                byte commandCode = buf.readByte();
                System.out.println(commandCode);
                switch (commandCode) {
                    case CommandCode.FILE:   //Получение файла
                        currentState = State.FILE;
                        receivedFileLength = 0L;
                        System.out.println("STATE: Start file receiving");
                        break;
                    case CommandCode.FILE_LIST:     //Получение списка файлов с сервера
                        currentState = State.FILE_LIST;
                        System.out.println("STATE: File list request");
                        break;
                    case CommandCode.FILE_SUCCESS:
                        Platform.runLater(() -> {
                            listener.showDialog("Файл успешно загружен");
                        });
                        break;
                    default:
                        System.out.println("ERROR: Invalid first byte - " + commandCode);
                }
            }

            if (currentState == State.FILE_LIST) {
                fileList(buf);
            }

            if (currentState == State.FILE) {
                file(buf);
            }
        }

        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void fileList(ByteBuf buf) {
        //Получаем количество файлов в списке
        if (State.FILE_LIST.getNumberOperation() == 0)
            if (buf.readableBytes() >= 4) {
                countFiles = buf.readInt();
                System.out.println("FILE_LIST: количество файлов " + countFiles);
                State.FILE_LIST.setNumberOperation(1);

                if (countFiles == 0) {
                    listener.updateRemoteList(fileList);
                    currentState = State.IDLE;
                    State.FILE_LIST.setNumberOperation(0);
                }
            }
        //Получаем список файлов в цикле, пока счетчик counter не достигнет количества файлов в списке
        while (buf.readableBytes() > 0) {
            //Получаем длину имени файла
            if (State.FILE_LIST.getNumberOperation() == 1) {
                System.out.println("1!!!! " + buf.readableBytes());
                if (buf.readableBytes() >= 4) {
                    nextLength = buf.readInt();
                    System.out.println("FILE_LIST: длина имени файла " + nextLength);
                    State.FILE_LIST.setNumberOperation(2);
                }
            }
            //Получаем имя файла
            if (State.FILE_LIST.getNumberOperation() == 2) {

                System.out.println("2!!!! " + buf.readableBytes());
                if (buf.readableBytes() >= nextLength) {
                    fileName = new byte[nextLength];
                    buf.readBytes(fileName);
                    System.out.println("FILE_LIST: имя файла " + new String(fileName));
                    State.FILE_LIST.setNumberOperation(3);
                }
            }
            //Получаем размер файла
            if (State.FILE_LIST.getNumberOperation() == 3) {
                System.out.println("3!!!! " + buf.readableBytes());
                if (buf.readableBytes() >= 8) {
                    long fileSize = buf.readLong();
                    System.out.println("FILE_LIST: размер файла " + fileSize);
                    //Добавляем данные о файле в список
                    fileList.add(new FileInfo(new String(fileName), fileSize));
                    counter++;
                    State.FILE_LIST.setNumberOperation(1);

                    if (counter == countFiles) {
                        //Обновляем список файлов на клиенте
                        listener.updateRemoteList(fileList);
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

    private void file(ByteBuf buf) {
        if (currentState.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                System.out.println("STATE: Get filename length");
                nextLength = buf.readInt();
                currentState.setNumberOperation(1);
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                String localDirectory = ((MainController) listener).getCurrentLocalDirectory();
                System.out.println("STATE: Filename received - " + new String(fileName, StandardCharsets.UTF_8));
                try {
                    out = new BufferedOutputStream(new FileOutputStream(localDirectory + "\\" + new String(fileName)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                currentState.setNumberOperation(2);
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
                    System.out.println("out.close() - length == 0");
                    try {
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException("length = 0", e);
                    }
                    listener.updateLocalList();
                } else {
                    currentState.setNumberOperation(3);
                }
            }
        }

        if (currentState.getNumberOperation() == 3) {
            while (buf.readableBytes() > 0) {
                byte b = buf.readByte();
                try {
                    out.write(b);
                } catch (IOException e) {
                    throw new RuntimeException("out.write", e);
                }
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState = State.IDLE;
                    currentState.setNumberOperation(0);
                    System.out.println("File received");
                    System.out.println("out.close()");
                    try {
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException("close", e);
                    }
                    listener.updateLocalList();
                    break;
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
