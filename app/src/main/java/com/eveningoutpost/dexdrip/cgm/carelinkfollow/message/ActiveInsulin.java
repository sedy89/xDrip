package com.eveningoutpost.dexdrip.cgm.carelinkfollow.message;

import java.io.Serializable;
import java.util.Date;

/**
 * CareLink ActiveInsulin data
 */
public class ActiveInsulin implements Serializable {

    public Integer code;
    public Date datetime;
    public long version;
    public Double amount;
    public String precision;
    public String kind;

}