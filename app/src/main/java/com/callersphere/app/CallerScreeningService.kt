package com.callersphere.app

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Initial Android call-screening foundation.
 * This prototype deliberately allows calls and does not upload numbers.
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
