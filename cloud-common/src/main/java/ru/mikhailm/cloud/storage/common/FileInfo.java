package ru.mikhailm.cloud.storage.common;

//Класс для хранения информации о файле
public class FileInfo {
    private String name;  //Имя файла
    private long size;    //Размер файла

    public FileInfo(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }
}
