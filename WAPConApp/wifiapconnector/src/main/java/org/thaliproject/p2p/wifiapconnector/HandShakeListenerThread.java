// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.wifiapconnector;

/**
 * Created by juksilve on 12.3.2015.
 */

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;


public class HandShakeListenerThread extends Thread {

    private WifiBase.HandShakeListenCallBack callback;
    private final ServerSocket mSocket;
    boolean mStopped = false;

    public HandShakeListenerThread(WifiBase.HandShakeListenCallBack Callback, int port) {
        callback = Callback;
        ServerSocket tmp = null;

        try {
            tmp = new ServerSocket(port);
        } catch (IOException e) {
            printe_line("new ServerSocket failed: " + e.toString());
        }
        mSocket = tmp;
    }

    public void run() {
    //    while (!this.interrupted()) {
        if(callback != null) {
            printe_line("starting to listen");
            Socket socket = null;
            try {
                if (mSocket != null) {
                    socket = mSocket.accept();
                }
                if (socket != null) {
                    printe_line("we got incoming connection");
                    callback.GotConnection(socket.getInetAddress(),socket.getLocalAddress());
                    OutputStream stream = socket.getOutputStream();
                    String hello = "shakeback";
                    stream.write(hello.getBytes());
                    socket.close();
                } else if (!mStopped) {
                    callback.ListeningFailed("Socket is null");
                }

            } catch (Exception e) {
                if (!mStopped) {
                    //return failure
                    printe_line("accept socket failed: " + e.toString());
                    callback.ListeningFailed(e.toString());
                }
            }
        }
       // }
    }

    private void printe_line(String message){
        //Log.i("HandShake", "Listener Thread: " + message);
    }

    public void Stop() {
        printe_line("cancelled");
        mStopped = true;
        try {
            if(mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            printe_line("closing socket failed: " + e.toString());
        }
    }
}