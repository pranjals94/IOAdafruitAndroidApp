package com.example.mqttapp

import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timer

class mqtt_utils {
     val hostname = "io.adafruit.com"
     val port = 1883
     val username = "your_adafruit_username"
     val aioKey = "your_adafruit_key" // adafruit keeps refreshing this key after every few days , need to keep changing here also
     val topic = "$username/feeds/test"

    // by using different clientID_subscriber we can subscribe to a topic using multiple clients
     val clientID_subscriber = "myClient_subscriber"
     val clientId_publish = "clientId_publish"

     fun encodeString(str: String): ByteArray {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        return byteArrayOf((bytes.size shr 8).toByte(), (bytes.size and 0xFF).toByte()) + bytes
    }

     fun encodeLength(length: Int): ByteArray {
        var value = length
        val encoded = mutableListOf<Byte>()
        do {
            var byte = (value % 128).toByte()
            value /= 128
            if (value > 0) byte = (byte.toInt() or 0x80).toByte()
            encoded.add(byte)
        } while (value > 0)
        return encoded.toByteArray()
    }

     fun buildConnectPacket(clientId: String, username: String, password: String): ByteArray {
        val protocolName = encodeString("MQTT")
        val protocolLevel = byteArrayOf(0x04) // MQTT 3.1.1
        val connectFlags = byteArrayOf(0b1100_0000.toByte()) // username & password
        val keepAlive = byteArrayOf(0x00, 0x3C) // 60 seconds

        val payload = encodeString(clientId) + encodeString(username) + encodeString(password)
        val variableHeader = protocolName + protocolLevel + connectFlags + keepAlive
        val remainingLength = encodeLength(variableHeader.size + payload.size)

        return byteArrayOf(0x10) + remainingLength + variableHeader + payload
    }

     fun buildPublishPacket(topic: String, message: String): ByteArray {
        val topicBytes = encodeString(topic)
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val remainingLength = encodeLength(topicBytes.size + messageBytes.size)
        //first byte 0x30 means qos =0;0x32 means qos =1; 0x34 means qos =2; google for mor details
        return byteArrayOf(0x30) + remainingLength + topicBytes + messageBytes
    }

     fun buildSubscribePacket(packetId: Int, topic: String): ByteArray {
        // Step 1: Encode the topic as a UTF-8 string with a length prefix
        val topicBytes = encodeString(topic)


        // Step 2: Fixed Header
        // 0x80 for SUBSCRIBE packet type, with reserved flag bits
        val fixedHeader = byteArrayOf(0x82.toByte())

        // Step 3: Remaining Length
        // Remaining length is calculated as the sum of the length of the variable header and payload
        val remainingLength =
            encodeLength(2 + topicBytes.size + 1) // 2 bytes for packetId, topic length, and QoS byte

        // Step 4: Packet Identifier (2-byte, big-endian)
        val packetIdBytes = byteArrayOf(
            (packetId shr 8).toByte(),  // High byte of packet ID
            (packetId and 0xFF).toByte() // Low byte of packet ID
        )

        // Step 5: Payload (Topic + QoS level)
        // Topic is encoded in the previous step, and QoS is provided by the argument
        //val qosByte = byteArrayOf(0x01)  // QoS 1
        val payload = topicBytes + byteArrayOf(0x01) // byteArrayOf(0x01) qos
        // Step 6: Combine everything
        return fixedHeader + remainingLength + packetIdBytes + payload
    }

    //------------------------------connect and publish-------------------------
     fun connectAndPublish(list: SnapshotStateList<String>, message: String) {
        list.add("👉 Trying to connect (Publish)")
        val socket = SocketChannel.open()
        try {
            val buffer = ByteBuffer.allocate(1024)
            socket.configureBlocking(true)  // Blocking mode
            var isConnected = false
            CoroutineScope(Dispatchers.IO).launch { //custom time out (when hostname or port is wrong)
                delay(2000)
                if (!isConnected){
                    throw Exception("Connecting to $hostname failed.")
                }
            }
            socket.connect(InetSocketAddress(hostname, port))// blocking
            isConnected = true
            list.add("🔌 Connected to Adafruit IO")
            val connectPacket = buildConnectPacket(clientId_publish, username, aioKey)
            list.add("📤 Trying to log in $username")
            socket.write(ByteBuffer.wrap(connectPacket)) // blocking
            socket.read(buffer)// blocking
            buffer.flip()
            val connack = ByteArray(buffer.remaining())
            buffer.get(connack)
            list.add("📥 CONNACK: ${connack.joinToString(" ") { "%02x".format(it) }}")
            if(connack[connack.size-1] != 0x00.toByte()){ //check responnse code is 0x00 (authorized or not)
                list.add("❌ Unauthorized Access !")
                socket.close()
                return
            }
            list.add("🟢 Logged In.")
            val publishPacket = buildPublishPacket(topic, message)
            socket.write(ByteBuffer.wrap(publishPacket))
            list.add("📤 Publish Msg: $message")
            socket.read(buffer)
            buffer.flip()
            buffer.get(connack)
            list.add("📥 CONNACK: ${connack.joinToString(" ") { "%02x".format(it) }}")
            if(connack[connack.size-1] != 0x00.toByte()){ //check responnse code is 0x00 (authorized or not)
                list.add("❌ Publish Failed !")
            }else{
                list.add("🟢 Publish Success.")
            }
            socket.close()
        } catch (e: Exception) {
            list.add("❌ Publish Failed !, Error: ${e.message}")
            socket.close()
        }
    }

