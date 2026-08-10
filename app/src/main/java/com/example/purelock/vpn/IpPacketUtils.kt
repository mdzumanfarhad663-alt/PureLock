package com.example.purelock.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

object IpPacketUtils {

    data class ParsedUdpPacket(
        val srcIp: InetAddress,
        val dstIp: InetAddress,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    )

    fun parseUdpPacket(buffer: ByteArray, length: Int): ParsedUdpPacket? {
        if (length < 28) return null // 20 bytes IP + 8 bytes UDP minimum

        // Check IPv4 (version = 4)
        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null

        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        // Protocol check (17 = UDP)
        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != 17) return null

        // Source and Destination IP
        val srcIpBytes = ByteArray(4)
        val dstIpBytes = ByteArray(4)
        System.arraycopy(buffer, 12, srcIpBytes, 0, 4)
        System.arraycopy(buffer, 16, dstIpBytes, 0, 4)

        val srcIp = InetAddress.getByAddress(srcIpBytes)
        val dstIp = InetAddress.getByAddress(dstIpBytes)

        // UDP Ports
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)

        val payloadLen = udpLen - 8
        if (payloadLen <= 0 || ihl + 8 + payloadLen > length) return null

        val payload = ByteArray(payloadLen)
        System.arraycopy(buffer, ihl + 8, payload, 0, payloadLen)

        return ParsedUdpPacket(srcIp, dstIp, srcPort, dstPort, payload)
    }

    fun buildUdpResponsePacket(
        srcIp: InetAddress,
        dstIp: InetAddress,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)
        val bb = ByteBuffer.wrap(packet)

        // IP Header (20 bytes)
        bb.put(0x45.toByte()) // Version 4, IHL 5
        bb.put(0x00.toByte()) // TOS
        bb.putShort(totalLength.toShort()) // Total Length
        bb.putShort(0x1234.toShort()) // Identification
        bb.putShort(0x4000.toShort()) // Don't Fragment
        bb.put(64.toByte()) // TTL
        bb.put(17.toByte()) // Protocol UDP
        bb.putShort(0.toShort()) // Checksum placeholder

        val srcIpBytes = srcIp.address
        val dstIpBytes = dstIp.address
        bb.put(srcIpBytes)
        bb.put(dstIpBytes)

        // Calculate & write IP checksum
        val checksum = calculateChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()

        // UDP Header (8 bytes)
        bb.position(20)
        bb.putShort(srcPort.toShort())
        bb.putShort(dstPort.toShort())
        bb.putShort((8 + payload.size).toShort())
        bb.putShort(0.toShort()) // UDP Checksum (0 in IPv4 = ignored)

        // Payload
        bb.put(payload)

        return packet
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word.toLong()
            i += 2
        }
        if (i < offset + length) {
            val word = (data[i].toInt() and 0xFF) shl 8
            sum += word.toLong()
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}
