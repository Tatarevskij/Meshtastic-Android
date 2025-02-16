package com.geeksville.mta

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Room [Entity] and [PrimaryKey] annotations and imports can be removed when only using the API.
 * For details check the AIDL interface in [com.geeksville.mta.IMeshService]
 */

// MyNodeInfo sent via special protobuf from radio
@Parcelize
@Entity(tableName = "MyNodeInfo")
data class MyNodeInfo(
    @PrimaryKey(autoGenerate = false)
    val myNodeNum: Int,
    val hasGPS: Boolean,
    val model: String?,
    val firmwareVersion: String?,
    val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
    val shouldUpdate: Boolean, // this device has old firmware
    val currentPacketId: Long,
    val messageTimeoutMsec: Int,
    val minAppVersion: Int,
    val maxChannels: Int,
    val hasWifi: Boolean,
    val channelUtilization: Float,
    val airUtilTx: Float
) : Parcelable {
    /** A human readable description of the software/hardware version */
    val firmwareString: String get() = "$model $firmwareVersion"
}
