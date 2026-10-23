package com.callersphere.app

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Applies only the local block list. Cloud/community spam labels do not
 * automatically reject calls in this MVP.
 */
class CallerScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val blocked = BlockListStore.isBlocked(this, incomingNumber)
        val response = CallResponse.Builder()
            .setDisallowCall(blocked)
            .setRejectCall(blocked)
            .setSkipCallLog(false)
            .setSkipNotification(blocked)
            .build()
        respondToCall(callDetails, response)
    }
}
