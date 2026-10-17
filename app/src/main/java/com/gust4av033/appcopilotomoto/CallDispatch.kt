package com.gust4av033.appcopilotomoto

import android.content.Intent
import android.net.Uri

/** Opening the phone app is a dispatch; it never proves that a call connected. */
internal object CallDispatch {
    fun intent(number: String, direct: Boolean): Intent = Intent(
        if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
        Uri.fromParts("tel", number, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
