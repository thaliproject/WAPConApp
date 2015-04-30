package org.thaliproject.p2p.wapconapp;

import java.net.Socket;

/**
 * Created by juksilve on 17.4.2015.
 */
public interface  TestConnectCallBack {
    public void Connected(Socket socket);
    public void GotConnection(Socket socket);
    public void ConnectionFailed(String reason);
    public void ListeningFailed(String reason);
}

