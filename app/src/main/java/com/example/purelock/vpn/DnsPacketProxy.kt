package com.example.purelock.vpn

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class FilteredDnsServer(
    val providerName: String,
    val ipAddress: String,
    val isPrimary: Boolean
)

data class DnsResult(
    val responsePayload: ByteArray,
    val providerName: String,
    val serverIp: String
)

class DnsPacketProxy {

    private val filteredServers = listOf(
        FilteredDnsServer("CleanBrowsing Family Filter", "185.228.168.168", isPrimary = true),
        FilteredDnsServer("CleanBrowsing Family Filter", "185.228.169.168", isPrimary = false),
        FilteredDnsServer("Cloudflare 1.1.1.1 Families", "1.1.1.3", isPrimary = true),
        FilteredDnsServer("Cloudflare 1.1.1.1 Families", "1.0.0.3", isPrimary = false)
    )

    fun forwardDnsQuery(
        queryPayload: ByteArray,
        protectSocket: (DatagramSocket) -> Boolean
    ): DnsResult? {
        val socket = try {
            DatagramSocket().apply {
                soTimeout = 1500 // 1.5s timeout for fast failover
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DatagramSocket", e)
            return null
        }

        if (!protectSocket(socket)) {
            Log.w(TAG, "Failed to protect DatagramSocket with VpnService.protect()")
        }

        try {
            for (server in filteredServers) {
                try {
                    val destAddr = InetAddress.getByName(server.ipAddress)
                    val sendPacket = DatagramPacket(queryPayload, queryPayload.size, destAddr, 53)
                    socket.send(sendPacket)

                    val rxBuffer = ByteArray(2048)
                    val receivePacket = DatagramPacket(rxBuffer, rxBuffer.size)
                    socket.receive(receivePacket)

                    val responseLen = receivePacket.length
                    if (responseLen > 0) {
                        val responsePayload = ByteArray(responseLen)
                        System.arraycopy(rxBuffer, 0, responsePayload, 0, responseLen)
                        return DnsResult(
                            responsePayload = responsePayload,
                            providerName = server.providerName,
                            serverIp = server.ipAddress
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DNS query to ${server.providerName} (${server.ipAddress}) failed/timed out, trying fallback", e)
                }
            }
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }

        Log.e(TAG, "All filtered DNS resolvers failed!")
        return null
    }

    companion object {
        private const val TAG = "DnsPacketProxy"
    }
}
