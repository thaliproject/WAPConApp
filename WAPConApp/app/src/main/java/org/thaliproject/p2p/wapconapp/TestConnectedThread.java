// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.wapconapp;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by juksilve on 11.3.2015.
 */

public class TestConnectedThread extends Thread {

    public static final int MESSAGE_READ         = 0x11;
    public static final int MESSAGE_WRITE        = 0x22;
    public static final int SOCKET_DISCONNEDTED  = 0x33;

    private final Socket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    boolean mRunning = true;
    final String TAG  = "TestConnectedThread";

    public TestConnectedThread(Socket socket, Handler handler) {
        Log.d(TAG, "Creating TestConnectedThread");
        mHandler = handler;
        mmSocket = socket;

        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        // Get the Socket input and output streams
        try {
            if(mmSocket != null) {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            }
        } catch (IOException e) {
            Log.e(TAG, "Creating temp sockets failed: ", e);
        }
        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }
    public void run() {
        Log.i(TAG, "BTConnectedThread started");
        byte[] buffer = new byte[1048576];
        int bytes;

        while (mRunning) {
            try {
                bytes = mmInStream.read(buffer);
                if(bytes > 0) {
                    //  Log.d(TAG, "TestConnectedThread read data: " + bytes + " bytes");
                     mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                 }else{
                    Stop();
                    mHandler.obtainMessage(SOCKET_DISCONNEDTED, -1,-1 ,"Disconnected").sendToTarget();
                }
            } catch (IOException e) {
               // Log.e(TAG, "ConnectedThread disconnected: ", e);
                Stop();
                mHandler.obtainMessage(SOCKET_DISCONNEDTED, -1,-1 ,e ).sendToTarget();
                break;
            }
        }

        Log.i(TAG, "BTConnectedThread exit now !");
    }
    /**
     * Write to the connected OutStream.
     * @param buffer The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            if(mmOutStream != null) {
                mmOutStream.write(buffer);
                mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer).sendToTarget();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  write failed: ", e);
        }
    }

    public void Stop() {
        mRunning = false;
        try {
            if(mmInStream != null) {
                mmInStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  mmInStream close failed: ", e);
        }
        try {
            if(mmOutStream != null) {
                mmOutStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  mmOutStream close failed: ", e);
        }

        try {

            if(mmSocket != null) {
                mmSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "ConnectedThread  socket close failed: ", e);
        }
    }
}
