// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.

package org.thaliproject.p2p.wifiapconnector;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.util.Log;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;


/**
 * Created by juksilve on 28.2.2015.
 */
public class WifiConnection{

    WifiConnection that = this;


    private WifiBase.ConectionState mConectionState = WifiBase.ConectionState.NONE;

    CountDownTimer ConnectingTimeOutTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            debug_print("Cancelling the connection with timeout");
            //lets cancel
            mConectionState = WifiBase.ConectionState.Disconnected;
            that.callBack.connectionStatusChanged(mConectionState, null,0);
        }
    };


    private boolean hadConnection = false;

    WifiManager wifiManager = null;
    WifiConfiguration wifiConfig = null;
    WifiBase.WifiStatusCallBack callBack = null;
    Context context = null;
    int netId = 0;

    WiFiConnectionReceiver receiver;
    private IntentFilter filter;

    String intetAddress = "";

    public WifiConnection(Context Context, WifiBase.WifiStatusCallBack CallBack) {
        this.context = Context;
        this.callBack = CallBack;

        receiver = new WiFiConnectionReceiver();
        filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        this.context.registerReceiver(receiver, filter);

        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }
    public void Start(String SSIS, String password){

        this.wifiConfig = new WifiConfiguration();
        this.wifiConfig.hiddenSSID = true;
        this.wifiConfig.SSID = String.format("\"%s\"", SSIS);
        this.wifiConfig.preSharedKey = String.format("\"%s\"", password);

        this.netId = this.wifiManager.addNetwork(this.wifiConfig);

    /*    ConnectivityManager connManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (mWifi.isConnected()) {
            Log.i("WifiConnection","already connected");
        }else{
            Log.i("WifiConnection","not connected yet, but lets start");
        }
*/
        this.wifiManager.disconnect();
        this.wifiManager.enableNetwork(this.netId, false);
        this.wifiManager.reconnect();
        ConnectingTimeOutTimer.start();
    }

    public void Stop(boolean disconnect){
        ConnectingTimeOutTimer.cancel();
        this.context.unregisterReceiver(receiver);
        if(disconnect) {
        this.wifiManager.removeNetwork(this.netId);
        this.wifiManager.disableNetwork(this.netId);
        this.wifiManager.disconnect();
            debug_print("Disconnected wifi");
        }
    }

    public void SetInetAddress(String address){
        this.intetAddress = address;
    }

    public String GetInetAddress(){
        return this.intetAddress;
    }

    private class WiFiConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info != null) {

                    if (info.isConnected()) {
                        ConnectingTimeOutTimer.cancel();
                        hadConnection = true;
                        mConectionState = WifiBase.ConectionState.Connected;
                    }else if(info.isConnectedOrConnecting()) {
                        mConectionState = WifiBase.ConectionState.Connecting;
                    }else {
                        if(hadConnection){
                            mConectionState = WifiBase.ConectionState.Disconnected;
                        }else{
                            mConectionState = WifiBase.ConectionState.PreConnecting;
                        }
                    }
                    that.callBack.connectionStatusChanged(mConectionState, info.getDetailedState(),0);

                }

                WifiInfo wiffo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if(wiffo != null){
                    //wiffo.getIpAddress());

                    // you could get otherparty IP via:
                    // http://stackoverflow.com/questions/10053385/how-to-get-each-devices-ip-address-in-wifi-direct-scenario
                    // as well if needed
                }
            }
        }
    }

    public void debug_print(String datttaa){
        Log.i("WifiConnection",datttaa);
    }

}
