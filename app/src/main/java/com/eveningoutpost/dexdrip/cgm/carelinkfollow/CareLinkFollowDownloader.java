package com.eveningoutpost.dexdrip.cgm.carelinkfollow;

import android.os.PowerManager;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.UtilityModels.Inevitable;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.client.*;
import com.eveningoutpost.dexdrip.cgm.carelinkfollow.message.RecentData;

import static com.eveningoutpost.dexdrip.Models.JoH.emptyString;

public class CareLinkFollowDownloader {

    private static final String TAG = "CareLinkFollowDL";
    private static final boolean D = false;

    private String carelinkUsername;
    private String carelinkPassword;
    private String carelinkCountry;

    private CareLinkClient carelinkClient;

    private boolean loginDataLooksOkay;

    private static PowerManager.WakeLock wl;

    private String status;

    private long loginBlockedTill = 0;
    private long loginBackoff = Constants.MINUTE_IN_MS;
    private int lastResponseCode = 0;

    public String getStatus(){
        return status;
    }
    public int getLastResponseCode(){
        return  lastResponseCode;
    }

    CareLinkFollowDownloader(String carelinkUsername, String carelinkPassword, String carelinkCountry) {
        this.carelinkUsername = carelinkUsername;
        this.carelinkPassword = carelinkPassword;
        this.carelinkCountry = carelinkCountry;
        loginDataLooksOkay = !emptyString(carelinkUsername) && !emptyString(carelinkPassword) && carelinkCountry != null && !emptyString(carelinkCountry);
    }

    public static void resetInstance() {
        //retrofit = null;
        //service = null;
        UserError.Log.d(TAG, "Instance reset");
        CollectionServiceStarter.restartCollectionServiceBackground();
    }

    public boolean doEverything( ) {
        msg("Start download");

        if (D) UserError.Log.e(TAG, "doEverything called");
        if (loginDataLooksOkay) {
            if (JoH.tsl() > loginBlockedTill) {
                try {
                    if (getCareLinkClient() != null) {
                        extendWakeLock(30_000);
                        backgroundProcessConnectData();
                    } else {
                        UserError.Log.d(TAG, "Cannot get data as CareLinkClient is null");
                        return false;
                    }
                    return true;
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Got exception in getData() " + e);
                    releaseWakeLock();
                    return false;
                }
            } else {
                UserError.Log.e(TAG, "Not trying to login due to backoff timer for login failures until: " + JoH.dateTimeText(loginBlockedTill));
                return false;
            }
        } else {
            final String invalid = "CareLink login data isn't valid!";
            msg(invalid);
            UserError.Log.e(TAG, invalid);
            if(emptyString(carelinkUsername)){
                UserError.Log.e(TAG, "CareLink Username empty!");
            }
            if(emptyString(carelinkPassword)){
                UserError.Log.e(TAG, "CareLink Password empty!");
            }
            if(carelinkCountry == null){
                UserError.Log.e(TAG, "CareLink Country empty!");
            }else if(!CountryUtils.isSupportedCountry(carelinkCountry)){
                UserError.Log.e(TAG, "CareLink Country not supported!");
            }
            return false;
        }

    }

    private void msg(final String msg) {
        status = msg != null ? JoH.hourMinuteString() + ": " + msg : null;
        if (msg != null) UserError.Log.d(TAG, "Setting message: " + status);
    }

    public void invalidateSession() {
        this.carelinkClient = null;
    }

    private void backgroundProcessConnectData() {
        Inevitable.task("proc-carelink-follow", 100, this::processCareLinkData);
        releaseWakeLock(); // handover to inevitable
    }

    // don't call this directly unless you are also handling the wakelock release
    private void processCareLinkData() {

        RecentData recentData = null;
        CareLinkClient carelinkClient = null;

        loginBackoff = 0;

        //Get client
        carelinkClient = getCareLinkClient();
        //Get ConnectData from CareLink client
        if (carelinkClient != null) {

            //Try twice in case of 401 error
            for(int i = 0; i < 2; i++) {
                recentData = getCareLinkClient().getRecentData();
                lastResponseCode = carelinkClient.getLastResponseCode();

                //Data request success
                if (carelinkClient.getLastDataSuccess()) {
                    UserError.Log.d(TAG, "Success call get data! Response code: " + carelinkClient.getLastResponseCode());
                    try {
                        if (recentData == null) {
                            UserError.Log.e(TAG, "Received recent data is empty, nothing to process!");
                            msg("Received data is empty!");
                        } else {
                            if (D) UserError.Log.d(TAG, "Calling data processor");
                            //Process CareLink data (conversion and update xDrip data)
                            CareLinkDataProcessor.processRecentData(recentData, true);
                            if (D) UserError.Log.d(TAG, "Data processor completed");
                            //Update Service status
                            CareLinkFollowService.updateBgReceiveDelay();
                            if (D) UserError.Log.d(TAG, "UpdateBgReceiveDelay finished");
                            msg(null);
                        }
                    } catch (Exception e) {
                        UserError.Log.e(TAG, "Got exception for data update" + e);
                        msg("Data update error!");
                    }
                    //Error during data download
                } else {
                    //login error
                    if (!getCareLinkClient().getLastLoginSuccess()) {
                        UserError.Log.e(TAG, "CareLink login error! Response code: " + carelinkClient.getLastResponseCode());
                        loginBackoff += Constants.MINUTE_IN_MS;
                        loginBlockedTill = JoH.tsl() + loginBackoff;
                        msg("Login error!");
                        //data request error
                    } else if (!getCareLinkClient().getLastDataSuccess()) {
                        UserError.Log.e(TAG, "CareLink download error! Response code: " + carelinkClient.getLastResponseCode());
                        UserError.Log.e(TAG, "Error message: " + getCareLinkClient().getLastErrorMessage());
                        msg("Data request error!");
                    }
                }

                if(carelinkClient.getLastResponseCode() != 401)
                    break;
                else
                    UserError.Log.e(TAG, "Try get data again due to 401 response code." + getCareLinkClient().getLastErrorMessage());

            }

        }

    }


    private CareLinkClient getCareLinkClient() {
        if (carelinkClient == null) {
            try {
                UserError.Log.d(TAG, "Creating CareLinkClient");
                carelinkClient = new CareLinkClient(carelinkUsername, carelinkPassword, carelinkCountry);
            } catch (NullPointerException e) {
                UserError.Log.e(TAG, "Error creating CareLinkClient");
            }
        }
        return carelinkClient;
    }


    private static synchronized void extendWakeLock(final long ms) {
        if (wl == null) {
            if (D) UserError.Log.d(TAG,"Creating wakelock");
            wl = JoH.getWakeLock("CareLinkFollow-download", (int) ms);
        } else {
            JoH.releaseWakeLock(wl); // lets not get too messy
            wl.acquire(ms);
            if (D) UserError.Log.d(TAG,"Extending wakelock");
        }
    }

    protected static synchronized void releaseWakeLock() {
        if (D) UserError.Log.d(TAG, "Releasing wakelock");
        JoH.releaseWakeLock(wl);
    }

}
