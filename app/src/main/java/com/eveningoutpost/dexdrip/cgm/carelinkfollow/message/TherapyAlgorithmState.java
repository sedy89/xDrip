package com.eveningoutpost.dexdrip.cgm.carelinkfollow.message;

import java.io.Serializable;

public class TherapyAlgorithmState implements Serializable {

    public String autoModeShieldState;
    public String autoModeReadinessState;
    public String plgmLgsState;
    public int safeBasalDuration;
    public int waitToCalibrateDuration;

}
