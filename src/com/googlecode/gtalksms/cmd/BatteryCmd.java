package com.googlecode.gtalksms.cmd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.googlecode.gtalksms.MainService;
import com.googlecode.gtalksms.R;
import com.googlecode.gtalksms.xmpp.XmppPresenceStatus;

public class BatteryCmd extends CommandHandlerBase {
    private static final int STEP = 5;
    private static final String PSRC_USB = "USB";
    private static final String PSRC_AC = "AC";
    private static final String PSRC_BATT = "Battery";
    private static final String PSRC_UNKNOWN = "Unknown";
    
    private static boolean sReceiverRegistered = false; 
    private static BroadcastReceiver sBatInfoReceiver = null;
    private static int sLastKnownPercentage = -1; // flag so the BroadcastReceiver can set the percentage
    private static String sLastKnownPowerSource;
    private static int sLastSendPercentage = -1;
    private static String sLastSendPowerSource;
    private static XmppPresenceStatus sXmppPresenceStatus;    
    
    public BatteryCmd(MainService mainService) {
        super(mainService, CommandHandlerBase.TYPE_SYSTEM, "Battery", new Cmd("battery", "batt"));
    }

    protected void onCommandActivated() {
        sXmppPresenceStatus = XmppPresenceStatus.getInstance(sContext);
        sLastKnownPowerSource = "NotInitialized";
        if (!sReceiverRegistered) {
            if (sBatInfoReceiver == null) {
                sBatInfoReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context arg0, Intent intent) {
                        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                        float levelFloat = ((float) level / (float) scale) * 100;
                        level = (int) levelFloat;
                        int pSource = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                        String pSourceStr;
                        switch (pSource) {
                        case 0:
                            pSourceStr = PSRC_BATT;
                            break;
                        case BatteryManager.BATTERY_PLUGGED_AC:
                            pSourceStr = PSRC_AC;
                            break;
                        case BatteryManager.BATTERY_PLUGGED_USB:
                            pSourceStr = PSRC_USB;
                            break;
                        default:
                            pSourceStr = PSRC_UNKNOWN;
                            break;
                        }
                        // something has changed, update
                        if (level != sLastKnownPercentage || !sLastKnownPowerSource.equals(pSourceStr)) {
                            notifyAndSave(level, pSourceStr);           
                        } else if (sLastKnownPercentage == -1) {
                            notifyAndSave(level, pSourceStr);
                        }
                    }
                };
            }
            sContext.registerReceiver(sBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            sReceiverRegistered = true;
        }
    }
    
    @Override
    protected void onCommandDeactivated() {
        sXmppPresenceStatus = null;
        sLastKnownPowerSource = null;
        if (sReceiverRegistered) {
            sContext.unregisterReceiver(sBatInfoReceiver);
            sReceiverRegistered = false;
        }
    }
    
    /**
     * 
     * @param force - always send the battery information via an XMPP message
     */
    private void sendBatteryInfos(boolean force) {
        if (force || mustNotifyUser()) {
            send(R.string.chat_battery_level, sLastKnownPercentage);
            updateBatteryInformation();
        }
        if (sSettingsMgr.notifyBatteryInStatus && batteryInformationChanged()) {
            // send detailed information when on AC or USB
            if (sLastKnownPowerSource.equals(PSRC_AC) || sLastKnownPowerSource.equals(PSRC_USB)) {
                sXmppPresenceStatus.setPowerInfo(sLastKnownPercentage + "%", sLastKnownPowerSource);
            } else {
                String lastRange = intToRange(sLastSendPercentage);
                String newRange = intToRange(sLastKnownPercentage);
                if (!sLastKnownPowerSource.equals(sLastSendPowerSource) || !lastRange.equals(newRange)) {
                    sXmppPresenceStatus.setPowerInfo(newRange + "%", sLastKnownPowerSource);
                }
            }
            updateBatteryInformation();
        }

    }
    
    /**
     * Checks if the preconditions for an automatic notification about the
     * current power status to the user via an XMPP message are given
     * 
     * @return
     */
    private boolean mustNotifyUser() {
        return sSettingsMgr.notifyBattery
                && batteryInformationChanged()
                && sLastKnownPercentage % sSettingsMgr.batteryNotificationIntervalInt == 0;
    }
    
    private void updateBatteryInformation() {
        sLastSendPercentage = sLastKnownPercentage;
        sLastSendPowerSource = sLastKnownPowerSource;
    }
    
    private boolean batteryInformationChanged() {
        return sLastKnownPercentage != sLastSendPercentage || !sLastKnownPowerSource.equals(sLastSendPowerSource);
    }
    
    private void notifyAndSave(int level, String powerSource) {
        sLastKnownPowerSource = powerSource;
        sLastKnownPercentage = level;
        // maybe update the battery information
        sendBatteryInfos(false);
    }
    
    protected void execute(Command cmd) {
        sendBatteryInfos(!cmd.getArg1().equals("silent"));
    }

    private static String intToRange(int in) {
        int lowerBound = (in / 5) * STEP;
        if (lowerBound != 100) {
        int upperBound = lowerBound + STEP;
            return lowerBound + "-" + upperBound;
        } else {
            return Integer.toString(lowerBound);
        }
    }

    @Override
    protected void initializeSubCommands() {
        mCommandMap.get("battery").setHelp(R.string.chat_help_battery, null); 
    }
}
