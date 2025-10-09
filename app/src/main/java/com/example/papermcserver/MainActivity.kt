package com.example.papermcserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.*

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerVersions: Spinner
    private lateinit var btnDownload: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var consoleOutput: TextView
    private lateinit var maxPlayersInput: EditText
    private lateinit var serverPortInput: EditText

    private val client = OkHttpClient()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverProcess: Process? = null
    private var serverJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (!allGranted) {
            Toast.makeText(this, "Permissions are required to run the server", Toast.LENGTH_LONG).show()
        } else {
            fetchVersions()
        }
    }

    private var versionsMap: Map<String, String> = emptyMap() // version -> download URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerVersions = findViewById(R.id.spinner_versions)
        btnDownload = findViewById(R.id.btn_download)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        consoleOutput = findViewById(R.id.tv_console)
        maxPlayersInput = findViewById(R.id.et_max_players)
        serverPortInput = findViewById(R.id.et_server_port)

        btnStart.isEnabled = false
        btnStop.isEnabled = false

        checkPermissions()

        btnDownload.setOnClickListener {
            val version = spinnerVersions.selectedItem as? String
            if (version != null) {
                val url = versionsMap[version]
                if (url != null) {
                    downloadServerJar(url)
                }
            }
        }

        btnStart.setOnClickListener {
            startServer()
        }

        btnStop.setOnClickListener {
            stopServer()
        }
    }

    private fun checkPermissions() {
        val neededPermissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
        val permissionsNotGranted = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNotGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted.toTypedArray())
        } else {
            fetchVersions()
        }
    }

    private fun fetchVersions() {
        ioScope.launch {
            try {
                val request = Request.Builder()
                    .url("https://gist.githubusercontent.com/osipxd/6119732e30059241c2192c4a8d2218d9/raw/d8b5faadcfdfadfa0ff58dbf7c04cd10de16c678/paper-versions.json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                val jsonStr = response.body?.string() ?: ""

                val json = JSONObject(jsonStr)
                val versionsJson = json.getJSONObject("versions")
                val versionsList = versionsJson.keys().asSequence().toList().sortedDescending()

                val map = mutableMapOf<String, String>()
                for (version in versionsList) {
                    val vObj = versionsJson.getJSONObject(version)
                    val buildNumber = vObj.getString("build")
                    val downloadUrl = "https://papermc.io/api/v2/projects/paper/versions/$version/builds/$buildNumber/downloads/paper-$version-$buildNumber.jar"
                    map[version] = downloadUrl
                }

                versionsMap = map

                withContext(Dispatchers.Main) {
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, versionsList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerVersions.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to fetch versions: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadServerJar(url: String) {
        ioScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    btnDownload.isEnabled = false
                    consoleOutput.append("Downloading server jar...\n")
                }

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Failed to download: $response")

                val serverDir = getExternalFilesDir(null)?.resolve("papermc_server")
                if (serverDir == null) {
                    throw IOException("Storage not accessible")
                }
                if (!serverDir.exists()) serverDir.mkdirs()
                val jarFile = serverDir.resolve("paper_server.jar")

                val sink = jarFile.outputStream().buffered()
                response.body?.byteStream()?.copyTo(sink)
                sink.flush()
                sink.close()

                withContext(Dispatchers.Main) {
                    consoleOutput.append("Download completed: ${jarFile.absolutePath}\n")
                    btnStart.isEnabled = true
                    btnDownload.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    btnDownload.isEnabled = true
                }
            }
        }
    }

    private fun startServer() {
        if (serverProcess != null) {
            Toast.makeText(this, "Server is already running", Toast.LENGTH_SHORT).show()
            return
        }

        val maxPlayers = maxPlayersInput.text.toString().toIntOrNull() ?: 10
        val port = serverPortInput.text.toString().toIntOrNull() ?: 25565

        val serverDir = getExternalFilesDir(null)?.resolve("papermc_server")
        if (serverDir == null) {
            Toast.makeText(this, "Storage not accessible", Toast.LENGTH_LONG).show()
            return
        }
        val jarFile = serverDir.resolve("paper_server.jar")
        if (!jarFile.exists()) {
            Toast.makeText(this, "Server jar not found, please download first", Toast.LENGTH_LONG).show()
            return
        }

        val propertiesFile = serverDir.resolve("server.properties")
        writeServerProperties(propertiesFile, maxPlayers, port)

        val eulaFile = serverDir.resolve("eula.txt")
        if (!eulaFile.exists()) {
            eulaFile.writeText("eula=true\n")
        }

        serverJob = ioScope.launch {
            try {
                val pb = ProcessBuilder(
                    "java",
                    "-Xmx1024M",
                    "-Xms512M",
                    "-jar",
                    jarFile.absolutePath,
                    "nogui"
                )
                pb.directory(serverDir)
                pb.redirectErrorStream(true)

                serverProcess = pb.start()

                val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
                var line: String?

                withContext(Dispatchers.Main) {
                    btnStart.isEnabled = false
                    btnStop.isEnabled = true
                    consoleOutput.append("Server started...\n")
                }

                while (reader.readLine().also { line = it } != null) {
                    val outputLine = line ?: ""
                    withContext(Dispatchers.Main) {
                        consoleOutput.append("$outputLine\n")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to start server: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnStart.isEnabled = true
                    btnStop.isEnabled = false
                    consoleOutput.append("Server stopped.\n")
                }
                serverProcess = null
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        serverProcess?.destroy()
        serverProcess = null
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show()
    }

    private fun writeServerProperties(file: File, maxPlayers: Int, port: Int) {
        val props = """
            #Minecraft server properties
            max-players=$maxPlayers
            server-port=$port
            online-mode=true
            motd=A PaperMC Server on Android
            white-list=false
            difficulty=easy
            gamemode=survival
            """.trimIndent()

        file.writeText(props)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        ioScope.cancel()
    }
}
