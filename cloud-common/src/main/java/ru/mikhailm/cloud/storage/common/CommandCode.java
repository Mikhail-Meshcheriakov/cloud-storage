package ru.mikhailm.cloud.storage.common;

public class CommandCode {
    public static final byte AUTHORIZATION = 1;
    public static final byte REQUEST_FILE = 2;
    public static final byte FILE = 3;
    public static final byte FILE_LIST = (byte) 4;
    public static final byte REQUEST_FILE_LIST = 5;
    public static final byte REQUEST_FILE_RENAME = 6;
    public static final byte REQUEST_FILE_DELETE = 7;
    public static final byte RESULT = 8;
    public static final byte AUTHORIZATION_SUCCESS = 9;
    public static final byte FILE_SUCCESS = 10;
    public static final byte AUTHORIZATION_FAIL = 11;
}
