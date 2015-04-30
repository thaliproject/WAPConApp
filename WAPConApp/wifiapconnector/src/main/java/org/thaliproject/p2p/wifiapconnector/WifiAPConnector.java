package org.thaliproject.p2p.wifiapconnector;


import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

/**
 * Created by juksilve on 8.4.2015.
 */
public class WifiAPConnector implements WifiBase.WifiStatusCallBack, WifiBase.HandShakerCallBack {

    WifiAPConnector that = this;

    // needed since when starting we get unnessessary Wifi enabled event
    // and I don't want to rely that it comes with all firmware & device  versions
    Boolean wifiIsEnabled = false;

    public enum ConnectionState{
        Idle,
        NotInitialized,
        WaitingStateChange,
        FindingPeers,
        FindingServices,
        Connecting,
        HandShaking,
        Connected,
        Disconnecting,
        Disconnected
    }

    public enum ListeningState {
        Idle,
        NotInitialized,
        WaitingStateChange,
        Listening,
        ConnectedAndListening
    }
    private ListeningState listenState = ListeningState.NotInitialized;
    private ConnectionState connState = ConnectionState.NotInitialized;

    WifiBase mWifiBase = null;
    WifiServiceSearcher mWifiServiceSearcher = null;
    WifiConnection mWifiConnection = null;
    WifiAccessPoint mWifiAccessPoint = null;
    HandShakerThread mHandShakerThread = null;
    private WDCallback callback = null;
    private Context context = null;
    private Handler mHandler = null;

