package ru.mikhailm.cloud.storage.common;

public class CommandCode {
    public static final byte AUTHORIZATION = 1;
    public static final byte REQUEST_FILE = 2;
    public static final byte FILE = 3;
    public static final byte FILE_LIST = 4;
    public static final byte REQUEST_FILE_LIST = 5;
    public static final byte REQUEST_FILE_RENAME = 6;
    public static final byte REQUEST_FILE_DELETE = 7;

    public static final byte AUTHORIZATION_SUCCESS = 8;
    public static final byte FILE_SUCCESS = 9;
    public static final byte AUTHORIZATION_FAIL = 10;
    public static final byte CREATE_DIRECTORY = 11;
    public static final byte REGISTRATION = 12;
    public static final byte REGISTRATION_SUCCESS = 13;
    public static final byte REGISTRATION_FAIL = 14;
    public static final byte CREATE_DIRECTORY_SUCCESS = 15;
    public static final byte CREATE_DIRECTORY_FAIL = 16;
    public static final byte FILE_DELETE_SUCCESS = 17;
    public static final byte FILE_DELETE_FAIL = 18;
    public static final byte FILE_RENAME_SUCCESS = 19;
    public static final byte FILE_RENAME_FAIL = 20;
    public static final byte FILE_FAIL = 21;
}
