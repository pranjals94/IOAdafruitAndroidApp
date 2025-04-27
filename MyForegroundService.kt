package com.example.mqttapp
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class MyForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "my_service_channel"

    companion object {
        var isServiceRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        isServiceRunning = true // ✅ set running true when service starts

        serviceScope.launch {
            connectAndSubscribe()
            // ❌ NO stopSelf() here unless you want to manually stop the service.
            stopSelf() // if u want to stop the service after above code finishes
            isServiceRunning = true // ✅ set running true when service starts
        }


        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Io.Adafruit.Com")
            .setContentText("🟢 Subscribed service Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false // set running false when service starts
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun sendNotification(title: String, message: String) {
        val localChannelId = "local_channel_id"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                localChannelId,
                "Local Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(soundUri, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, localChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(2001, notification)
    }

    private suspend fun connectAndSubscribe() {
        val hostname = "io.adafruit.com"
        val port = 1883
        val username = "yourUsername"
        val aioKey = "yourKey"
        val topic = "$username/feeds/test"
        val clientID_subscriber = "cliend_ID_background_service"

        val KeepAlivePingRunning = AtomicBoolean(true)
        val socket = SocketChannel.open()

        try {
            var buffer = ByteBuffer.allocate(1024)
            socket.configureBlocking(true)
            var isConnected = false

            // 🛡 Timeout logic using serviceScope
            serviceScope.launch {
                delay(2000)
                if (!isConnected) {
                    KeepAlivePingRunning.set(false)
                    socket.close()
                }
            }

            socket.connect(InetSocketAddress(hostname, port))
            isConnected = true

            val mqtt =  mqtt_utils()

            val connectPacket = mqtt.buildConnectPacket(clientID_subscriber, username, aioKey)
            socket.write(ByteBuffer.wrap(connectPacket))

            socket.read(buffer)
            buffer.flip()

            val connack = ByteArray(buffer.remaining())
            buffer.get(connack)

            if (connack[connack.size - 1] != 0x00.toByte()) {
                KeepAlivePingRunning.set(false)
                socket.close()
                return
            }

            val subscribePacket = mqtt.buildSubscribePacket(1, topic)
            socket.write(ByteBuffer.wrap(subscribePacket))

            buffer.clear()
            socket.read(buffer)
            buffer.flip()
            val suback = ByteArray(buffer.remaining())
            buffer.get(suback)

            // 🛡 Start KeepAlive Ping loop using serviceScope
            startPingLoop(socket, KeepAlivePingRunning)

            buffer.clear()
            while (true) {
                buffer.clear()
                val readBytes = socket.read(buffer)
                if (readBytes > 0) {
                    buffer.flip()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    sendNotification("Mqtt IO.Adafruit", "Message: " + parsePublish(data))
                } else if (readBytes < 0) {
                    KeepAlivePingRunning.set(false)
                    socket.close()
                    return
                }
            }
        } catch (e: Exception) {
            sendNotification("Adafruit Mqtt Service", "Unable to connect, service Closed.")
            KeepAlivePingRunning.set(false)
            try {
                socket.close()
            } catch (e2: Exception) { }
            e.printStackTrace()
        }
    }

    private fun buildPingReqPacket(): ByteArray {
        return byteArrayOf(0xC0.toByte(), 0x00)
    }

    private fun startPingLoop(socket: SocketChannel, isRunning: AtomicBoolean) {
        serviceScope.launch {
            try {
                while (isRunning.get()) {
                    delay(80_000) // Wait 80 seconds
                    socket.write(ByteBuffer.wrap(buildPingReqPacket()))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parsePublish(data: ByteArray): String {
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