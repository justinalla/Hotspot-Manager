package com.example.service

import android.net.TrafficStats
import com.example.model.TrafficSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class TrafficMonitor(private val scope: CoroutineScope) {

    private var monitoringJob: Job? = null
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTimestamp = 0L

    private val maxSamples = 30
    private val samplesQueue = ArrayDeque<TrafficSample>(maxSamples)

    private val _currentRxSpeed = MutableStateFlow(0f)
    val currentRxSpeed = _currentRxSpeed.asStateFlow()

    private val _currentTxSpeed = MutableStateFlow(0f)
    val currentTxSpeed = _currentTxSpeed.asStateFlow()

    private val _peakRxSpeed = MutableStateFlow(0f)
    val peakRxSpeed = _peakRxSpeed.asStateFlow()

    private val _peakTxSpeed = MutableStateFlow(0f)
    val peakTxSpeed = _peakTxSpeed.asStateFlow()

    private val _trafficHistory = MutableStateFlow<List<TrafficSample>>(emptyList())
    val trafficHistory = _trafficHistory.asStateFlow()

    private val _totalRxSession = MutableStateFlow(0L)
    val totalRxSession = _totalRxSession.asStateFlow()

    private val _totalTxSession = MutableStateFlow(0L)
    val totalTxSession = _totalTxSession.asStateFlow()

    var onTick: ((rxSpeed: Float, txSpeed: Float) -> Unit)? = null

    fun start() {
        if (monitoringJob != null && monitoringJob?.isActive == true) return

        lastRxBytes = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        lastTxBytes = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        lastTimestamp = System.currentTimeMillis()

        // Seed initial history
        if (samplesQueue.isEmpty()) {
            for (i in 0 until 15) {
                samplesQueue.add(TrafficSample(System.currentTimeMillis() - (15 - i) * 1000, 0f, 0f))
            }
            _trafficHistory.value = samplesQueue.toList()
        }

        monitoringJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                measureTick()
            }
        }
    }

    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private fun measureTick() {
        val now = System.currentTimeMillis()
        val currentRx = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        val currentTx = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L

        val elapsedSec = (now - lastTimestamp).coerceAtLeast(1) / 1000f

        var rxSpeed = 0f
        var txSpeed = 0f

        if (lastRxBytes > 0 && currentRx >= lastRxBytes) {
            val deltaRx = currentRx - lastRxBytes
            rxSpeed = (deltaRx / 1024f) / elapsedSec // KB/s
            _totalRxSession.value += deltaRx
        }
        if (lastTxBytes > 0 && currentTx >= lastTxBytes) {
            val deltaTx = currentTx - lastTxBytes
            txSpeed = (deltaTx / 1024f) / elapsedSec // KB/s
            _totalTxSession.value += deltaTx
        }

        // Apply smoothing or minimum noise threshold
        if (rxSpeed < 0.1f) rxSpeed = 0f
        if (txSpeed < 0.1f) txSpeed = 0f

        _currentRxSpeed.value = rxSpeed
        _currentTxSpeed.value = txSpeed

        if (rxSpeed > _peakRxSpeed.value) _peakRxSpeed.value = rxSpeed
        if (txSpeed > _peakTxSpeed.value) _peakTxSpeed.value = txSpeed

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestamp = now

        val sample = TrafficSample(now, rxSpeed, txSpeed)
        synchronized(samplesQueue) {
            if (samplesQueue.size >= maxSamples) {
                samplesQueue.removeFirst()
            }
            samplesQueue.addLast(sample)
            _trafficHistory.value = samplesQueue.toList()
        }

        onTick?.invoke(rxSpeed, txSpeed)
    }

    /**
     * Injects synthetic variance if running on an emulator where TrafficStats is static,
     * maintaining lively telemetry while honoring real stats.
     */
    fun injectSimulatedTrafficPulse(deltaRxKbps: Float, deltaTxKbps: Float) {
        val now = System.currentTimeMillis()
        val rx = (_currentRxSpeed.value + deltaRxKbps).coerceAtLeast(0f)
        val tx = (_currentTxSpeed.value + deltaTxKbps).coerceAtLeast(0f)
        _currentRxSpeed.value = rx
        _currentTxSpeed.value = tx
        if (rx > _peakRxSpeed.value) _peakRxSpeed.value = rx
        if (tx > _peakTxSpeed.value) _peakTxSpeed.value = tx

        val sample = TrafficSample(now, rx, tx)
        synchronized(samplesQueue) {
            if (samplesQueue.size >= maxSamples) {
                samplesQueue.removeFirst()
            }
            samplesQueue.addLast(sample)
            _trafficHistory.value = samplesQueue.toList()
        }
    }
}
