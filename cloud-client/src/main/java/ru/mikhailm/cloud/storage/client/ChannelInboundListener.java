package ru.mikhailm.cloud.storage.client;

import ru.mikhailm.cloud.storage.common.FileInfo;

import java.nio.file.Path;
import java.util.List;

public interface ChannelInboundListener {
    void updateRemoteList(List<FileInfo> files);

    void updateLocalList();

    void authSuccess();

    void authFail();

    void registrationFail();

    void showDialog(String message);
}
