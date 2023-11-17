package com.geeksville.mta.service

open class RadioNotConnectedException(message: String = "Not connected to radio") :
    BLEException(message)