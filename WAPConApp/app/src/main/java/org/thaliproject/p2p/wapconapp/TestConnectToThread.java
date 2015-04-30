// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.wapconapp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by juksilve on 12.3.2015.
 */
public class TestConnectToThread extends Thread {

    private TestConnectCallBack callback;
    private final Socket mSocket;
    private String mAddress;
    private int mPort;
    boolean mStopped = false;

    public TestConnectToThread(TestConnectCallBack Callback, String address, int port) {
        mAddress = address;
        mPort = port;
        callback = Callback;
        mSocket = new Socket();

    }
    public void run() {
        printe_line("Starting to connect");
        if(mSocket != null && callback != null) {
            try {
                mSocket.bind(null);
                mSocket.connect(new InetSocketAddress(mAddress,mPort), 5000);
                //return success
                callback.Connected(mSocket);
            } catch (IOException e) {
                printe_line("socket connect failed: " + e.toString());
                try {
                    if (mSocket != null){
                        mSocket.close();
                    }
                } catch (IOException ee) {
                    printe_line("closing socket 2 failed: " + ee.toString());
                }
                if(!mStopped) {
                    callback.ConnectionFailed(e.toString());
                }
            }
        }
    }

    private void printe_line(String message){
     //   Log.d("BTConnectToThread",  "BTConnectToThread: " + message);
    }

    public void Stop() {
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