    //------------------------------connect and Subscribe------------------------
    fun connectAndSubscribe(list: SnapshotStateList<String>) {
        list.add("👉 Trying to connect (subscribe)")
        // AtomicBoolean for thread safety
        var KeepAlivePingRunning = AtomicBoolean(true)
        val socket = SocketChannel.open()
        try {
            var buffer = ByteBuffer.allocate(1024)
            socket.configureBlocking(true)  // Blocking mode
            var isConnected = false
            CoroutineScope(Dispatchers.IO).launch { //custom time out when hostname or port is wrong
                delay(2000)
                if (!isConnected){
                    KeepAlivePingRunning= AtomicBoolean(false)
                    socket.close()
                }
            }
            /*
             No response is returned from socket.connect(). Means the server does not respond any ack/massage.
             If the connection is established successfully, it returns no response and just completes the operation.
            If the connection cannot be made (e.g., the server is down, the port is closed, or there is no network),
            it throws an exception (e.g., ConnectException , after time out).
             It either completes successfully or throws an exception if the connection cannot be made.
             also it does not return any value. It is a void method.
             default time out is about 2 to 3 minutes, all time out settings
             eg socket.socket().soTimeout = 1000 //5 sec time out i made did not work
             */
            socket.connect(InetSocketAddress(hostname, port)) // try to connect (Blocking)
            isConnected = true
            list.add("👉 Connection established with ${hostname + port.toString() }")
            val connectPacket = buildConnectPacket(clientID_subscriber, username, aioKey)
            list.add("📥 Trying to log in using ${username}")
            /*
            val status = socket.write() returns an int value telling its status
             if (status < 0) { // negative indicates failed
                Log.e("MQTT", "🔌Faild to establish communication!")

                    list.add("🔌Failed to establish communication!")
                }
            }
            also the server will respond with ack/msgs (ByteArrey) that can be read by socket.read()
            */
            socket.write(ByteBuffer.wrap(connectPacket))

            socket.read(buffer)
            buffer.flip()
            /*
            ack authorization response from the adafruit is only 4 bytes
            20: Message Type (CONNACK)
            02: Remaining Length (2 bytes follow)
            00: Reserved field (always 0)
            00: Return Code (0x00 = Connection Accepted/Authorized) (0x05 - Connection Refused, not authorized, 0x01 - Connection Refused, unacceptable protocol version)
            */
            val connack = ByteArray(buffer.remaining())// connack1 is 4 bytes byteArrey
            buffer.get(connack) // copy those response 4 bytes to connack1
            list.add("📥 Log In response: ${connack.joinToString(" ") { "%02x".format(it) }}")
            list.add("📥 Authorization Response Code: ${String.format("%02x", connack[connack.size-1])}")// hex format

            if(connack[connack.size-1] != 0x00.toByte()){ //check responnse code is 0x00 (authorized or not)
                list.add("❌ Unauthorized Access !")
                KeepAlivePingRunning= AtomicBoolean(false)
                socket.close()
            }

            // Send SUBSCRIBE
            val subscribePacket = buildSubscribePacket(1, topic)
            socket.write(ByteBuffer.wrap(subscribePacket))
            list.add("📤 SUBSCRIBE packet sent")

            // Read SUBACK
            buffer.clear()
            socket.read(buffer)
            buffer.flip()
            val suback = ByteArray(buffer.remaining())
            buffer.get(suback)
            list.add("📥 SUBACK: ${suback.joinToString(" ") { "%02x".format(it) }}")

            //keep Alive ping request broker server replies 2 byte data, byte[0] is PINGRES(0xD0) also contains the 5 bit flags, byte[1] is Remaining Length (0x00)
//                KeepAlivePingRunning = AtomicBoolean(true)
            startPingLoop(socket,list, KeepAlivePingRunning)

            buffer.clear()
            list.add("👉 Subscribed: Listening...")
            // Listen for incoming PUBLISH messages
            while (true) {
                buffer.clear()
                val readBytes = socket.read(buffer)
                list.add("👉 Rcv Buffer Length: ${readBytes.toString()}")
                if (readBytes > 0) {
                    buffer.flip()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    list.add("📥 Raw hex Data: ${data.joinToString(" ") { "%02x".format(it) }}")
                    list.add("⚠️ Message: ${parsePublish(data)}")
                } else if (readBytes < 0) {
                    KeepAlivePingRunning= AtomicBoolean(false)
                    socket.close()
                    list.add("📥 (subs) Connection Closed By server.")
                }
            }
        } catch (e: Exception) {
            KeepAlivePingRunning= AtomicBoolean(false)
            socket.close()
            list.add("❌ Subscribe Failed ! Error: ${e.message}")
        }
    }
    //keep Alive ping request
     fun buildPingReqPacket(): ByteArray {
        return byteArrayOf(0xC0.toByte(), 0x00)
    }

     fun startPingLoop(socket: SocketChannel, list: SnapshotStateList<String>, isRunning: AtomicBoolean) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                while (isRunning.get()) {
                    val pingPacket = buildPingReqPacket()
                    delay(80_000) // Wait 80 seconds, 1min20sec, adafruit io expects a keep alive ping request before (1 min30 secs approx)
                    socket.write(ByteBuffer.wrap(pingPacket))
                    list.add("📤 keep alive PINGREQ Sent.")
                }
            } catch (e: Exception) {
                list.add("❌ PING loop stopped: ${e.message}")
            }
        }
    }



     fun parsePublish(data: ByteArray): String {
        var payload = ""
        if (data.isEmpty()) return "Null"
        val header = data[0].toInt() and 0xF0
        if (header == 0x30) { //check if the msg received from the broker is of PUBLISH type.
            val topicLength = (data[2].toInt() shl 8) or data[3].toInt()
            val topic = String(data, 4, topicLength, StandardCharsets.UTF_8)
            val payloadStart = 4 + topicLength
            payload = String(data, payloadStart, data.size - payloadStart, StandardCharsets.UTF_8)
        }
        return payload
    }

}
