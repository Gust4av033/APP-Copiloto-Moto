package com.gust4av033.appcopilotomoto

import android.content.pm.ServiceInfo

internal fun locationForegroundReady(toggle: Boolean, permission: Boolean, gpsOn: Boolean,
    startedVisible: Boolean): Boolean = toggle && permission && gpsOn && startedVisible

// Remote media controls do not make APP a background media player.
internal fun tripForegroundTypes(locationReady: Boolean): Int =
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
        (if (locationReady) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
