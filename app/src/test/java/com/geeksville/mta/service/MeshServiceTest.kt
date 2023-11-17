package com.geeksville.mta.service

import com.geeksville.mta.MeshProtos
import com.geeksville.mta.MeshUser
import com.geeksville.mta.NodeInfo
import com.geeksville.mta.Position
import org.junit.Assert
import org.junit.Test


class MeshServiceTest {
    val model = MeshProtos.HardwareModel.ANDROID_SIM
    val nodeInfo = NodeInfo(4, MeshUser("+one", "User One", "U1", model), Position(37.1, 121.1, 35, 10))

    @Test
    fun givenNodeInfo_whenUpdatingWithNewTime_thenPositionTimeIsUpdated() {

        val newerTime = 20
        updateNodeInfoTime(nodeInfo, newerTime)
        Assert.assertEquals(newerTime, nodeInfo.lastHeard)
    }
}


