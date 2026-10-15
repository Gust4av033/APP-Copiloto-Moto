package com.gust4av033.appcopilotomoto

import android.content.pm.ServiceInfo

internal fun locationForegroundReady(toggle: Boolean, permission: Boolean, gpsOn: Boolean,
    startedVisible: Boolean): Boolean = toggle && permission && gpsOn && startedVisible

internal fun tripForegroundTypes(mediaReady: Boolean, locationReady: Boolean): Int =
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
        (if (mediaReady) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0) or
        (if (locationReady) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
