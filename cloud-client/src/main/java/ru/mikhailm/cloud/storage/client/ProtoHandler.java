package ru.mikhailm.cloud.storage.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.application.Platform;
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
    private ChannelInboundListener listener;
    private boolean littleData;
    private ByteBuf buf;
    private FileInfo.FileType fileType;

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
        listener = Network.getInstance().getListener();
        while (buf.readableBytes() > 0 && !littleData) {
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
                        Platform.runLater(() -> listener.showDialog("Файл успешно загружен"));
                        break;
                    case CommandCode.FILE_FAIL:
                        Platform.runLater(() -> listener.showDialog("Произошла ошибка при скачивании файла"));
                        break;
                    case CommandCode.CREATE_DIRECTORY_SUCCESS:
                        Platform.runLater(() -> listener.showDialog("Каталог успешно создан"));
                        break;
                    case CommandCode.CREATE_DIRECTORY_FAIL:
                        Platform.runLater(() -> listener.showDialog("Произошла ошибка при сщздании каталога"));
                        break;
                    case CommandCode.FILE_DELETE_SUCCESS:
                        Platform.runLater(() -> listener.showDialog("Файл успешно удален"));
                        break;
                    case CommandCode.FILE_DELETE_FAIL:
                        Platform.runLater(() -> listener.showDialog("Произошла ошибка при удалении файла"));
                        break;
                    case CommandCode.FILE_RENAME_SUCCESS:
                        Platform.runLater(() -> listener.showDialog("Файл успешно переименован"));
                        break;
                    case CommandCode.FILE_RENAME_FAIL:
                        Platform.runLater(() -> listener.showDialog("Произошла ошибка при переименовании файла"));
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
        if (State.FILE_LIST.getNumberOperation() == 0) {
            if (buf.readableBytes() >= 4) {
                countFiles = buf.readInt();
                System.out.println("FILE_LIST: количество файлов " + countFiles);
                State.FILE_LIST.setNumberOperation(1);

                if (countFiles == 0) {
                    listener.updateRemoteList(fileList);
                    currentState = State.IDLE;
                    State.FILE_LIST.setNumberOperation(0);
                }
            } else {
                littleData = true;
            }
        }
        //Получаем список файлов в цикле, пока счетчик counter не достигнет количества файлов в списке
        while (buf.readableBytes() > 0) {
            if (State.FILE_LIST.getNumberOperation() == 1) {
                if (buf.readableBytes() >= 1) {
                    byte type = buf.readByte();
                    if (type == 0) {
                        fileType = FileInfo.FileType.FILE;
                    } else {
                        fileType = FileInfo.FileType.DIRECTORY;
                    }
                    State.FILE_LIST.setNumberOperation(2);
                }
            }


            //Получаем длину имени файла
            if (State.FILE_LIST.getNumberOperation() == 2) {
                if (buf.readableBytes() >= 4) {
                    nextLength = buf.readInt();
                    System.out.println("FILE_LIST: длина имени файла " + nextLength);
                    State.FILE_LIST.setNumberOperation(3);
                } else {
                    littleData = true;
                    break;
                }
            }
            //Получаем имя файла
            if (State.FILE_LIST.getNumberOperation() == 3) {
                if (buf.readableBytes() >= nextLength) {
                    fileName = new byte[nextLength];
                    buf.readBytes(fileName);
                    System.out.println("FILE_LIST: имя файла " + new String(fileName));
                    State.FILE_LIST.setNumberOperation(4);
                } else {
                    littleData = true;
                    break;
                }
            }
            //Получаем размер файла
            if (State.FILE_LIST.getNumberOperation() == 4) {
                if (buf.readableBytes() >= 8) {
                    long fileSize = buf.readLong();
                    System.out.println("FILE_LIST: размер файла " + fileSize);
                    //Добавляем данные о файле в список
                    fileList.add(new FileInfo(new String(fileName), fileSize, fileType));
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
                } else {
                    littleData = true;
                    break;
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
            } else {
                littleData = true;
            }
        }

        if (currentState.getNumberOperation() == 1) {
            if (buf.readableBytes() >= nextLength) {
                byte[] fileName = new byte[nextLength];
                buf.readBytes(fileName);
                String localDirectory = ((MainController) listener).getCurrentLocalDirectory();
                System.out.println("STATE: Filename received - " + new String(fileName, StandardCharsets.UTF_8));
                System.out.println(localDirectory + "\\" + new String(fileName));
                out = new BufferedOutputStream(new FileOutputStream(localDirectory + "\\" + new String(fileName)));
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
                    listener.updateLocalList();
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
