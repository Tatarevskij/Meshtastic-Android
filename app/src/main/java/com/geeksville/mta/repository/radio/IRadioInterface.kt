package com.geeksville.mta.repository.radio

import java.io.Closeable

interface IRadioInterface : Closeable {
    fun handleSendToRadio(p: ByteArray)
}

