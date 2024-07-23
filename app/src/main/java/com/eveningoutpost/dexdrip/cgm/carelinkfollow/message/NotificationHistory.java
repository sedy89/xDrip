package com.eveningoutpost.dexdrip.cgm.carelinkfollow.message;

import java.io.Serializable;
import java.util.List;

public class NotificationHistory implements Serializable {

    public List<ActiveNotification> activeNotifications;
    public List<ClearedNotification> clearedNotifications;

}
