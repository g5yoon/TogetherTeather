package com.n3v.junwidi.Listener;

public interface MyClientTaskListener {

    void onEndWait();
    void progressUpdate(int progress);
    void onHandshaked();
    void setFile(String fileName, long fileSize);
    void onReceiveFinished();
    void onReceiveCancelled();
    void onVideoAlreadyExist();
    void onReceiveShowGuideline();
    void onPreparePlayReceived();
    void onEndExcute();

    void onReceiveConPlay();
    void onReceiveConPause();
    void onReceiveConResume();
    void onReceiveConStop();
    void onReceiveConSeek(int time);
}
