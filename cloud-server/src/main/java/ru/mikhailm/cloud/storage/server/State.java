package ru.mikhailm.cloud.storage.server;

public enum State {
    IDLE,
    FILE,
    REQUEST_FILE_DOWNLOAD,
    LOGIN_LENGTH,
    LOGIN,
    PASSWORD_LENGTH,
    PASSWORD,
    REQUEST_FILE_RENAME,
    REQUEST_FILE_DELETE;

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
