package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import ru.mikhailm.cloud.storage.common.CommandCode;
import ru.mikhailm.cloud.storage.common.FileInfo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        ClientController clientController = Network.getInstance().getClientController();
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
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("");
                            alert.setHeaderText(null);
                            alert.setContentText("Файл успешно загружен");
                            alert.show();
                        });
                        break;
                    default:
                        System.out.println("ERROR: Invalid first byte - " + commandCode);
                }
            }

            if (currentState == State.FILE_LIST) {
                fileList(buf, clientController);
            }

            if (currentState == State.FILE) {
                file(buf);
            }
        }

        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void fileList(ByteBuf buf, ClientController clientController) {
        //Получаем количество файлов в списке
        if (State.FILE_LIST.getNumberOperation() == 0)
            if (buf.readableBytes() >= 4) {
                countFiles = buf.readInt();
                State.FILE_LIST.setNumberOperation(1);

                if (countFiles == 0) {
                    clientController.updateList(fileList);
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
                }
            }
            //Получаем имя файла
            if (State.FILE_LIST.getNumberOperation() == 2) {
                if (buf.readableBytes() >= nextLength) {
                    fileName = new byte[nextLength];
                    buf.readBytes(fileName);
                    State.FILE_LIST.setNumberOperation(3);
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

                    if (counter == countFiles) {
                        //Обновляем список файлов на клиенте
                        clientController.updateList(fileList);
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

    private void file(ByteBuf buf) throws IOException {
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
                System.out.println("STATE: Filename received - _" + new String(fileName, StandardCharsets.UTF_8));
                out = new BufferedOutputStream(new FileOutputStream("client_directory\\" + new String(fileName)));
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
                    out.close();
                } else {
                    currentState.setNumberOperation(3);
                }
            }
        }

        if (currentState.getNumberOperation() == 3) {
            while (buf.readableBytes() > 0) {
                byte b = buf.readByte();
                out.write(b);
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState = State.IDLE;
                    currentState.setNumberOperation(0);
                    System.out.println("File received");
                    out.close();
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