    CountDownTimer ServiceFoundTimeOutTimer = new CountDownTimer(600000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            print_line("", "ServiceFoundTimeOutTimer timeout");
            reStartTheSearch();
        }
    };

    public interface  WDCallback{
        public void Connected(String address, Boolean isGroupOwner);
        public void GroupInfoChanged(WifiP2pGroup group);
        public void ConnectionStateChanged(ConnectionState newState);
        public void ListeningStateChanged(ListeningState newState);
    }

    public WifiAPConnector(Context Context, WDCallback Callback){
        this.context = Context;
        this.callback = Callback;
        this.mHandler = new Handler(this.context.getMainLooper());
        this.connState = ConnectionState.NotInitialized;
        this.listenState = ListeningState.NotInitialized;
    }

    public void Start() {
        Stop();
        //initialize the system, and
        // make sure Wifi is enabled before we start running
        mWifiBase = new WifiBase(this.context, this);
        Boolean WifiOk = mWifiBase.Start();

        if (!WifiOk ) {
            print_line("", "wifi NOT available: ");
            wifiIsEnabled = false;
            setConnectionState(ConnectionState.NotInitialized);
            setListeningState(ListeningState.NotInitialized);
        } else if (mWifiBase.isWifiEnabled()) {
            print_line("", "All stuf available and enabled");
            wifiIsEnabled = true;
            reStartAll();
        }else{
            wifiIsEnabled = false;
            //we wait untill both Wifi & BT are turned on
            setConnectionState(ConnectionState.WaitingStateChange);
            setListeningState(ListeningState.WaitingStateChange);
        }
    }

    private void reStartAll() {
        reStartTheSearch();
        reStartTheAdvertising();
    }

    private void reStartTheSearch() {
        ServiceFoundTimeOutTimer.cancel();
        //to get fresh situation, lets close all stuff before continuing
        stopServiceSearcher();

        WifiP2pManager.Channel channel = mWifiBase.GetWifiChannel();
        WifiP2pManager p2p = mWifiBase.GetWifiP2pManager();

        if (channel != null && p2p != null) {
            print_line("", "Starting WifiServiceSearcher");
            setConnectionState(ConnectionState.FindingPeers);
            mWifiServiceSearcher = new WifiServiceSearcher(this.context, p2p, channel, this,WifiBase.SERVICE_TYPE);
            mWifiServiceSearcher.Start();
        }
    }

    private void reStartTheAdvertising() {

        if(mWifiAccessPoint != null){
            setListeningState(ListeningState.Listening);
        }else{
            WifiP2pManager.Channel channel = mWifiBase.GetWifiChannel();
            WifiP2pManager p2p = mWifiBase.GetWifiP2pManager();

            setListeningState(ListeningState.Listening);
            mWifiAccessPoint = new WifiAccessPoint(this.context, p2p, channel, this);
            mWifiAccessPoint.Start();
        }
    }

    private  void startHandShakerThread(String Address,int trialNum) {
        print_line("", "startHandShakerThread addreess: " + Address + ", port : " + WifiBase.HandShakeportToUse);

        mHandShakerThread = new HandShakerThread(that,Address,WifiBase.HandShakeportToUse,trialNum);
        mHandShakerThread.start();
    }

    public void Stop() {
        print_line("", "Stopping all");
        stopHandShakerThread();
        stopWifiConnection();
        stopWifiAccessPoint();
        stopServiceSearcher();
        stopWifiBase();
        setConnectionState(ConnectionState.NotInitialized);
        setListeningState(ListeningState.NotInitialized);
    }

    private  void stopHandShakerThread() {
        print_line("", "stopHandShakerThread");

        if (mHandShakerThread != null) {
            mHandShakerThread.Stop();
            mHandShakerThread = null;
        }
    }



    private  void stopWifiAccessPoint() {
        print_line("", "stopWifiAccessPoint");

        if (mWifiAccessPoint != null) {
            mWifiAccessPoint.Stop();
            mWifiAccessPoint = null;
        }
    }

    private  void stopWifiConnection() {
        print_line("", "stopWifiConnection");

        if (mWifiConnection != null) {
            mWifiConnection.Stop(true); // do we need to disconnect ?
            mWifiConnection = null;
        }
    }

    private  void stopServiceSearcher() {
        print_line("", "stopServiceSearcher");
        if(ServiceFoundTimeOutTimer != null) {
            ServiceFoundTimeOutTimer.cancel();
        }

        if (mWifiServiceSearcher != null) {
            mWifiServiceSearcher.Stop();
            mWifiServiceSearcher = null;
        }
    }

    private  void stopWifiBase() {
        print_line("", "stopWifiBase");

        if (mWifiBase != null) {
            mWifiBase.Stop();
            mWifiBase = null;
        }
    }

    @Override
    public void WifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            print_line("WB", "Wifi is now enabled !");

            // to avoid getting this event on starting, we we already know the state
            if(!wifiIsEnabled) {
                reStartAll();
            }
            wifiIsEnabled = true;
        } else {
            //no wifi availavble, thus we need to stop doing anything;
            print_line("WB", "Wifi is DISABLEd !!");

            wifiIsEnabled = false;
            stopServiceSearcher();
            stopWifiAccessPoint();

            // indicate the waiting with state change
            setConnectionState(ConnectionState.WaitingStateChange);
            setListeningState(ListeningState.WaitingStateChange);
        }
    }


    @Override
    public boolean  gotPeersList(Collection<WifiP2pDevice> list) {

        boolean cont = true;
        if(mWifiConnection != null){
            print_line("", "gotPeersList, while connecting!!");
            cont = false;
        }else {
            ServiceFoundTimeOutTimer.cancel();
            ServiceFoundTimeOutTimer.start();
            print_line("SS", "Found " + list.size() + " peers.");
            int numm = 0;
            for (WifiP2pDevice peer : list) {
                numm++;
                print_line("SS", "Peer(" + numm + "): " + peer.deviceName + " " + peer.deviceAddress);
            }

            setConnectionState(ConnectionState.FindingServices);
        }
        return cont;
    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {

        if(mWifiBase != null && list != null && list.size() > 0) {

            if(mWifiConnection != null){
                print_line("", "Already connecting !!");
            }else {
                ServiceItem selItem = mWifiBase.SelectServiceToConnect(list);
                if (selItem != null) {

                    print_line("", "Selected device address: " + selItem.instanceName);
                    String[] separated = selItem.instanceName.split(":");
                    print_line("SS", "found SSID:" + separated[1] + ", pwd:"  + separated[2]+ "IP: " + separated[3]);

                    stopServiceSearcher();
                    setConnectionState(ConnectionState.Connecting);

                    final String networkSSID = separated[1];
                    final String networkPass = separated[2];
                    final String ipAddress   = separated[3];

                    print_line("", "Starting to connect now.");
                    mWifiConnection = new WifiConnection(that.context,that);
                    mWifiConnection.SetInetAddress(ipAddress);
                    mWifiConnection.Start(networkSSID,networkPass);

                } else {
                    // we'll get discovery stopped event soon enough
                    // and it starts the discovery again, so no worries :)
                    print_line("", "No devices selected");
                }
            }
            //print_line("", "gotServicesList disable for testing!!");
        }
    }

    @Override
    public void GroupInfoAvailable(WifiP2pGroup group) {
        print_line("CONN","GroupInfoAvailable: " + group.getNetworkName() + " ,cunt: " + group.getClientList().size());
        //do we have connections to our Group

        callback.GroupInfoChanged(group);

        if(group.getClientList().size() > 0){
             // we are ok, we just got new connections coming aiin
        }else{
            // note that we get this when we create a new group, so we also do need to check what the state is we are in

            // if we got zero clients then we are  not conncted anymore, so we can start doing stuff
            if(listenState == ListeningState.ConnectedAndListening){

                reStartTheAdvertising();

                //also if we did stop all searching, lets start it again
                if(connState == ConnectionState.Idle){
                    reStartTheSearch();
                }
            }

        }
    }
/*
    @Override
    public void ConnectionInfoAvailable(WifiP2pInfo info) {
        print_line("CONN","ConnectionInfoAvailable, GO: " + info.isGroupOwner + ", addr: " + info.groupOwnerAddress.getHostAddress());

        // must be done here, to prevent ServiceFoundTimeOutTimer firing while connecting
        stopServiceSearcher();
        // we can get rid of the connection, and same time cancel conenction timer
        stopWifiConnection();

        if(info.isGroupOwner){
            // we'll actually also check the number of connection from the Group
            //Clients will start Handshake, and we are already listening, thus no need to do anything else here
        }else{
            setConnectionState(ConnectionState.HandShaking);
            startHandShakerThread(info.groupOwnerAddress.getHostAddress());

            stopWifiAccessPoint();
            setListeningState(ListeningState.Idle);
        }
    }
*/
    @Override
    public void connectionStatusChanged(WifiBase.ConectionState state, NetworkInfo.DetailedState detailedState, int Error) {

        print_line("COM", "State " + state + ", detailed state: " + detailedState + " , Error: " + Error);

        String conStatus = "";
        if(state == WifiBase.ConectionState.NONE) {
            conStatus = "NONE";
        }else if(state  == WifiBase.ConectionState.Connecting) {
            conStatus = "Connecting";
            setConnectionState(ConnectionState.Connecting);
        }else if(state  == WifiBase.ConectionState.PreConnecting) {
            conStatus = "PreConnecting";
            setConnectionState(ConnectionState.Connecting);
        }else if(state == WifiBase.ConectionState.Connected) {
            conStatus = "Connected";
            if(mWifiConnection != null && mHandShakerThread == null)
            {
                String address = mWifiConnection.GetInetAddress();

                stopServiceSearcher();
                stopWifiAccessPoint();
                setListeningState(ListeningState.Idle);

                setConnectionState(ConnectionState.HandShaking);
                startHandShakerThread(address,0);
            }else{
                conStatus = "already handshaking";
            }
        }else if(state == WifiBase.ConectionState.DisConnecting) {
            conStatus = "DisConnecting";
            setConnectionState(ConnectionState.Disconnecting);
        }else if(state == WifiBase.ConectionState.ConnectingFailed
                ||state == WifiBase.ConectionState.Disconnected) {
            print_line("CON", "We are disconnected, re-starting the search");
            setConnectionState(ConnectionState.Disconnected);
            conStatus = "Disconnected";
            // need to clear the connection here.
            stopHandShakerThread();
            stopWifiConnection();

            // to make sure advertising is ok, lets clear the old out at this point
            stopWifiAccessPoint();
            // we have no connections, so lets make sure we do advertise us, as well as do active discovery
            reStartTheAdvertising();
            reStartTheSearch();
        }

        print_line("COM", "State change-out with status : " + conStatus);

    }

    @Override
    public void Connected(InetAddress remote, boolean ListeningStill) {

        final InetAddress remoteTmp = remote;
        final boolean ListeningStillTmp = ListeningStill;

        if(ListeningStill){
            stopWifiConnection();
            stopServiceSearcher();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                stopHandShakerThread();

                that.callback.Connected(remoteTmp.getHostAddress(), ListeningStillTmp);

                if (ListeningStillTmp) {
                    // we did not make connection, not would we attempt to make any new ones
                    // we are just listening for more connections, until we lose all connected clients
                    setConnectionState(ConnectionState.Idle);
                    setListeningState(ListeningState.ConnectedAndListening);
                } else {
                    setConnectionState(ConnectionState.Connected);
                    //Clients can not accept connections, thus we are not listening for more either
                    setListeningState(ListeningState.Idle);
                }


            }
        });
    }

    @Override
    public void Connected(InetAddress remote, InetAddress local) {
        //lets just forward this to the main handler above
        Connected(remote, false);
    }

    @Override
    public void ConnectionFailed(String reason, int trialCount) {

        final int trialCountTmp = trialCount;
        final String reasonTmp = reason;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                print_line("HandSS", "HandShake(" + trialCountTmp + ") ConnectionFailed " + reasonTmp);
                //lets do 3 re-tries, we could also have logic that waits that remove group is finihed before
                // doing handshake, or we could try getting it removed earlier
                // anyhow, untill we change the ways, we might get Connection refuced, since our listening is cancelled
                // but our group, might still be having same IP as the remote party has :)
                if(trialCountTmp < 2) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        //There are supposedly a possible race-condition bug with the service discovery
                        // thus to avoid it, we are delaying the service discovery start here
                        public void run() {
                            print_line("shake ", "re shaking.");
                            String address = mWifiConnection.GetInetAddress();
                            setConnectionState(ConnectionState.HandShaking);
                            startHandShakerThread(address,(trialCountTmp + 1));
                        }
                    }, 2000);
                }else{
                    connectionStatusChanged(WifiBase.ConectionState.ConnectingFailed, null,123456);
                }
            }
        });
    }


    private void setConnectionState(ConnectionState newState) {
        if(connState != newState) {
            final ConnectionState tmpState = newState;
            connState = tmpState;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.callback.ConnectionStateChanged(tmpState);
                }
            });
        }
    }

    private void setListeningState(ListeningState newState) {
        if(listenState != newState) {
            final ListeningState tmpState = newState;
            listenState = tmpState;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    that.callback.ListeningStateChanged(tmpState);
                }
            });
        }
    }

    public void print_line(String who, String line) {
      //  Log.i("WifiAPConnector" + who, line);
    }
}
