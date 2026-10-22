package com.callersphere.app

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Initial Android call-screening foundation.
 *
 * This prototype deliberately allows calls and does not upload phone numbers.
 * Later phases can add privacy-reviewed lookup and spam logic.
 */
class CallerScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}
