// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.wifiapconnector;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by juksilve on 12.3.2015.
 */
public class HandShakerThread extends Thread {

    private WifiBase.HandShakerCallBack callback;
    private final Socket mSocket;
    private String mAddress;
    private int mPort;
    boolean mStopped = false;
    private int triedSoFarTimes = 0;

    public HandShakerThread(WifiBase.HandShakerCallBack Callback, String address, int port, int trialnum) {
        mAddress = address;
        mPort = port;
        callback = Callback;
        mSocket = new Socket();
        triedSoFarTimes = trialnum;
    }
    public void run() {
        printe_line("Starting to connect");
        if(mSocket != null && callback != null) {
            try {
                mSocket.bind(null);
                mSocket.connect(new InetSocketAddress(mAddress,mPort), 5000);
                //return success
                callback.Connected(mSocket.getInetAddress(),mSocket.getLocalAddress());
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
                    callback.ConnectionFailed(e.toString(),triedSoFarTimes);
                }
            }
        }
    }

    private void printe_line(String message){
     //   Log.i("BTConnectToThread",  "BTConnectToThread: " + message);
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
