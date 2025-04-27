@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mqttapp
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalFocusManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.* // * means import all, but advisable to import only what is needed
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.items //
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mqttapp.ui.theme.MqttAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/*
MainActivity is an Activity:
This part indicates that MainActivity represents a screen or a view within your application.
It's the starting point for user interaction when the app is launched

: ComponentActivity()
This specifies that MainActivity inherits from ComponentActivity. This inheritance grants
MainActivity the necessary lifecycle callbacks, enabling it to manage the app's state and handle
UI updates as the user interacts with it.

Lifecycle Management:
ComponentActivity provides standard lifecycle methods (like onCreate(), onResume(), onPause(), etc.)
that allow you to control the behavior of the activity at different stages of its existence, like when
it's being created, displayed, or when the user interacts with it.

ComponentActivity is particularly well-suited for use with Jetpack Compose
*/
class MainActivity : ComponentActivity() {
    private fun startMyService() {
        if (!MyForegroundService.isServiceRunning) {// check if service is already Running
            val intent = Intent(this, MyForegroundService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Log.d("Forground Service","Foreground Service started." )
        }
    }

    private fun stopMyService() {
        val intent = Intent(this, MyForegroundService::class.java)
        stopService(intent)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                //sendNotification("Hello", "This is a local notification.")
                startMyService()
            }
            else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    private fun checkPermissionAndNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { //If your app runs on Android 12 or lower → No permission needed.
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            startMyService()
        } else {
            startMyService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissionAndNotify()
        setContent{MyApp(::stopMyService)} //passing stopMyService() to MyApp()
    }

    /*
    override fun onDestroy() {
    //  no need if u don't have some custom tasks to be done when the app is destroyed,
    // Android handles app process cleanup automatically.
    // Called when  your app gets destroyed
    //add your codes
        super.onDestroy() //calls the parent
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
    // Called when user swipes your app away
    //add your codes
    super.onTaskRemoved(rootIntent) //calls the parent
    }
    */
}

/*
Main Activity Lifecycle Methods:
Method	            When it's called

onCreate()	        When the Activity is first created (starting point).
onStart()	        When the Activity becomes visible to the user.
onResume()	        When the Activity starts interacting with the user (foreground).
onPause()	        When the Activity loses focus but is still partially visible (e.g., dialog or new activity pops up).
onStop()	        When the Activity is no longer visible (completely hidden).
onRestart()	        When the Activity is coming back to the foreground from onStop().
onDestroy()	        When the Activity is about to be destroyed — either because user finishes it, or Android kills it to free memory.

Lifecycle Flow (Simple Version):

onCreate() → onStart() → onResume()
// [User using the app now]

// If another app or dialog comes:
onPause()

// If your app is totally hidden:
onStop()

// If user comes back:
onRestart() → onStart() → onResume()

// If app is closed or destroyed:
onPause() → onStop() → onDestroy()
*/

@Composable
fun MyApp(MyFunction: () -> Unit) {
    //mutableStateOf is necessary to update the ui immediately
    var inputText by remember { mutableStateOf("") }
//    var textState2 by remember { mutableStateOf("AUX") }
    var buttonConnectSubscribe by remember { mutableStateOf(true) }
    var buttonConnectNpublish by remember { mutableStateOf(true) }
    var logMessages = remember { mutableStateListOf<String>() }

    fun test(){
        logMessages.add("Thread Name 1: ${Thread.currentThread().name}") // running in main thread
        /*
        lunch a custom coroutine(similar to thread but more efficient an uses less resources)
        coroutines returns a job object that is used to control the co Routine
        val job1 = CoroutineScope(Dispatchers.IO + Job())
         job1.launch{
        /*your code to be ran in this co routine scope */
        }
        //eg. to control refer documentation
        job1.cancel()// custom scope needs cancellation
        job1.join()
        */
       CoroutineScope(Dispatchers.IO).launch{
            /*
            delay() is
            Non-blocking: Suspends only the coroutine, not the whole thread.
            Safe for UI/Main thread.
            Used inside coroutines only.

            Thread.sleep() — Thread-blocking
            Blocking: Pauses the entire thread.
            Not safe on the main/UI thread.
            Works anywhere, not just coroutines.
          */
            delay(2000) // Simulate background work
            val result = "Thread Name 2: ${Thread.currentThread().name}"


            // Switch to Main thread to update UI, this runs only after the above code finishes
            withContext(Dispatchers.Main) {
                logMessages.add(result)
            }
       }//Releases the resource as this is not a custom scope
    }

    fun connectNsubscribe(){
        buttonConnectSubscribe= false
        CoroutineScope(Dispatchers.IO).launch {
            val mqtt = mqtt_utils()
//            test()
            mqtt.connectAndSubscribe(logMessages) // has a co routine
            buttonConnectSubscribe = true
        }
    }

    fun connectNpublish(){
        buttonConnectNpublish = false //this line runs in the main Thread
        CoroutineScope(Dispatchers.IO).launch {
            val mqtt = mqtt_utils()
            mqtt.connectAndPublish(logMessages, inputText)
            inputText = ""
            withContext(Dispatchers.Main){
                buttonConnectNpublish = true // enable the button
            }
        }
    }

//-------------------UI code starts here Wraped in a theme---------------
    val focusManager = LocalFocusManager.current // used unfocus the text input on touching outside
    MqttAppTheme {
        Box(
            modifier = Modifier.fillMaxSize() // Fills the entire screen
            .pointerInput(Unit) {
            // Listen for touch events
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.pressed }) {
                         focusManager.clearFocus() // Clear focus when touched anywhere
                    }
                }
            }
        }
            .padding(16.dp),
            contentAlignment = Alignment.Center // Centers content in the middle of the screen
        ) {
            Column(modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp) // Adds 16dp padding to all sides
            )
            {

                // Row 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ){ LogConsoleApp(logMessages)}


                // Row 1
                // Spacer to add space between rows
                Spacer(modifier = Modifier.height(25.dp)) // Adds vertical space between rows

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Input Text Box
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Enter Text") },
                        modifier = Modifier.width(180.dp) // 👈 fixed width
                    )
                    Button(enabled = buttonConnectNpublish, onClick = {connectNpublish() }) {
                        Text("Publish",fontSize = 14.sp)
                    }
                }
                // Row 2
                // Spacer to add space between rows
                Spacer(modifier = Modifier.height(25.dp)) // Adds vertical space between rows

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(enabled = buttonConnectSubscribe, onClick = {  MyFunction() }) {
                        Text("StopService")
                    }
                    Button(enabled = buttonConnectSubscribe, onClick = {  connectNsubscribe() }) {
                        Text("Subscribe")
                    }
                }

                // Row 3
                // Spacer to add space between rowsS
                Spacer(modifier = Modifier.height(25.dp)) // Adds vertical space between rows
            }
        }

    }
//-------------------UI code ends here---------------
}


@Composable
fun LogConsoleApp(logMessages: SnapshotStateList<String>) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "📜 Log Console",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp, top = 10.dp)
        )

        LogView(messages = logMessages)

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        logMessages.add("✅ Log entry at ${System.currentTimeMillis()}")
                    }
                }
            }) {
                Text("Add Log")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                logMessages.clear()
            }) {
                Text("Clear")
            }
        }
    }
}

@Composable
fun LogView(messages: List<String>) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Color.Black)
            .padding(8.dp)
    ) {
        items(messages) { msg -> // this items() function is of "import androidx.compose.foundation.lazy.items"
            Text(
                text = msg,
                color = Color.Green,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
    // Auto-scroll when new messages are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }
}