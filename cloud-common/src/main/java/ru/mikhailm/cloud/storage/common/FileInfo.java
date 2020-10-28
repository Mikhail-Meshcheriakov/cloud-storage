package ru.mikhailm.cloud.storage.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//Класс для хранения информации о файле
public class FileInfo {
    public enum FileType {
        FILE("F"), DIRECTORY("D");

        private String name;

        FileType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private FileType type;
    private String name;  //Имя файла
    private long size;    //Размер файла

    public FileInfo(String name, long size, FileType type) {
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public FileInfo(Path path) {
        try {
            name = path.getFileName().toString();
            size = Files.size(path);
            this.type = Files.isDirectory(path) ? FileType.DIRECTORY : FileType.FILE;
            if (this.type == FileType.DIRECTORY) {
                this.size = -1L;
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

    public FileType getType() {
        return type;
    }
}
