package com.waypointvoice.app;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Android only lets an app read which music is playing (and control it)
 * if it has a notification listener that you've approved in Settings → Notification access.
 * The actual music logic lives in NativeBridge.
 */
public class MediaListener extends NotificationListenerService { }
