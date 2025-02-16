package com.geeksville.mta.model

import android.app.Application
import android.net.Uri
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mta.AdminProtos
import com.geeksville.mta.ChannelProtos
import com.geeksville.mta.ClientOnlyProtos.DeviceProfile
import com.geeksville.mta.ConfigProtos
import com.geeksville.mta.IMeshService
import com.geeksville.mta.MeshProtos
import com.geeksville.mta.ModuleConfigProtos
import com.geeksville.mta.MyNodeInfo
import com.geeksville.mta.NodeInfo
import com.geeksville.mta.Portnums
import com.geeksville.mta.Position
import com.geeksville.mta.android.Logging
import com.geeksville.mta.config
import com.geeksville.mta.database.MeshLogRepository
import com.geeksville.mta.database.entity.MeshLog
import com.geeksville.mta.deviceProfile
import com.geeksville.mta.moduleConfig
import com.geeksville.mta.repository.datastore.RadioConfigRepository
import com.geeksville.mta.ui.ConfigRoute
import com.geeksville.mta.ui.ResponseState
import com.google.protobuf.MessageLite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Data class that represents the current RadioConfig state.
 */
data class RadioConfigState(
    val route: String = "",
    val userConfig: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    val channelList: List<ChannelProtos.ChannelSettings> = emptyList(),
    val radioConfig: ConfigProtos.Config = ConfigProtos.Config.getDefaultInstance(),
    val moduleConfig: ModuleConfigProtos.ModuleConfig = ModuleConfigProtos.ModuleConfig.getDefaultInstance(),
    val ringtone: String = "",
    val cannedMessageMessages: String = "",
    val responseState: ResponseState<Boolean> = ResponseState.Empty,
)

