package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ConnectedDevice
import com.example.model.DeviceType
import com.example.model.HotspotState
import com.example.model.TrafficSample
import com.example.scanner.SubnetScanner
import com.example.service.DnsFilterServer
import com.example.service.HotspotProxyService
import com.example.service.HotspotVpnService
import com.example.service.IptablesController
import com.example.service.TrafficMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DeviceFilter {
    ALL,
    ACTIVE,
    THROTTLED,
    BLOCKED
}

class HotspotViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hotspot_manager_prefs", Context.MODE_PRIVATE)
    private val scanner = SubnetScanner()
    private val trafficMonitor = TrafficMonitor(viewModelScope)

    private val _hotspotState = MutableStateFlow(
        HotspotState(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    )
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedDevice>> = _devices.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(DeviceFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _rootExecutionLog = MutableStateFlow("")
    val rootExecutionLog = _rootExecutionLog.asStateFlow()

    // Filtered devices flow
    val filteredDevices: StateFlow<List<ConnectedDevice>> = combine(
        _devices,
        _searchQuery,
        _selectedFilter
    ) { deviceList, query, filter ->
        deviceList.filter { device ->
            val matchesQuery = query.isBlank() ||
                    device.displayName.contains(query, ignoreCase = true) ||
                    device.ipAddress.contains(query, ignoreCase = true) ||
                    device.macAddress.contains(query, ignoreCase = true) ||
                    device.vendor.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                DeviceFilter.ALL -> true
                DeviceFilter.ACTIVE -> device.isOnline && !device.isBlocked
                DeviceFilter.THROTTLED -> device.bandwidthLimitKbps != null && !device.isBlocked
                DeviceFilter.BLOCKED -> device.isBlocked
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trafficHistory: StateFlow<List<TrafficSample>> = trafficMonitor.trafficHistory

    private val apStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
                val state = intent.getIntExtra("wifi_state", -1)
                val isEnabled = state == 13 || state == 12
                _hotspotState.value = _hotspotState.value.copy(isHotspotActive = isEnabled)
                if (isEnabled) {
                    updateHotspotInterfaceAndState()
                    scanNetwork()
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85
                val tempCelsius = tempTenths / 10f
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                _hotspotState.value = _hotspotState.value.copy(
                    batteryLevel = batteryPct,
                    batteryTempCelsius = tempCelsius,
                    isCharging = isCharging
                )
            }
        }
    }

    init {
        // Register Wi-Fi AP state broadcast receiver (sticky intent on Android 10)
        val apFilter = IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
        val stickyApIntent = application.registerReceiver(apStateReceiver, apFilter)
        val initialApState = stickyApIntent?.getIntExtra("wifi_state", -1) ?: -1
        val isApActive = if (initialApState != -1) (initialApState == 13 || initialApState == 12) else scanner.isHotspotActive(application)
        _hotspotState.value = _hotspotState.value.copy(isHotspotActive = isApActive)

        // Register battery monitor
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        application.registerReceiver(batteryReceiver, filter)

        // Load saved firewall & quota rules from storage
        loadSavedRules()

        // Observe VPN running state
        viewModelScope.launch {
            HotspotVpnService.isVpnRunning.collect { isRunning ->
                _hotspotState.value = _hotspotState.value.copy(isVpnActive = isRunning)
            }
        }

        // Observe Proxy running state
        viewModelScope.launch {
            HotspotProxyService.isProxyRunning.collect { isRunning ->
                _hotspotState.value = _hotspotState.value.copy(isProxyActive = isRunning)
            }
        }

        // Auto-start Local Proxy Gateway (Zero-Root Controller) on startup
        try {
            HotspotProxyService.startService(application)
        } catch (_: Exception) {}

        // Handle Quota exceeded callbacks from Proxy
        HotspotProxyService.onQuotaExceededListener = { ip ->
            viewModelScope.launch {
                val dev = _devices.value.find { it.ipAddress == ip }
                val name = dev?.displayName ?: ip
                _toastEvent.emit("Alerte: $name a dépassé son quota de données et a été bloqué par le Proxy !")
                vibrate(300)
                if (dev != null && !dev.isBlocked) {
                    toggleBlockDevice(dev)
                }
            }
        }

        // Start real-time traffic monitoring
        trafficMonitor.onTick = { rx, tx ->
            val blockedCount = _devices.value.count { it.isBlocked }
            val throttledCount = _devices.value.count { it.bandwidthLimitKbps != null && !it.isBlocked }

            _hotspotState.value = _hotspotState.value.copy(
                currentRxSpeedKbps = rx,
                currentTxSpeedKbps = tx,
                peakRxSpeedKbps = trafficMonitor.peakRxSpeed.value,
                peakTxSpeedKbps = trafficMonitor.peakTxSpeed.value,
                totalRxBytes = trafficMonitor.totalRxSession.value,
                totalTxBytes = trafficMonitor.totalTxSession.value,
                connectedClientsCount = _devices.value.count { it.isOnline },
                throttledClientsCount = throttledCount,
                blockedClientsCount = blockedCount
            )

            // Distribute speed & bytes among unblocked active devices
            updateDevicesTraffic(rx, tx)
        }
        trafficMonitor.start()

        // Check root status
        viewModelScope.launch {
            val rootAvailable = IptablesController.isRootAvailable()
            _hotspotState.value = _hotspotState.value.copy(isRootGranted = rootAvailable)
        }

        // Detect network interface & Hotspot state
        updateHotspotInterfaceAndState()

        // Perform initial real scan
        scanNetwork()

        // Start background auto-scan loop (every 6 seconds) to detect real connected devices immediately
        viewModelScope.launch {
            while (isActive) {
                delay(6000)
                updateHotspotInterfaceAndState()
                if (_hotspotState.value.isHotspotActive && !_hotspotState.value.isScanning) {
                    performRealScanSilent()
                }
            }
        }
    }

    fun updateHotspotInterfaceAndState() {
        val info = scanner.findHotspotInterface()
        val isActive = scanner.isHotspotActive(getApplication())
        val subnet = info.ipAddress.substringBeforeLast('.') + ".0/24"
        _hotspotState.value = _hotspotState.value.copy(
            interfaceName = info.name,
            gatewayIp = info.ipAddress,
            subnetMask = "255.255.255.0",
            isHotspotActive = isActive
        )
    }

    fun scanNetwork() {
        viewModelScope.launch {
            _hotspotState.value = _hotspotState.value.copy(isScanning = true)
            updateHotspotInterfaceAndState()

            val baseSubnet = _hotspotState.value.gatewayIp.substringBeforeLast('.') + "."
            val myIp = _hotspotState.value.gatewayIp

            val scanned = withContext(Dispatchers.IO) {
                scanner.scanSubnet(baseSubnet, myIp)
            }

            // Real devices ONLY (No sample devices!)
            val merged = mergeWithSavedRules(scanned)
            _devices.value = merged

            _hotspotState.value = _hotspotState.value.copy(
                isScanning = false,
                connectedClientsCount = merged.count { it.isOnline },
                throttledClientsCount = merged.count { it.bandwidthLimitKbps != null && !it.isBlocked },
                blockedClientsCount = merged.count { it.isBlocked }
            )

            if (merged.isNotEmpty()) {
                _toastEvent.emit("${merged.size} appareil(s) connecté(s) détecté(s)")
            } else if (_hotspotState.value.isHotspotActive) {
                _toastEvent.emit("Point d'accès actif - En attente de connexion d'appareils")
            } else {
                _toastEvent.emit("Point d'accès inactif - Veuillez activer le partage Wi-Fi")
            }
        }
    }

    private suspend fun performRealScanSilent() {
        val baseSubnet = _hotspotState.value.gatewayIp.substringBeforeLast('.') + "."
        val myIp = _hotspotState.value.gatewayIp

        val scanned = withContext(Dispatchers.IO) {
            scanner.scanSubnet(baseSubnet, myIp)
        }

        val previousDevices = _devices.value.associateBy { it.ipAddress }
        val merged = scanned.map { scannedDev ->
            val prev = previousDevices[scannedDev.ipAddress]
            if (prev != null) {
                scannedDev.copy(
                    isBlocked = prev.isBlocked,
                    customNickname = prev.customNickname,
                    bandwidthLimitKbps = prev.bandwidthLimitKbps,
                    quotaBytes = prev.quotaBytes,
                    bytesUsed = prev.bytesUsed,
                    firstSeenTimestamp = prev.firstSeenTimestamp
                )
            } else {
                mergeSingleDeviceRules(scannedDev)
            }
        }

        _devices.value = merged
        _hotspotState.value = _hotspotState.value.copy(
            connectedClientsCount = merged.count { it.isOnline },
            throttledClientsCount = merged.count { it.bandwidthLimitKbps != null && !it.isBlocked },
            blockedClientsCount = merged.count { it.isBlocked }
        )
    }

    private fun mergeSingleDeviceRules(dev: ConnectedDevice): ConnectedDevice {
        val blockedSet = prefs.getStringSet("blocked_ips", emptySet()) ?: emptySet()
        val nicknames = prefs.getStringSet("nicknames", emptySet()) ?: emptySet()
        val nickMap = nicknames.associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }

        val isBlocked = blockedSet.contains(dev.ipAddress)
        val customNick = nickMap[dev.ipAddress] ?: dev.customNickname
        val savedLimit = prefs.getInt("limit_${dev.ipAddress}", -1).takeIf { it > 0 }
        val savedQuota = prefs.getLong("quota_${dev.ipAddress}", -1L).takeIf { it > 0 }

        if (isBlocked) {
            HotspotVpnService.blockIp(dev.ipAddress)
            HotspotProxyService.blockIp(dev.ipAddress)
        }
        if (savedLimit != null) {
            HotspotVpnService.setRateLimit(dev.ipAddress, savedLimit)
            HotspotProxyService.setBandwidthLimit(dev.ipAddress, savedLimit)
        }
        if (savedQuota != null) {
            HotspotProxyService.setQuota(dev.ipAddress, savedQuota)
        }

        return dev.copy(
            isBlocked = isBlocked,
            customNickname = customNick,
            bandwidthLimitKbps = savedLimit,
            quotaBytes = savedQuota
        )
    }

    private fun mergeWithSavedRules(scannedList: List<ConnectedDevice>): List<ConnectedDevice> {
        val blockedSet = prefs.getStringSet("blocked_ips", emptySet()) ?: emptySet()
        val nicknames = prefs.getStringSet("nicknames", emptySet()) ?: emptySet()
        val nickMap = nicknames.associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }

        return scannedList.map { dev ->
            val isBlocked = blockedSet.contains(dev.ipAddress) || dev.isBlocked
            val customNick = nickMap[dev.ipAddress] ?: dev.customNickname
            val savedLimit = prefs.getInt("limit_${dev.ipAddress}", -1).takeIf { it > 0 } ?: dev.bandwidthLimitKbps
            val savedQuota = prefs.getLong("quota_${dev.ipAddress}", -1L).takeIf { it > 0 } ?: dev.quotaBytes

            // Sync with VPN & Proxy Services
            if (isBlocked) {
                HotspotVpnService.blockIp(dev.ipAddress)
                HotspotProxyService.blockIp(dev.ipAddress)
            }
            if (savedLimit != null) {
                HotspotVpnService.setRateLimit(dev.ipAddress, savedLimit)
                HotspotProxyService.setBandwidthLimit(dev.ipAddress, savedLimit)
            }
            if (savedQuota != null) {
                HotspotProxyService.setQuota(dev.ipAddress, savedQuota)
            }

            dev.copy(
                isBlocked = isBlocked,
                customNickname = customNick,
                bandwidthLimitKbps = savedLimit,
                quotaBytes = savedQuota
            )
        }
    }

    val dnsLogs = DnsFilterServer.dnsLogs
    val blockAds = DnsFilterServer.blockAds
    val blockSocial = DnsFilterServer.blockSocial
    val blockAdult = DnsFilterServer.blockAdult
    val customBlacklist = DnsFilterServer.customBlacklist

    private fun loadSavedRules() {
        val blockedSet = prefs.getStringSet("blocked_ips", emptySet()) ?: emptySet()
        blockedSet.forEach { ip ->
            HotspotVpnService.blockIp(ip)
            HotspotProxyService.blockIp(ip)
        }

        // Load DNS filtering preferences
        DnsFilterServer.blockAds.value = prefs.getBoolean("dns_block_ads", true)
        DnsFilterServer.blockSocial.value = prefs.getBoolean("dns_block_social", false)
        DnsFilterServer.blockAdult.value = prefs.getBoolean("dns_block_adult", false)

        val blacklistSet = prefs.getStringSet("dns_blacklist", emptySet()) ?: emptySet()
        blacklistSet.forEach { DnsFilterServer.addDomainToBlacklist(it) }
    }

    fun toggleBlockAds() {
        val newVal = !DnsFilterServer.blockAds.value
        DnsFilterServer.blockAds.value = newVal
        prefs.edit().putBoolean("dns_block_ads", newVal).apply()
        viewModelScope.launch {
            _toastEvent.emit(if (newVal) "Blocage des publicités et traqueurs activé" else "Blocage des pubs désactivé")
        }
    }

    fun toggleBlockSocial() {
        val newVal = !DnsFilterServer.blockSocial.value
        DnsFilterServer.blockSocial.value = newVal
        prefs.edit().putBoolean("dns_block_social", newVal).apply()
        viewModelScope.launch {
            _toastEvent.emit(if (newVal) "Blocage des réseaux sociaux activé (TikTok, Insta, etc.)" else "Réseaux sociaux autorisés")
        }
    }

    fun toggleBlockAdult() {
        val newVal = !DnsFilterServer.blockAdult.value
        DnsFilterServer.blockAdult.value = newVal
        prefs.edit().putBoolean("dns_block_adult", newVal).apply()
        viewModelScope.launch {
            _toastEvent.emit(if (newVal) "Contrôle parental activé (Sites adultes bloqués)" else "Contrôle parental désactivé")
        }
    }

    fun addCustomBlacklistDomain(domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isBlank()) return
        DnsFilterServer.addDomainToBlacklist(clean)
        val current = prefs.getStringSet("dns_blacklist", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add(clean)
        prefs.edit().putStringSet("dns_blacklist", current).apply()
        viewModelScope.launch {
            _toastEvent.emit("Domaine '$clean' ajouté à la liste noire DNS")
        }
    }

    fun removeCustomBlacklistDomain(domain: String) {
        val clean = domain.trim().lowercase()
        DnsFilterServer.removeDomainFromBlacklist(clean)
        val current = prefs.getStringSet("dns_blacklist", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.remove(clean)
        prefs.edit().putStringSet("dns_blacklist", current).apply()
        viewModelScope.launch {
            _toastEvent.emit("Domaine '$clean' retiré de la liste noire")
        }
    }

    fun clearDnsLogs() {
        DnsFilterServer.clearLogs()
    }

    fun toggleProxyService(context: Context) {
        val running = HotspotProxyService.isProxyRunning.value
        if (running) {
            HotspotProxyService.stopService(context)
            viewModelScope.launch { _toastEvent.emit("Serveur Proxy Passerelle arrêté") }
        } else {
            HotspotProxyService.startService(context)
            viewModelScope.launch { _toastEvent.emit("Serveur Proxy Passerelle démarré sur le port 8282") }
        }
    }

    fun toggleBlockDevice(device: ConnectedDevice) {
        val newBlockedState = !device.isBlocked
        val updated = _devices.value.map {
            if (it.id == device.id) it.copy(isBlocked = newBlockedState) else it
        }
        _devices.value = updated

        // Update VPN and Proxy services
        if (newBlockedState) {
            HotspotVpnService.blockIp(device.ipAddress)
            HotspotProxyService.blockIp(device.ipAddress)
        } else {
            HotspotVpnService.unblockIp(device.ipAddress)
            HotspotProxyService.unblockIp(device.ipAddress)
        }

        // Save to prefs
        val blockedSet = prefs.getStringSet("blocked_ips", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (newBlockedState) {
            blockedSet.add(device.ipAddress)
        } else {
            blockedSet.remove(device.ipAddress)
        }
        prefs.edit().putStringSet("blocked_ips", blockedSet).apply()

        // Haptic feedback
        vibrate(if (newBlockedState) 120 else 50)

        // Show toast
        viewModelScope.launch {
            _toastEvent.emit(
                if (newBlockedState) "Appareil ${device.displayName} bloqué par le Proxy"
                else "Appareil ${device.displayName} débloqué"
            )
        }

        _hotspotState.value = _hotspotState.value.copy(
            blockedClientsCount = updated.count { it.isBlocked },
            throttledClientsCount = updated.count { it.bandwidthLimitKbps != null && !it.isBlocked }
        )
    }

    fun setBandwidthLimit(device: ConnectedDevice, limitKbps: Int?) {
        val updated = _devices.value.map {
            if (it.id == device.id) it.copy(bandwidthLimitKbps = limitKbps) else it
        }
        _devices.value = updated

        // Update VPN and Proxy Services
        HotspotVpnService.setRateLimit(device.ipAddress, limitKbps)
        HotspotProxyService.setBandwidthLimit(device.ipAddress, limitKbps)

        // Save to prefs
        if (limitKbps != null && limitKbps > 0) {
            prefs.edit().putInt("limit_${device.ipAddress}", limitKbps).apply()
        } else {
            prefs.edit().remove("limit_${device.ipAddress}").apply()
        }

        vibrate(40)

        viewModelScope.launch {
            _toastEvent.emit(
                if (limitKbps != null) "Vitesse de ${device.displayName} bridée à $limitKbps Ko/s (Proxy)"
                else "Limite de vitesse désactivée pour ${device.displayName}"
            )
        }

        _hotspotState.value = _hotspotState.value.copy(
            throttledClientsCount = updated.count { it.bandwidthLimitKbps != null && !it.isBlocked }
        )
    }

    fun setQuota(device: ConnectedDevice, quotaBytes: Long?) {
        val updated = _devices.value.map {
            if (it.id == device.id) it.copy(quotaBytes = quotaBytes) else it
        }
        _devices.value = updated

        // Update Proxy Service
        HotspotProxyService.setQuota(device.ipAddress, quotaBytes)

        if (quotaBytes != null && quotaBytes > 0) {
            prefs.edit().putLong("quota_${device.ipAddress}", quotaBytes).apply()
        } else {
            prefs.edit().remove("quota_${device.ipAddress}").apply()
        }

        viewModelScope.launch {
            _toastEvent.emit(
                if (quotaBytes != null) "Quota de ${quotaBytes / 1_000_000} Mo défini pour ${device.displayName}"
                else "Quota retiré pour ${device.displayName}"
            )
        }
    }

    fun setCustomNickname(device: ConnectedDevice, nickname: String) {
        val updated = _devices.value.map {
            if (it.id == device.id) it.copy(customNickname = nickname.trim().takeIf { s -> s.isNotEmpty() }) else it
        }
        _devices.value = updated

        val nicknames = prefs.getStringSet("nicknames", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        nicknames.removeAll { it.startsWith("${device.ipAddress}=") }
        if (nickname.isNotBlank()) {
            nicknames.add("${device.ipAddress}=$nickname")
        }
        prefs.edit().putStringSet("nicknames", nicknames).apply()

        viewModelScope.launch {
            _toastEvent.emit("Nom modifié en: $nickname")
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: DeviceFilter) {
        _selectedFilter.value = filter
    }

    fun executeRootCommand(cmd: String) {
        viewModelScope.launch {
            _rootExecutionLog.value = "Exécution : $cmd...\n"
            val result = IptablesController.executeRootCommand(cmd)
            _rootExecutionLog.value = if (result.isSuccess) {
                "[Succès (Code ${result.exitCode})]\n${result.output}"
            } else {
                "[Échec (Code ${result.exitCode})]\n${result.output}"
            }
            _toastEvent.emit(if (result.isSuccess) "Commande root exécutée !" else "Erreur commande root")
        }
    }

    private fun updateDevicesTraffic(rxSpeed: Float, txSpeed: Float) {
        val activeList = _devices.value.filter { it.isOnline && !it.isBlocked }
        if (activeList.isEmpty()) return

        val activeCount = activeList.size
        val shareRx = rxSpeed / activeCount
        val shareTx = txSpeed / activeCount

        val updated = _devices.value.map { dev ->
            if (dev.isBlocked) {
                dev.copy(currentSpeedKbps = 0f)
            } else if (dev.isOnline) {
                var speed = shareRx + shareTx
                dev.bandwidthLimitKbps?.let { cap ->
                    if (speed > cap) speed = cap.toFloat()
                }

                val addedBytes = (speed * 1024).toLong()
                val proxyBytes = HotspotProxyService.ipBytesUsedMap[dev.ipAddress] ?: 0L
                val totalBytes = kotlin.math.max(dev.bytesUsed + addedBytes, proxyBytes)

                val shouldAutoBlock = dev.quotaBytes != null && totalBytes >= dev.quotaBytes && !dev.isBlocked
                if (shouldAutoBlock) {
                    viewModelScope.launch {
                        _toastEvent.emit("Alerte: ${dev.displayName} a dépassé son quota (${dev.quotaBytes / 1_000_000} Mo) et a été bloqué automatiquement !")
                        vibrate(300)
                    }
                    HotspotVpnService.blockIp(dev.ipAddress)
                }

                dev.copy(
                    currentSpeedKbps = speed,
                    bytesUsed = totalBytes,
                    isBlocked = if (shouldAutoBlock) true else dev.isBlocked
                )
            } else {
                dev
            }
        }
        _devices.value = updated
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        trafficMonitor.stop()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        try {
            getApplication<Application>().unregisterReceiver(apStateReceiver)
        } catch (_: Exception) {}
    }
}
