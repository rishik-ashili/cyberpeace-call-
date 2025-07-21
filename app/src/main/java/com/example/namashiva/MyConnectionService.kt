package com.example.namashiva

import android.telecom.Connection
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.content.Context
import android.os.Build
import android.telecom.DisconnectCause
import android.util.Log

class MyConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: android.telecom.ConnectionRequest?): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                super.onAnswer()
                Log.i("MyConnectionService", "Call answered programmatically.")
                setActive()
            }
            override fun onDisconnect() {
                super.onDisconnect()
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }
        connection.setInitializing()
        connection.setActive()
        // Auto-answer the call
        connection.onAnswer()
        return connection
    }
} 