@HiltViewModel
class RadioConfigViewModel @Inject constructor(
    private val app: Application,
    private val radioConfigRepository: RadioConfigRepository,
    meshLogRepository: MeshLogRepository,
) : ViewModel(), Logging {

    private var destNum: Int = 0
    private val meshService: IMeshService? get() = radioConfigRepository.meshService

    // Connection state to our radio device
    val connectionState get() = radioConfigRepository.connectionState

    // A map from nodeNum to NodeInfo
    val nodes: StateFlow<Map<Int, NodeInfo>> get() = radioConfigRepository.nodeDBbyNum

    private val _myNodeInfo = MutableStateFlow<MyNodeInfo?>(null)
    val myNodeInfo get() = _myNodeInfo

    private val requestIds = MutableStateFlow<HashMap<Int, Boolean>>(hashMapOf())
    private val _radioConfigState = MutableStateFlow(RadioConfigState())
    val radioConfigState: StateFlow<RadioConfigState> = _radioConfigState

    private val _currentDeviceProfile = MutableStateFlow(deviceProfile {})
    val currentDeviceProfile get() = _currentDeviceProfile.value

    init {
        radioConfigRepository.myNodeInfoFlow().onEach {
            _myNodeInfo.value = it
        }.launchIn(viewModelScope)

        radioConfigRepository.deviceProfileFlow.onEach {
            _currentDeviceProfile.value = it
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            combine(meshLogRepository.getAllLogs(9), requestIds) { list, ids ->
                val unprocessed = ids.filterValues { !it }.keys.ifEmpty { return@combine emptyList() }
                list.filter { log -> log.meshPacket?.decoded?.requestId in unprocessed }
            }.collect { it.forEach(::processPacketResponse) }
        }
        debug("RadioConfigViewModel created")
    }

    val myNodeNum get() = myNodeInfo.value?.myNodeNum
    val maxChannels get() = myNodeInfo.value?.maxChannels ?: 8
    private val ourNodeInfo: NodeInfo? get() = nodes.value[myNodeNum]

    override fun onCleared() {
        super.onCleared()
        debug("RadioConfigViewModel cleared")
    }

    private fun request(
        destNum: Int,
        requestAction: suspend (IMeshService, Int, Int) -> Unit,
        errorMessage: String,
    ) = viewModelScope.launch {
        meshService?.let { service ->
            this@RadioConfigViewModel.destNum = destNum
            val packetId = service.packetId
            try {
                requestAction(service, packetId, destNum)
                requestIds.update { it.apply { put(packetId, false) } }
                _radioConfigState.update { state ->
                    if (state.responseState is ResponseState.Loading) {
                        val total = maxOf(requestIds.value.size, state.responseState.total)
                        state.copy(responseState = state.responseState.copy(total = total))
                    } else {
                        state.copy(responseState = ResponseState.Loading())
                    }
                }
            } catch (ex: RemoteException) {
                errormsg("$errorMessage: ${ex.message}")
            }
        }
    }

    fun setOwner(user: MeshProtos.User) {
        setRemoteOwner(myNodeNum ?: return, user)
    }

    fun setRemoteOwner(destNum: Int, user: MeshProtos.User) = request(
        destNum,
        { service, packetId, _ ->
            _radioConfigState.update { it.copy(userConfig = user) }
            service.setRemoteOwner(packetId, user.toByteArray())
        },
        "Request setOwner error",
    )

    fun getOwner(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteOwner(packetId, dest) },
        "Request getOwner error"
    )

    fun updateChannels(
        destNum: Int,
        new: List<ChannelProtos.ChannelSettings>,
        old: List<ChannelProtos.ChannelSettings>,
    ) {
        getChannelList(new, old).forEach { setRemoteChannel(destNum, it) }

        if (destNum == myNodeNum) viewModelScope.launch {
            radioConfigRepository.replaceAllSettings(new)
        }
        _radioConfigState.update { it.copy(channelList = new) }
    }

    private fun setChannels(channelUrl: String) = viewModelScope.launch {
        val new = Uri.parse(channelUrl).toChannelSet()
        val old = radioConfigRepository.channelSetFlow.firstOrNull() ?: return@launch
        updateChannels(myNodeNum ?: return@launch, new.settingsList, old.settingsList)
    }

    private fun setRemoteChannel(destNum: Int, channel: ChannelProtos.Channel) = request(
        destNum,
        { service, packetId, dest ->
            service.setRemoteChannel(packetId, dest, channel.toByteArray())
        },
        "Request setRemoteChannel error"
    )

    fun getChannel(destNum: Int, index: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteChannel(packetId, dest, index) },
        "Request getChannel error"
    )

    fun setRemoteConfig(destNum: Int, config: ConfigProtos.Config) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(radioConfig = config) }
            service.setRemoteConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
    )

    fun getConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRemoteConfig(packetId, dest, configType) },
        "Request getConfig error",
    )

    fun setModuleConfig(destNum: Int, config: ModuleConfigProtos.ModuleConfig) = request(
        destNum,
        { service, packetId, dest ->
            _radioConfigState.update { it.copy(moduleConfig = config) }
            service.setModuleConfig(packetId, dest, config.toByteArray())
        },
        "Request setConfig error",
    )

    fun getModuleConfig(destNum: Int, configType: Int) = request(
        destNum,
        { service, packetId, dest -> service.getModuleConfig(packetId, dest, configType) },
        "Request getModuleConfig error",
    )

    fun setRingtone(destNum: Int, ringtone: String) {
        _radioConfigState.update { it.copy(ringtone = ringtone) }
        meshService?.setRingtone(destNum, ringtone)
    }

    fun getRingtone(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getRingtone(packetId, dest) },
        "Request getRingtone error"
    )

    fun setCannedMessages(destNum: Int, messages: String) {
        _radioConfigState.update { it.copy(cannedMessageMessages = messages) }
        meshService?.setCannedMessages(destNum, messages)
    }

    fun getCannedMessages(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.getCannedMessages(packetId, dest) },
        "Request getCannedMessages error"
    )

    fun requestShutdown(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestShutdown(packetId, dest) },
        "Request shutdown error"
    )

    fun requestReboot(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestReboot(packetId, dest) },
        "Request reboot error"
    )

    fun requestFactoryReset(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestFactoryReset(packetId, dest) },
        "Request factory reset error"
    )

    fun requestNodedbReset(destNum: Int) = request(
        destNum,
        { service, packetId, dest -> service.requestNodedbReset(packetId, dest) },
        "Request NodeDB reset error"
    )

    fun requestPosition(destNum: Int, position: Position = Position(0.0, 0.0, 0)) {
        try {
            meshService?.requestPosition(destNum, position)
        } catch (ex: RemoteException) {
            errormsg("Request position error: ${ex.message}")
        }
    }

    // Set the radio config (also updates our saved copy in preferences)
    fun setConfig(config: ConfigProtos.Config) {
        setRemoteConfig(myNodeNum ?: return, config)
    }

    fun setModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        setModuleConfig(myNodeNum ?: return, config)
    }

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile: StateFlow<DeviceProfile?> get() = _deviceProfile

    fun setDeviceProfile(deviceProfile: DeviceProfile?) {
        _deviceProfile.value = deviceProfile
    }

    fun importProfile(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri).use { inputStream ->
                val bytes = inputStream?.readBytes()
                val protobuf = DeviceProfile.parseFrom(bytes)
                _deviceProfile.value = protobuf
            }
        } catch (ex: Exception) {
            errormsg("Import DeviceProfile error: ${ex.message}")
            setResponseStateError(ex.customMessage)
        }
    }

    fun exportProfile(uri: Uri) = viewModelScope.launch {
        val profile = deviceProfile.value ?: return@launch
        writeToUri(uri, profile)
        _deviceProfile.value = null
    }

    private suspend fun writeToUri(uri: Uri, message: MessageLite) = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use { outputStream ->
                    message.writeTo(outputStream)
                }
            }
            setResponseStateSuccess()
        } catch (ex: Exception) {
            errormsg("Can't write file error: ${ex.message}")
            setResponseStateError(ex.customMessage)
        }
    }

    fun installProfile(protobuf: DeviceProfile) = with(protobuf) {
        _deviceProfile.value = null
        // meshService?.beginEditSettings()
        if (hasLongName() || hasShortName()) ourNodeInfo?.user?.let {
            val user = it.copy(
                longName = if (hasLongName()) longName else it.longName,
                shortName = if (hasShortName()) shortName else it.shortName
            )
            setOwner(user.toProto())
        }
        if (hasChannelUrl()) try {
            setChannels(channelUrl)
        } catch (ex: Exception) {
            errormsg("DeviceProfile channel import error", ex)
            setResponseStateError(ex.customMessage)
        }
        if (hasConfig()) {
            setConfig(config { device = config.device })
            setConfig(config { position = config.position })
            setConfig(config { power = config.power })
            setConfig(config { network = config.network })
            setConfig(config { display = config.display })
            setConfig(config { lora = config.lora })
            setConfig(config { bluetooth = config.bluetooth })
        }
        if (hasModuleConfig()) moduleConfig.let {
            setModuleConfig(moduleConfig { mqtt = it.mqtt })
            setModuleConfig(moduleConfig { serial = it.serial })
            setModuleConfig(moduleConfig { externalNotification = it.externalNotification })
            setModuleConfig(moduleConfig { storeForward = it.storeForward })
            setModuleConfig(moduleConfig { rangeTest = it.rangeTest })
            setModuleConfig(moduleConfig { telemetry = it.telemetry })
            setModuleConfig(moduleConfig { cannedMessage = it.cannedMessage })
            setModuleConfig(moduleConfig { audio = it.audio })
            setModuleConfig(moduleConfig { remoteHardware = it.remoteHardware })
        }
        setResponseStateSuccess()
        // meshService?.commitEditSettings()
    }

    fun clearPacketResponse() {
        requestIds.value = hashMapOf()
        _radioConfigState.update { it.copy(responseState = ResponseState.Empty) }
    }

    fun setResponseStateLoading(route: String) {
        _radioConfigState.value = RadioConfigState(
            route = route,
            responseState = ResponseState.Loading(),
        )
        // channel editor is synchronous, so we don't use requestIds as total
        if (route == ConfigRoute.CHANNELS.name) setResponseStateTotal(maxChannels + 1)
    }

    private fun setResponseStateTotal(total: Int) {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = state.responseState.copy(total = total))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private fun setResponseStateSuccess() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                state.copy(responseState = ResponseState.Success(true))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private val Exception.customMessage: String get() = "${javaClass.simpleName}: $message"
    private fun setResponseStateError(error: String) {
        _radioConfigState.update { it.copy(responseState = ResponseState.Error(error)) }
    }

    private fun incrementCompleted() {
        _radioConfigState.update { state ->
            if (state.responseState is ResponseState.Loading) {
                val increment = state.responseState.completed + 1
                state.copy(responseState = state.responseState.copy(completed = increment))
            } else {
                state // Return the unchanged state for other response states
            }
        }
    }

    private fun processPacketResponse(log: MeshLog?) {
        val packet = log?.meshPacket ?: return
        val data = packet.decoded
        requestIds.update { it.apply { put(data.requestId, true) } }

        // val destNum = destNode.value?.num ?: return
        val debugMsg =
            "requestId: ${data.requestId.toUInt()} to: ${destNum.toUInt()} received %s from: ${packet.from.toUInt()}"

        if (data?.portnumValue == Portnums.PortNum.ROUTING_APP_VALUE) {
            val parsed = MeshProtos.Routing.parseFrom(data.payload)
            debug(debugMsg.format(parsed.errorReason.name))
            if (parsed.errorReason != MeshProtos.Routing.Error.NONE) {
                setResponseStateError(parsed.errorReason.name)
            } else if (packet.from == destNum) {
                if (requestIds.value.filterValues { !it }.isEmpty()) setResponseStateSuccess()
                else incrementCompleted()
            }
        }
        if (data?.portnumValue == Portnums.PortNum.ADMIN_APP_VALUE) {
            val parsed = AdminProtos.AdminMessage.parseFrom(data.payload)
            debug(debugMsg.format(parsed.payloadVariantCase.name))
            if (destNum != packet.from) {
                setResponseStateError("Unexpected sender: ${packet.from.toUInt()} instead of ${destNum.toUInt()}.")
                return
            }
            // check if destination is channel editor
            val goChannels = radioConfigState.value.route == ConfigRoute.CHANNELS.name
            when (parsed.payloadVariantCase) {
                AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE -> {
                    val response = parsed.getChannelResponse
                    // Stop once we get to the first disabled entry
                    if (response.role != ChannelProtos.Channel.Role.DISABLED) {
                        _radioConfigState.update { state ->
                            state.copy(channelList = state.channelList.toMutableList().apply {
                                add(response.index, response.settings)
                            })
                        }
                        incrementCompleted()
                        if (response.index + 1 < maxChannels && goChannels) {
                            // Not done yet, request next channel
                            getChannel(destNum, response.index + 1)
                        }
                    } else {
                        // Received last channel, update total and start channel editor
                        setResponseStateTotal(response.index + 1)
                    }
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_OWNER_RESPONSE -> {
                    _radioConfigState.update { it.copy(userConfig = parsed.getOwnerResponse) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE -> {
                    val response = parsed.getConfigResponse
                    if (response.payloadVariantCase.number == 0) { // PAYLOADVARIANT_NOT_SET
                        setResponseStateError(response.payloadVariantCase.name)
                    }
                    _radioConfigState.update { it.copy(radioConfig = response) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_MODULE_CONFIG_RESPONSE -> {
                    val response = parsed.getModuleConfigResponse
                    if (response.payloadVariantCase.number == 0) { // PAYLOADVARIANT_NOT_SET
                        setResponseStateError(response.payloadVariantCase.name)
                    }
                    _radioConfigState.update { it.copy(moduleConfig = response) }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CANNED_MESSAGE_MODULE_MESSAGES_RESPONSE -> {
                    _radioConfigState.update {
                        it.copy(cannedMessageMessages = parsed.getCannedMessageModuleMessagesResponse)
                    }
                    incrementCompleted()
                }

                AdminProtos.AdminMessage.PayloadVariantCase.GET_RINGTONE_RESPONSE -> {
                    _radioConfigState.update { it.copy(ringtone = parsed.getRingtoneResponse) }
                    incrementCompleted()
                }

                else -> TODO()
            }
        }
    }
}
