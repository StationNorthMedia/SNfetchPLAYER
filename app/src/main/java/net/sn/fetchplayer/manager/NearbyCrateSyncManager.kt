package net.sn.fetchplayer.manager

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.sn.fetchplayer.manager.PlaylistManager.RawPlaylistItem
import net.sn.fetchplayer.util.AppLogger
import java.io.ByteArrayInputStream

/**
 * Peer-to-Peer direct playlist transfer manager using Google Play Services Nearby Connections.
 * Allows phones, tablets, and Android TVs to discover each other offline and beam .sncrate mixtapes.
 */
class NearbyCrateSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "NearbyCrateSync"
        private const val MAX_BYTES_PAYLOAD_SIZE = 32000
    }

    data class DiscoveredDevice(
        val endpointId: String,
        val deviceName: String
    )

    sealed class SyncState {
        object Idle : SyncState()
        object Searching : SyncState()
        data class DiscoveredDevices(val devices: List<DiscoveredDevice>) : SyncState()
        data class Connecting(val endpointId: String, val deviceName: String) : SyncState()
        data class Connected(val endpointId: String, val deviceName: String) : SyncState()
        data class Sending(val progressPercent: Int) : SyncState()
        data class Receiving(val progressPercent: Int) : SyncState()
        data class CrateReceived(
            val manifest: CrateArchiveManager.CrateManifest,
            val tracks: List<RawPlaylistItem>
        ) : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val errorMessage: String) : SyncState()
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId: String = context.packageName

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val discoveredEndpoints = mutableMapOf<String, DiscoveredDevice>()
    private var activeConnectedEndpointId: String? = null
    private var activeConnectedDeviceName: String? = null

    val localDeviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) model else "$manufacturer $model"
        }

    // -------------------------------------------------------------------------
    // Session Lifecycle: Start Discovery & Advertising
    // -------------------------------------------------------------------------

    fun startAirBeamSession() {
        stopAll()
        discoveredEndpoints.clear()
        _syncState.value = SyncState.Searching

        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startAdvertising(
            localDeviceName,
            serviceId,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            AppLogger.d(TAG, "Nearby advertising started as '$localDeviceName'")
        }.addOnFailureListener { e ->
            AppLogger.w(TAG, "Nearby advertising failed: ${e.message}")
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            AppLogger.d(TAG, "Nearby discovery started for service '$serviceId'")
        }.addOnFailureListener { e ->
            AppLogger.e(TAG, "Nearby discovery failed: ${e.message}", e)
            _syncState.value = SyncState.Error("Discovery error: ${e.message}")
        }
    }

    fun connectToDevice(device: DiscoveredDevice) {
        _syncState.value = SyncState.Connecting(device.endpointId, device.deviceName)
        connectionsClient.requestConnection(
            localDeviceName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            AppLogger.e(TAG, "Connection request to ${device.endpointId} failed: ${e.message}", e)
            _syncState.value = SyncState.Error("Failed to connect: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Sending & Receiving .sncrate Playlists
    // -------------------------------------------------------------------------

    fun sendPlaylist(playlist: CustomPlaylist, curatorName: String = localDeviceName) {
        val targetEndpoint = activeConnectedEndpointId
        if (targetEndpoint == null) {
            _syncState.value = SyncState.Error("No active connection to send playlist.")
            return
        }

        try {
            val zipBytes = CrateArchiveManager.packCrateToBytes(playlist, curatorName)
            val payload = if (zipBytes.size <= MAX_BYTES_PAYLOAD_SIZE) {
                Payload.fromBytes(zipBytes)
            } else {
                Payload.fromStream(ByteArrayInputStream(zipBytes))
            }

            _syncState.value = SyncState.Sending(progressPercent = 0)
            connectionsClient.sendPayload(targetEndpoint, payload)
                .addOnSuccessListener {
                    AppLogger.d(TAG, "Payload queued for delivery to $targetEndpoint (${zipBytes.size} bytes)")
                }
                .addOnFailureListener { e ->
                    AppLogger.e(TAG, "Failed to send payload to $targetEndpoint", e)
                    _syncState.value = SyncState.Error("Send failed: ${e.message}")
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error packaging crate for sending", e)
            _syncState.value = SyncState.Error("Crate packaging error: ${e.message}")
        }
    }

    fun stopAll() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping Nearby: ${e.message}")
        }
        activeConnectedEndpointId = null
        activeConnectedDeviceName = null
        discoveredEndpoints.clear()
        _syncState.value = SyncState.Idle
    }

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            AppLogger.d(TAG, "Discovered device: '$endpointId' (${info.endpointName})")
            discoveredEndpoints[endpointId] = DiscoveredDevice(endpointId, info.endpointName)
            _syncState.value = SyncState.DiscoveredDevices(discoveredEndpoints.values.toList())
        }

        override fun onEndpointLost(endpointId: String) {
            AppLogger.d(TAG, "Lost device: '$endpointId'")
            discoveredEndpoints.remove(endpointId)
            _syncState.value = SyncState.DiscoveredDevices(discoveredEndpoints.values.toList())
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            AppLogger.d(TAG, "Connection initiated with '${connectionInfo.endpointName}' [$endpointId]")
            // Auto-accept connection for Station North App-to-App exchange
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    AppLogger.e(TAG, "Failed to accept connection with $endpointId", e)
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                AppLogger.d(TAG, "Connection established with $endpointId")
                activeConnectedEndpointId = endpointId
                val name = discoveredEndpoints[endpointId]?.deviceName ?: "Station North Device"
                activeConnectedDeviceName = name
                _syncState.value = SyncState.Connected(endpointId, name)
            } else {
                AppLogger.w(TAG, "Connection rejected or failed: ${resolution.status.statusCode}")
                _syncState.value = SyncState.Error("Connection rejected or failed (${resolution.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            AppLogger.d(TAG, "Disconnected from $endpointId")
            if (activeConnectedEndpointId == endpointId) {
                activeConnectedEndpointId = null
                activeConnectedDeviceName = null
                _syncState.value = SyncState.Idle
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            AppLogger.d(TAG, "Incoming payload from $endpointId (type: ${payload.type})")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes()
                    if (bytes != null) {
                        processReceivedBytes(bytes)
                    }
                }
                Payload.Type.STREAM -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val stream = payload.asStream()?.asInputStream()
                        if (stream != null) {
                            val bytes = stream.readBytes()
                            processReceivedBytes(bytes)
                        }
                    }
                }
                else -> {
                    AppLogger.w(TAG, "Unsupported payload type: ${payload.type}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val totalBytes = update.totalBytes
            val bytesTransferred = update.bytesTransferred
            val percent = if (totalBytes > 0) ((bytesTransferred.toFloat() / totalBytes) * 100).toInt() else 0

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val currentState = _syncState.value
                    if (currentState is SyncState.Sending) {
                        _syncState.value = SyncState.Sending(percent)
                    } else {
                        _syncState.value = SyncState.Receiving(percent)
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    AppLogger.d(TAG, "Payload transfer finished successfully with $endpointId")
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    AppLogger.e(TAG, "Payload transfer failed with $endpointId")
                    _syncState.value = SyncState.Error("Payload transfer failed.")
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    AppLogger.w(TAG, "Payload transfer canceled.")
                }
            }
        }
    }

    private fun processReceivedBytes(bytes: ByteArray) {
        val result = CrateArchiveManager.unpackCrateFromBytes(bytes)
        if (result != null) {
            val (manifest, tracks) = result
            AppLogger.d(TAG, "Successfully received crate '${manifest.playlistName}' (${tracks.size} tracks)")
            _syncState.value = SyncState.CrateReceived(manifest, tracks)
        } else {
            AppLogger.e(TAG, "Could not unpack received payload into valid crate")
            _syncState.value = SyncState.Error("Received data is not a valid Station North crate.")
        }
    }
}
