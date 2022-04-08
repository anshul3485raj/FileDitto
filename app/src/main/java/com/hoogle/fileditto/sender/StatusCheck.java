package com.hoogle.fileditto.sender;

/**
 * Created by Sri on 18/12/16.
 */
interface StatusCheck {
    void onBytesTransferProgress(String ip, String fileName, long totalSize, String speed, long currentSize, int percentageUploaded);
    void onBytesTransferCompleted(String ip, String fileName);
    void onBytesTransferStarted(String ip, String fileName);
    void onBytesTransferCancelled(String ip, String error, String fileName);
}
