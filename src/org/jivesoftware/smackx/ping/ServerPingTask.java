package org.jivesoftware.smackx.ping;

import java.lang.ref.WeakReference;
import java.util.Set;

import org.jivesoftware.smack.Connection;

class ServerPingTask implements Runnable {
    
    private WeakReference<Connection> weakConnection;
    private int pingIntervall;
    
    protected ServerPingTask(Connection connection, int pingIntervall) {
        this.weakConnection = new WeakReference<Connection>(connection);
        this.pingIntervall = pingIntervall;
    }
    
    protected void setDone() {
        this.pingIntervall = -1;
    }
    
    protected void setPingIntervall(int pingIntervall) {
        this.pingIntervall = pingIntervall;
    }
    
    protected int getIntIntervall() {
        return pingIntervall;
    }
    
    public void run() {            
        try {
            // Sleep a minimum of 60 seconds plus delay before sending first ping.
            // This will give time to properly finish TLS negotiation and 
            // then start sending XMPP pings to the server.
            Thread.sleep(60000 + pingIntervall);
        }
        catch (InterruptedException ie) {
            // Do nothing
        }
        
        while(pingIntervall > 0) {
            Connection connection = weakConnection.get();
            if (connection == null) {
                // connection has been collected by GC
                // which means we can stop the thread by breaking the loop
                break;
            }
            if (connection.isAuthenticated()) {
                PingManager pingManager = PingManager.getInstaceFor(connection);
                if (!pingManager.pingMyServer()) {
                    Set<PingFailedListener> pingFailedListeners = pingManager.getPingFailedListeners();
                    for (PingFailedListener l : pingFailedListeners) {
                        l.pingFailed();
                    }
                }
            }
            try {
                Thread.sleep(pingIntervall);
            } catch (InterruptedException e) {
                /* Ignore */
            }
        }
    }
}
