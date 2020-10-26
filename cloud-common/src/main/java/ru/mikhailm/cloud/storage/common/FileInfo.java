package ru.mikhailm.cloud.storage.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//Класс для хранения информации о файле
public class FileInfo {
    private String name;  //Имя файла
    private long size;    //Размер файла

    public FileInfo(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public FileInfo(Path path) {
        try {
            name = path.getFileName().toString();
            size = Files.size(path);
            if (Files.isDirectory(path)) {
                size = -1L;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }
}
