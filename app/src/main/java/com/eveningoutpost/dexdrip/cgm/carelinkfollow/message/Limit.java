package com.eveningoutpost.dexdrip.cgm.carelinkfollow.message;

import java.io.Serializable;

public class Limit implements Serializable {

    public int index;
    public int highLimit;
    public int lowLimit;
    public String kind;
    public int version;

}
