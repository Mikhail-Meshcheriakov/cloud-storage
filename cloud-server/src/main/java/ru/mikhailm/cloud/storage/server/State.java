package ru.mikhailm.cloud.storage.server;

public enum State {
    IDLE,
    NAME_LENGTH,
    NAME,
    FILE_LENGTH,
    FILE,
    REQUEST_FILE_DOWNLOAD_1,
    REQUEST_FILE_DOWNLOAD_2,
    LOGIN_LENGTH,
    LOGIN,
    PASSWORD_LENGTH,
    PASSWORD
}
