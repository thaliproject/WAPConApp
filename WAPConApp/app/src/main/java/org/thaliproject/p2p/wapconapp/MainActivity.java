// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.wapconapp;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.thaliproject.p2p.wifiapconnector.WifiAPConnector;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class MainActivity extends ActionBarActivity implements WifiAPConnector.WDCallback,TestConnectCallBack {

    /*
        For End-to-End testing we can use timer here to Stop the process
        for example after 1 minute.
        The value is determined by mExitWithDelay
        */
    private int mExitWithDelay = 60; // 60 seconds test before exiting
    private boolean mExitWithDelayIsOn = true; // set false if we are not uisng this app for testing

    enum DataState{
        Idle,
        Listening,
        Connecting,
        Connected,
        SendingFirstMessage,
        SendingBigData,
        SendingAct,
        WaitingAct,
        WaitingBigdata
    }

    DataState currentState = DataState.Idle;

    final private int TestChatPortNumber = 8768;

    MainActivity that = this;
    MyTextSpeech mySpeech = null;

    int sendMessageCounter = 0;
    int gotMessageCounter = 0;
    int ComFailedCounter = 0;
    int ConAttemptCounter = 0;
    int ConnectionCounter = 0;
    int GotConnectionCounter = 0;
    int ConCancelCounter = 0;

    boolean iWasBigSender = false;
    boolean amIBigSender = false;
    boolean gotFirstMessage = false;
    boolean wroteFirstMessage = false;
    long wroteDataAmount = 0;
    long gotDataAmount = 0;

    TestDataFile mTestDataFile = null;

    private int mInterval = 1000; // 1 second by default, can be changed later
    private Handler timeHandler;
    private int timeCounter = 0;
    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {

            // call function to update timer
            timeCounter = timeCounter + 1;
            String timeShow = "T: " + timeCounter;

          /*  if(mExitWithDelayIsOn) {
                //exit timer for testing
                if (mExitWithDelay > 0) {
                    mExitWithDelay = mExitWithDelay - 1;
                    timeShow = timeShow + ", S: " + mExitWithDelay;
                } else {
                    if(mWDConnector != null) {
                        mWDConnector.Stop();
                        mWDConnector = null;
                    }
                    mExitWithDelayIsOn = false;
                    ShowSummary();
                }
            }*/


            ((TextView) findViewById(R.id.TimeBox)).setText(timeShow);
            timeHandler.postDelayed(mStatusChecker, mInterval);
        }
    };

    WifiAPConnector mWDConnector = null;
    TestListenerThread mTestListenerThread = null;
    TestConnectToThread mTestConnectToThread = null;
    TestConnectedThread mTestConnectedThread = null;

    List<String> ClientIPAddressList = new ArrayList<String>();

    PowerManager.WakeLock mWakeLock = null;

    long receivingTimeOutBaseTime = 0;
    CountDownTimer BigBufferReceivingTimeOut = new CountDownTimer(2000, 500) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {

            //if the receiving process has taken more than a minute, lets cancel it
            long receivingNow = (System.currentTimeMillis() - receivingTimeOutBaseTime);
            if(receivingNow > 60000) {
                ConCancelCounter = ConCancelCounter + 1;
                ((TextView) findViewById(R.id.cancelCount)).setText("" + ConCancelCounter);

                print_line("CHAT", "WE got timeout on receiving data, lets Disconnect.");
                stopConnector();
                StartConnector();
            }else{
                BigBufferReceivingTimeOut.start();
            }
        }
    };

    CountDownTimer DisconnectGroupOwnerTimeOut = new CountDownTimer(30000, 4000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            // no clients queuing up, thus lets reset the group now.
            stopConnector();
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                //Lets give others chance on creating new group before we come back online
                public void run() {
                    StartConnector();
                }
            }, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mySpeech = new MyTextSpeech(this);
        mTestDataFile = new TestDataFile(this);
        mTestDataFile.StartNewFile();

        Button btButton = (Button) findViewById(R.id.appToggle);
        btButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mExitWithDelayIsOn = false;
                print_line("Debug","Exit with delay is set OFF");
                if(mWDConnector != null){
                    stopConnector();
                    ShowSummary();
                }else{
                    StartConnector();
                }
            }
        });

        timeHandler = new Handler();
        mStatusChecker.run();

        //for demo & testing to keep lights on
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        this.mWakeLock.acquire();

        // would need to make sure here that BT & Wifi both are on !!
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if(!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }

        StartConnector();
    }

    public void StartConnector() {
        //lets be ready for incoming test communications
        print_line("Whatsup","starting listener now, and connector");
        startListenerThread();
        mWDConnector = new WifiAPConnector(that, that);
        mWDConnector.Start();
    }

    public void stopConnector() {
        stopConnectedThread();
        stopConnectToThread();
        stopListenerThread();

        if(mWDConnector != null) {
            mWDConnector.Stop();
            mWDConnector = null;
        }
    }
    public void ShowSummary(){

        if(mTestDataFile != null){
            Intent intent = new Intent(getBaseContext(), DebugSummaryActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        this.mWakeLock.release();
        DisconnectGroupOwnerTimeOut.cancel();
        BigBufferReceivingTimeOut.cancel();

        timeHandler.removeCallbacks(mStatusChecker);

        stopConnector();

        if(mySpeech != null){
            mySpeech.stop();
            mySpeech = null;
        }

        mTestDataFile.CloseFile();
        mTestDataFile = null;
    }

    private void startListenerThread() {
        stopListenerThread();
        DataStateChanged(DataState.Listening);
        mTestListenerThread = new TestListenerThread(this,TestChatPortNumber);
        mTestListenerThread.start();
    }

    private void stopListenerThread() {
        DataStateChanged(DataState.Idle);
        if(mTestListenerThread != null){
            mTestListenerThread.Stop();
            mTestListenerThread = null;
        }
    }

    private void stopConnectToThread() {
        if(mTestConnectToThread != null){
            mTestConnectToThread.Stop();
            mTestConnectToThread = null;
        }
    }


    private void stopConnectedThread() {
        if(mTestConnectedThread != null){
            mTestConnectedThread.Stop();
            mTestConnectedThread = null;
        }
    }


    @Override
    public void Connected(String address, Boolean isGroupOwner) {

        if(isGroupOwner){
            ((TextView) findViewById(R.id.remoteHost)).setText("From : " + address);
            ClientIPAddressList.add(address);

            print_line("Connectec","Connected From remote host: " + address +  ", CTread : " + mTestConnectedThread + ", CtoTread: " + mTestConnectToThread);

            if(mTestConnectedThread == null
            && mTestConnectToThread == null){
                goToNextClientWaiting();
            }
        }else {
            print_line("Connectec","Connected to remote host: " + address);
            ((TextView) findViewById(R.id.remoteHost)).setText("To : " + address);
        }
    }

    @Override
    public void GroupInfoChanged(WifiP2pGroup group) {
        // could be used for determining whether we got some clients disconnected, or whether we migth be getting incoming connection soon
    }

    @Override
    public void ConnectionStateChanged(WifiAPConnector.ConnectionState newState) {
        ((TextView) findViewById(R.id.connstatusBox)).setText("State : " + newState);
        switch(newState){
            case Idle:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xff444444); //dark Gray
                break;
            case NotInitialized:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xffcccccc); //light Gray
                break;
            case WaitingStateChange:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xffEE82EE); // pink
                break;
            case FindingPeers:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xff00ffff); // Cyan
                break;
            case FindingServices:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xffffff00); // yellow
                if(mTestDataFile != null) {
                    mTestDataFile.SetTimeNow(TestDataFile.TimeForState.FoundPeers);
                }
                break;
            case Connecting: {
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xffff0000); // red
                ConAttemptCounter = ConAttemptCounter + 1;
                ((TextView) findViewById(R.id.conaCount)).setText("" + ConAttemptCounter);
                if (mTestDataFile != null) {
                    mTestDataFile.SetTimeNow(TestDataFile.TimeForState.Connecting);
                }
            }
            break;
          /*  case FetchingGOInfo:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xff008000); // darker Green
                break;*/
            case HandShaking:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xFFFFA500); // Orange
                break;
            case Connected: {
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xff7FFF00); // lighter green
                ConnectionCounter = ConnectionCounter + 1;
                ((TextView) findViewById(R.id.conCount)).setText("" + ConnectionCounter);
                if (mTestDataFile != null) {
                    mTestDataFile.SetTimeNow(TestDataFile.TimeForState.Connected);
                }
            }
            break;
            case Disconnecting:
                ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xff4169E1); // royal blue
                break;
            case Disconnected:
                {
                    ((TextView) findViewById(R.id.connstatusBox)).setBackgroundColor(0xffF4A460); // SandyBrown
                    startListenerThread();
                }
                break;
        }

        print_line("CON-STATE", "New state: " + newState);
    }

    @Override
    public void ListeningStateChanged(WifiAPConnector.ListeningState newState) {
        ((TextView) findViewById(R.id.liststatusBox)).setText("State : " + newState);
        switch(newState){
            case Idle:
                ((TextView) findViewById(R.id.liststatusBox)).setBackgroundColor(0xff444444); //dark Gray
                break;
            case NotInitialized:
                ((TextView) findViewById(R.id.liststatusBox)).setBackgroundColor(0xffcccccc); //light Gray
                break;
            case WaitingStateChange:
                ((TextView) findViewById(R.id.liststatusBox)).setBackgroundColor(0xffEE82EE); // pink
                break;
            case Listening:
                ((TextView) findViewById(R.id.liststatusBox)).setBackgroundColor(0xff00ffff); // Cyan
                break;
            case ConnectedAndListening: {
                GotConnectionCounter = GotConnectionCounter + 1;
                ((TextView) findViewById(R.id.conGotCount)).setText("" + GotConnectionCounter);

                ((TextView) findViewById(R.id.liststatusBox)).setBackgroundColor(0xff00ff00); // green
                if (mTestDataFile != null) {
                    mTestDataFile.SetTimeNow(TestDataFile.TimeForState.Connected);
                }
            }
            break;
        }

        print_line("Lis-STATE", "New state: " + newState);
    }

    public void DataStateChanged(DataState newState) {
        currentState = newState;
        ((TextView) findViewById(R.id.datatatusBox)).setText("State : " + newState);
        switch(newState){
            case Idle:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xff444444); //dark Gray
                break;
            case Listening:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xff00ffff); // Cyan
                break;
            case Connecting:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xffffff00); // yellow
                break;
            case SendingFirstMessage:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xffff0000); // red
                break;
            case Connected:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xff00ff00); // green
                break;
            case SendingAct:
            case WaitingAct:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xffF4A460); // SandyBrown
                break;
            case SendingBigData:
            case WaitingBigdata:
                ((TextView) findViewById(R.id.datatatusBox)).setBackgroundColor(0xff0000ff); //blue
                break;
        }

        print_line("Data state", "New state: " + newState);
    }

    private void goToNextClientWaiting(){
        stopConnectedThread();
        stopConnectToThread();
        DisconnectGroupOwnerTimeOut.cancel();

        if(ClientIPAddressList.size() > 0){
            //With this test we'll just handle each client one-by-one in order they got connected
            String Address = ClientIPAddressList.get(0);
            ClientIPAddressList.remove(0);

            print_line("Data state", "Will connect to " + Address);
            mTestConnectToThread = new TestConnectToThread(that,Address,TestChatPortNumber);
            mTestConnectToThread.start();

        }else{
            print_line("Data state", "All addresses connected, will start exit timer now.");
            // lets just see if we get more connections coming in before the timeout comes
            DisconnectGroupOwnerTimeOut.start();
        }
    }

    public void startTestConnection(Socket socket,boolean outGoing) {
        amIBigSender = outGoing;
        gotFirstMessage = false;
        wroteFirstMessage = false;
        wroteDataAmount = 0;
        gotDataAmount = 0;


        mTestConnectedThread = new TestConnectedThread(socket, mHandler);
        mTestConnectedThread.start();

        //both ends can not do cancelling.
        BigBufferReceivingTimeOut.cancel();

        if(!amIBigSender) {
            sayHi();
        }
    }

    @Override
    public void Connected(Socket socket) {
        print_line("Whatsup","Connected to ");

        final Socket socketTmp = socket;
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                // giving the socket to Connected thread, so need to get rid of the connect to thread instance
                mTestConnectToThread = null;
                DataStateChanged(DataState.Connected);
                startTestConnection(socketTmp, true);
            }
        }));
    }

    @Override
    public void GotConnection(Socket socket) {
        print_line("Whatsup","We got incoming connection");

        final Socket socketTmp = socket;
        runOnUiThread(new Thread(new Runnable() {
            public void run() {
                // giving the socket to Connected thread, so need to get rid of the connect to thread instance
                startListenerThread();
                mTestConnectToThread = null;
                DataStateChanged(DataState.Connected);
                startTestConnection(socketTmp, false);
            }
        }));
    }

    @Override
    public void ConnectionFailed(String reason) {
        //would likely just skip this client, and go for next
        // likely happens when client that was connected, got disconnected
        runOnUiThread(new Thread(new Runnable() {
            public void run() {
                ComFailedCounter = ComFailedCounter + 1;
                ((TextView) findViewById(R.id.failCount)).setText("" + ComFailedCounter);
                goToNextClientWaiting();
            }
        }));
    }

    @Override
    public void ListeningFailed(String reason) {
        //lets re-start the listening for incoming test communications
        runOnUiThread(new Thread(new Runnable() {
            public void run() {
                startListenerThread();
            }
        }));
    }


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TestConnectedThread.MESSAGE_WRITE:
                    if (amIBigSender) {
                        timeCounter = 0;
                        wroteDataAmount = wroteDataAmount + msg.arg1;
                        ((TextView) findViewById(R.id.CountBox)).setText("" + wroteDataAmount);
                        if (wroteDataAmount == 1048576) {
                            if (mTestDataFile != null) {
                                // lets do saving after we got ack received
                                //sendMessageCounter = sendMessageCounter+ 1;
                                //((TextView) findViewById(R.id.msgSendCount)).setText("" + sendMessageCounter);
                                mTestDataFile.SetTimeNow(TestDataFile.TimeForState.GoBigtData);
                                long timeval = mTestDataFile.timeBetween(TestDataFile.TimeForState.GoBigtData, TestDataFile.TimeForState.GotData);

                                final String sayoutloud = "Send megabyte in : " + (timeval / 1000) + " seconds.";

                                // lets do saving after we got ack received
                                //mTestDataFile.WriteDebugline("BigSender");

                                print_line("CHAT", sayoutloud);
                                mySpeech.speak(sayoutloud);
                            }
                            DataStateChanged(DataState.WaitingAct);
                        }
                    } else if(currentState == DataState.SendingAct) {
                        DataStateChanged(DataState.Idle);
                    }else {
                        DataStateChanged(DataState.WaitingBigdata);
                        byte[] writeBuf = (byte[]) msg.obj;// construct a string from the buffer
                        String writeMessage = new String(writeBuf);
                        if (mTestDataFile != null) {
                            mTestDataFile.SetTimeNow(TestDataFile.TimeForState.GotData);
                        }

                        wroteDataAmount = 0;
                        wroteFirstMessage = true;
                        print_line("CHAT", "Wrote: " + writeMessage);
                    }
                    break;
                case TestConnectedThread.MESSAGE_READ:
                    if (!amIBigSender) {
                        gotDataAmount = gotDataAmount + msg.arg1;
                        timeCounter = 0;
                        ((TextView) findViewById(R.id.CountBox)).setText("" + gotDataAmount);
                        if (gotDataAmount == 1048576) {
                            BigBufferReceivingTimeOut.cancel();

                            gotFirstMessage = false;
                            gotMessageCounter = gotMessageCounter+ 1;
                            ((TextView) findViewById(R.id.msgGotCount)).setText("" + gotMessageCounter);

                            if (mTestDataFile != null) {
                                mTestDataFile.SetTimeNow(TestDataFile.TimeForState.GoBigtData);

                                long timeval = mTestDataFile.timeBetween(TestDataFile.TimeForState.GoBigtData, TestDataFile.TimeForState.GotData);
                                final String sayoutloud = "Got megabyte in : " + (timeval / 1000) + " seconds.";

                                mTestDataFile.WriteDebugline("Receiver");

                                print_line("CHAT", sayoutloud);
                                mySpeech.speak(sayoutloud);
                            }

                            //got message
                            DataStateChanged(DataState.SendingAct);
                            sayAck(gotDataAmount);
                        }
                    } else if(gotFirstMessage) {
                        print_line("CHAT", "we got Ack message back, so lets disconnect.");
                        BigBufferReceivingTimeOut.cancel();

                        sendMessageCounter = sendMessageCounter+ 1;
                        ((TextView) findViewById(R.id.msgSendCount)).setText("" + sendMessageCounter);
                        if (mTestDataFile != null) {
                            mTestDataFile.WriteDebugline("BigSender");
                        }
                        DataStateChanged(DataState.Idle);
                        // we got Ack message back, so lets disconnect
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            //There are supposedly a possible race-condition bug with the service discovery
                            // thus to avoid it, we are delaying the service discovery start here
                            public void run() {
                                //print_line("CHAT", "disconnect disabled for testing.");
                                goToNextClientWaiting();
                            }
                        }, 1000);
                    }else{
                        byte[] readBuf = (byte[]) msg.obj;// construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        if (mTestDataFile != null) {
                            mTestDataFile.SetTimeNow(TestDataFile.TimeForState.GotData);
                        }

                        gotFirstMessage = true;
                        print_line("CHAT", "Got message: " + readMessage);
                        if (amIBigSender) {
                            DataStateChanged(DataState.SendingBigData);
                            sayItWithBigBuffer();
                        }
                    }
                    break;
                case TestConnectedThread.SOCKET_DISCONNEDTED: {
                    DataStateChanged(DataState.Idle);
                    print_line("CHAT", "WE are Disconnected now.");
                    stopConnectedThread();
                }
                break;
            }
        }
    };

    private void sayAck(long data) {
        if (mTestConnectedThread != null) {
            String message = "Got:"+data;
            print_line("CHAT", "sayAck");
            mTestConnectedThread.write(message.getBytes());
        }
    }

    private void sayHi() {
        if (mTestConnectedThread != null) {
            String message = "Hello from ";
            print_line("CHAT", "sayHi");
            DataStateChanged(DataState.SendingFirstMessage);
            mTestConnectedThread.write(message.getBytes());
        }
    }

    private void sayItWithBigBuffer() {
        if (mTestConnectedThread != null) {
            iWasBigSender = true;
            byte[] buffer = new byte[1048576]; //Megabyte buffer
            new Random().nextBytes(buffer);
            print_line("CHAT", "sayItWithBigBuffer");
            DataStateChanged(DataState.SendingBigData);
            mTestConnectedThread.write(buffer);
        }
    }

    public void print_line(String who, String line) {
        Log.i("BtTestMaa" + who, line);
        timeCounter = 0;
    }
}


