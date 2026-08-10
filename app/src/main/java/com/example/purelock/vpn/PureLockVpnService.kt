package com.example.purelock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.purelock.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class PureLockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var expiryCheckJob: Job? = null

    private lateinit var sessionManager: SessionManager
    private val dnsProxy = DnsPacketProxy()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (ACTION_STOP_TEST == action) {
            sessionManager.stopTestSession()
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        // Check if session is active according to state
        val state = sessionManager.state.value
        if (!state.isProtectionActive) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification(state.isTestActive)
        startVpn()
        startExpiryMonitor()

        return START_STICKY
    }

    private fun startForegroundServiceNotification(isTestMode: Boolean) {
        val channelId = "purelock_vpn_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PureLock DNS Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when PureLock DNS filtering is active."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PureLock Protection Active")
            .setContentText(
                if (isTestMode) "Test Mode Active (DNS Filtering)"
                else "Strict Mode Active — Adult Content Blocked"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isTestMode) {
            val stopTestIntent = Intent(this, PureLockVpnService::class.java).apply {
                action = ACTION_STOP_TEST
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopTestIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "STOP TEST PROTECTION", stopPendingIntent)
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    @Synchronized
    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("PureLock DNS Protection")
                .addAddress("10.200.0.1", 32)
                .addDnsServer("10.200.0.2")

            // DNS-only filtering: route ONLY DNS IP addresses into the TUN interface!
            val dnsRoutes = listOf(
                "10.200.0.2",
                "185.228.168.168", // CleanBrowsing Primary
                "185.228.169.168", // CleanBrowsing Secondary
                "1.1.1.3",         // Cloudflare Families Primary
                "1.0.0.3",         // Cloudflare Families Secondary
                "8.8.8.8",         // Google DNS
                "8.8.4.4",
                "1.1.1.1",         // Unfiltered Cloudflare
                "1.0.0.1",
                "9.9.9.9"          // Quad9
            )

            for (dnsIp in dnsRoutes) {
                try {
                    builder.addRoute(dnsIp, 32)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed adding DNS route: $dnsIp", e)
                }
            }

            builder.setMtu(1500)

            val pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "VpnService.Builder.establish() returned null")
                return
            }
            vpnInterface = pfd
            isRunning = true

            vpnThread = Thread({ processVpnTraffic(pfd) }, "PureLockVpnThread").apply {
                start()
            }

            Log.i(TAG, "PureLock DNS VPN interface established successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting PureLock VPN", e)
            stopVpn()
        }
    }

    private fun processVpnTraffic(pfd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32768)

        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                val length = inputStream.read(buffer)
                if (length <= 0) {
                    Thread.sleep(10)
                    continue
                }

                val parsedPacket = IpPacketUtils.parseUdpPacket(buffer, length)
                if (parsedPacket != null && parsedPacket.dstPort == 53) {
                    // Forward DNS query over protected socket
                    val dnsResult = dnsProxy.forwardDnsQuery(parsedPacket.payload) { socket ->
                        protect(socket)
                    }

                    if (dnsResult != null) {
                        // Build UDP response packet
                        val responsePacket = IpPacketUtils.buildUdpResponsePacket(
                            srcIp = parsedPacket.dstIp,
                            dstIp = parsedPacket.srcIp,
                            srcPort = parsedPacket.dstPort,
                            dstPort = parsedPacket.srcPort,
                            payload = dnsResult.responsePayload
                        )

                        outputStream.write(responsePacket)
                        outputStream.flush()

                        sessionManager.updateStatus(
                            provider = dnsResult.providerName,
                            status = "Working",
                            dns = dnsResult.serverIp
                        )
                    }
                }
            } catch (e: IOException) {
                if (!isRunning) break
                Log.w(TAG, "IOException reading/writing VPN TUN descriptor", e)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing packet", e)
            }
        }
    }

    private fun startExpiryMonitor() {
        expiryCheckJob?.cancel()
        expiryCheckJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val expired = sessionManager.checkAndCleanExpiredSession()
                if (expired) {
                    val state = sessionManager.state.value
                    if (!state.isProtectionActive) {
                        Log.i(TAG, "Protection session expired. Stopping VPN.")
                        stopVpn()
                        stopSelf()
                        break
                    }
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val isCellular = connectivityManager?.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    val networkType = if (isCellular) "Mobile Data" else "Wi-Fi"
                    Log.i(TAG, "Network available: $networkType")
                    sessionManager.updateStatus(
                        provider = sessionManager.state.value.activeProvider,
                        status = "Connected ($networkType)",
                        dns = sessionManager.state.value.activeDnsServer
                    )
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Network lost")
                    sessionManager.updateStatus(
                        provider = sessionManager.state.value.activeProvider,
                        status = "Reconnecting...",
                        dns = sessionManager.state.value.activeDnsServer
                    )
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering NetworkCallback", e)
        }
    }

    @Synchronized
    private fun stopVpn() {
        isRunning = false
        vpnThread?.interrupt()
        vpnThread = null

        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null

        expiryCheckJob?.cancel()
        expiryCheckJob = null

        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (ignored: Exception) {}
        }
        networkCallback = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system/user.")
        stopVpn()
        super.onRevoke()
    }

    companion object {
        private const val TAG = "PureLockVpnService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.purelock.action.START"
        const val ACTION_STOP_TEST = "com.example.purelock.action.STOP_TEST"

        fun startService(context: Context) {
            val intent = Intent(context, PureLockVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTestService(context: Context) {
            val intent = Intent(context, PureLockVpnService::class.java).apply {
                action = ACTION_STOP_TEST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
