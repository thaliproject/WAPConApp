// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.

package org.thaliproject.p2p.wifiapconnector;



import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Handler;
import android.util.Log;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;


/**
 * Created by juksilve on 28.2.2015.
 */
public class WifiAccessPoint implements WifiBase.HandShakeListenCallBack, WifiP2pManager.ConnectionInfoListener,WifiP2pManager.GroupInfoListener {

    WifiAccessPoint that = this;

    Context context;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    WifiBase.WifiStatusCallBack callback;
    private Handler mHandler = null;

    String mNetworkName = "";
    String mPassphrase = "";
    String mInetAddress = "";

    int lastError = -1;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    HandShakeListenerThread mHandShakeListenerThread = null;

    public WifiAccessPoint(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel, WifiBase.WifiStatusCallBack Callback) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.callback = Callback;
        this.mHandler = new Handler(this.context.getMainLooper());
    }

    public int GetLastError(){
        return lastError;
    }

    public void Start() {
        receiver = new AccessPointReceiver();
        filter = new IntentFilter();
        filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        this.context.registerReceiver(receiver, filter);

        reStartHandShakeListening();

        p2p.createGroup(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                debug_print("Creating Local Group ");
            }

            public void onFailure(int reason) {
                lastError = reason;
                debug_print("Local Group failed, error code " + reason);
            }
        });
    }

    public void Stop() {
        debug_print("Stop WifiAccessPoint");
        this.context.unregisterReceiver(receiver);
        if(mHandShakeListenerThread != null){
            mHandShakeListenerThread.Stop();
            mHandShakeListenerThread = null;
        }
        stopLocalServices();
        removeGroup();
    }

    public void removeGroup() {
        if (p2p != null && channel != null) {
         //   p2p.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
           //     @Override
             //   public void onGroupInfoAvailable(WifiP2pGroup group) {
               //     if (group != null && p2p != null && channel != null && group.isGroupOwner()) {
                        debug_print("Clling for removeGroup");
                        p2p.removeGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                lastError = -1;
                                debug_print("removeGroup onSuccess -");
                            }
                            @Override
                            public void onFailure(int reason) {
                                lastError = reason;
                                debug_print("removeGroup onFailure -" + reason);}
                        });
               //     }
             //   }
           // });
        }
    }

    private void startLocalService(String instance) {

        Map<String, String> record = new HashMap<String, String>();
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(instance, WifiBase.SERVICE_TYPE, record);

        debug_print("Add local service :" + instance);
        p2p.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                debug_print("Added local service");
            }

            public void onFailure(int reason) {
                lastError = reason;
                debug_print("Adding local service failed, error code " + reason);
            }
        });
    }

    private void stopLocalServices() {

        mNetworkName = "";
        mPassphrase = "";

        p2p.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                debug_print("Cleared local services");
            }

            public void onFailure(int reason) {
                lastError = reason;
                debug_print("Clearing local services failed, error code " + reason);
            }
        });
    }

    @Override
    public void GotConnection(InetAddress remote, InetAddress local) {
        debug_print("GotConnection to: " + remote + ", from " + local);
        final InetAddress remoteTmp = remote;
        final InetAddress localTmp = local;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                reStartHandShakeListening();
                callback.Connected(remoteTmp,true);
            }
        });
    }

    @Override
    public void ListeningFailed(String reason) {
        debug_print("ListeningFailed: " + reason);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                reStartHandShakeListening();
            }
        });
    }

    private void  reStartHandShakeListening(){
        if(mHandShakeListenerThread != null){
            mHandShakeListenerThread.Stop();
            mHandShakeListenerThread = null;
        }

        mHandShakeListenerThread = new HandShakeListenerThread(that,WifiBase.HandShakeportToUse);
        mHandShakeListenerThread.start();
    }

    private void debug_print(String buffer) {
   //     Log.i("WifiAccessPoint", buffer);
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        try {
            this.callback.GroupInfoAvailable(group);

            if(mNetworkName.equals(group.getNetworkName()) && mPassphrase.equals(group.getPassphrase())){
                debug_print("Already have local service for " + mNetworkName + " ," + mPassphrase);
            }else {
                mNetworkName = group.getNetworkName();
                mPassphrase = group.getPassphrase();
                startLocalService("NI:" + group.getNetworkName() + ":" + group.getPassphrase() + ":" + mInetAddress);
            }
        } catch(Exception e) {
            debug_print("onGroupInfoAvailable, error: " + e.toString());
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        try {
            if (info.isGroupOwner) {
                mInetAddress = info.groupOwnerAddress.getHostAddress();
                p2p.requestGroupInfo(channel,this);
            } else {
                debug_print("we are client !! group owner address is: " + info.groupOwnerAddress.getHostAddress());
            }
        } catch(Exception e) {
            debug_print("onConnectionInfoAvailable, error: " + e.toString());
        }
    }

    private class AccessPointReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()) {
                    debug_print("We are connected, will check info now");
                    p2p.requestConnectionInfo(channel, that);
                } else{
                    debug_print("We are DIS-connected");
                }
            }
        }
    }
}
