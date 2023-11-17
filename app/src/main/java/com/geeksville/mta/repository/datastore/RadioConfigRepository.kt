package com.geeksville.mta.repository.datastore

import com.geeksville.mta.AppOnlyProtos.ChannelSet
import com.geeksville.mta.ChannelProtos.Channel
import com.geeksville.mta.ChannelProtos.ChannelSettings
import com.geeksville.mta.ClientOnlyProtos.DeviceProfile
import com.geeksville.mta.ConfigProtos.Config
import com.geeksville.mta.IMeshService
import com.geeksville.mta.LocalOnlyProtos.LocalConfig
import com.geeksville.mta.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mta.ModuleConfigProtos.ModuleConfig
import com.geeksville.mta.MyNodeInfo
import com.geeksville.mta.NodeInfo
import com.geeksville.mta.deviceProfile
import com.geeksville.mta.model.NodeDB
import com.geeksville.mta.model.getChannelUrl
import com.geeksville.mta.service.MeshService.ConnectionState
import com.geeksville.mta.service.ServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Class responsible for radio configuration data.
 * Combines access to [nodeDB], [ChannelSet], [LocalConfig] & [LocalModuleConfig].
 */
class RadioConfigRepository @Inject constructor(
    private val serviceRepository: ServiceRepository,
    val nodeDB: NodeDB,
    private val channelSetRepository: ChannelSetRepository,
    private val localConfigRepository: LocalConfigRepository,
    private val moduleConfigRepository: ModuleConfigRepository,
) {
    val meshService: IMeshService? get() = serviceRepository.meshService

    // Connection state to our radio device
    val connectionState get() = serviceRepository.connectionState
    fun setConnectionState(state: ConnectionState) = serviceRepository.setConnectionState(state)

    /**
     * Flow representing the [MyNodeInfo] database.
     */
    fun myNodeInfoFlow(): Flow<MyNodeInfo?> = nodeDB.myNodeInfoFlow()
    suspend fun getMyNodeInfo(): MyNodeInfo? = myNodeInfoFlow().firstOrNull()

    val nodeDBbyNum: StateFlow<Map<Int, NodeInfo>> get() = nodeDB.nodeDBbyNum
    val nodeDBbyID: StateFlow<Map<String, NodeInfo>> get() = nodeDB.nodes

    /**
     * Flow representing the [NodeInfo] database.
     */
    fun nodeInfoFlow(): Flow<List<NodeInfo>> = nodeDB.nodeInfoFlow()
    suspend fun getNodes(): List<NodeInfo>? = nodeInfoFlow().firstOrNull()

    suspend fun upsert(node: NodeInfo) = nodeDB.upsert(node)
    suspend fun installNodeDB(mi: MyNodeInfo, nodes: List<NodeInfo>) {
        nodeDB.installNodeDB(mi, nodes)
    }

    /**
     * Flow representing the [ChannelSet] data store.
     */
    val channelSetFlow: Flow<ChannelSet> = channelSetRepository.channelSetFlow

    /**
     * Clears the [ChannelSet] data in the data store.
     */
    suspend fun clearChannelSet() {
        channelSetRepository.clearChannelSet()
    }

    /**
     * Replaces the [ChannelSettings] list with a new [settingsList].
     */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetRepository.clearSettings()
        channelSetRepository.addAllSettings(settingsList)
    }

    /**
     * Updates the [ChannelSettings] list with the provided channel and returns the index of the
     * admin channel after the update (if not found, returns 0).
     * @param channel The [Channel] provided.
     * @return the index of the admin channel after the update (if not found, returns 0).
     */
    suspend fun updateChannelSettings(channel: Channel) {
        return channelSetRepository.updateChannelSettings(channel)
    }

    /**
     * Flow representing the [LocalConfig] data store.
     */
    val localConfigFlow: Flow<LocalConfig> = localConfigRepository.localConfigFlow

    /**
     * Clears the [LocalConfig] data in the data store.
     */
    suspend fun clearLocalConfig() {
        localConfigRepository.clearLocalConfig()
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     * @param config The [Config] to be set.
     */
    suspend fun setLocalConfig(config: Config) {
        localConfigRepository.setLocalConfig(config)
        if (config.hasLora()) channelSetRepository.setLoraConfig(config.lora)
    }

    /**
     * Flow representing the [LocalModuleConfig] data store.
     */
    val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigRepository.moduleConfigFlow

    /**
     * Clears the [LocalModuleConfig] data in the data store.
     */
    suspend fun clearLocalModuleConfig() {
        moduleConfigRepository.clearLocalModuleConfig()
    }

    /**
     * Updates [LocalModuleConfig] from each [ModuleConfig] oneOf.
     * @param config The [ModuleConfig] to be set.
     */
    suspend fun setLocalModuleConfig(config: ModuleConfig) {
        moduleConfigRepository.setLocalModuleConfig(config)
    }

    /**
     * Flow representing the combined [DeviceProfile] protobuf.
     */
    val deviceProfileFlow: Flow<DeviceProfile> = combine(
        myNodeInfoFlow(),
        nodeDBbyNum,
        channelSetFlow,
        localConfigFlow,
        moduleConfigFlow,
    ) { myInfo, nodes, channels, localConfig, localModuleConfig ->
        deviceProfile {
            nodes[myInfo?.myNodeNum]?.user?.let {
                longName = it.longName
                shortName = it.shortName
            }
            channelUrl = channels.getChannelUrl().toString()
            config = localConfig
            moduleConfig = localModuleConfig
        }
    }
}
