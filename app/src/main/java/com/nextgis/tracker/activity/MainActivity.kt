/*
 * Project:  NextGIS Tracker
 * Purpose:  Software tracker for nextgis.com cloud
 * Author:   Dmitry Baryshnikov <dmitry.baryshnikov@nextgis.com>
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ****************************************************************************
 * Copyright (c) 2018-2019 NextGIS <info@nextgis.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.tracker.activity
import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.GpsStatus
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Constants.Settings.sendIntervalKey
import com.nextgis.maplib.Location
import com.nextgis.maplib.TrackInfo
import com.nextgis.maplib.checkPermission
import com.nextgis.maplib.fragment.LocationInfoFragment
import com.nextgis.maplib.printError
import com.nextgis.maplib.printMessage
import com.nextgis.maplib.service.TrackerDelegate
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.BuildConfig
import com.nextgis.tracker.MainApplication
import com.nextgis.tracker.R
import com.nextgis.tracker.adapter.TrackAdapter
import com.nextgis.tracker.databinding.ActivityMainBinding
import com.nextgis.tracker.startService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

const val SENTRY_DSN = BuildConfig.TRACKER_SENTRY_DSN
private const val NGT_PERMISSIONS_REQUEST_INTERNET = 771
private const val NGT_PERMISSIONS_REQUEST_GPS = 772
private const val NGT_PERMISSIONS_REQUEST_WAKE_LOCK = 773

const val PERMISSIONS_REQUEST = 1
const val LOCATION_REQUEST = 2


class MainActivity : BaseActivity(),
    PopupMenu.OnMenuItemClickListener, GpsStatus.Listener, LocationListener  {



    companion object const {
        val PERMISSIONS_REQUEST: Int = 1
        val CUSTOM_INTENT_ACTION = "com.nextgis.tracker.CUSTOM_SHARE_INTENT"
        const val REQUEST_SAVE_FILE = 8001

    }

    private lateinit var binding: ActivityMainBinding
    protected var mLocationManager: LocationManager? = null
    private var mSatelliteCount = 0
    var localGPSOn = false

    private var mHasInternetPerm = false
    private var mHasGPSPerm = false
    private var mIsServiceRunning = false

    private var mTrackerService: TrackerService? = null
    private var mIsBound = false
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TrackerService.LocalBinder
            mTrackerService = binder.getService()
            mTrackerService?.addDelegate(mTrackerDelegate)
            mIsBound = true
            mTrackerService?.status()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            mIsBound = false
        }
    }
    private var mTracksAdapter: TrackAdapter? = null
    var trackInfo : TrackInfo? = null


    private val mTrackerDelegate = object : TrackerDelegate {
        override fun onLocationChanged(location: Location) {
        }

        override fun onStatusChanged(status: TrackerService.Status, trackName: String, trackStartTime: Date) {
            mIsServiceRunning = status == TrackerService.Status.RUNNING
            val formatter = DateFormat.getTimeInstance()
            formatter.timeZone = TimeZone.getDefault()

            updateServiceStatus(trackName, trackStartTime)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private val mGnssStatusListener = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var satelliteCount = 0
            for(index in 0 until status.satelliteCount) {
                if (status.usedInFix(index)) {
                    satelliteCount++
                }
            }
            if(satelliteCount > 0) {
                mSatelliteCount = satelliteCount
            }
        }
    }

    fun processLocationChanges(location: Location){
        try {
            val fm = supportFragmentManager
            var locationFrag =
                fm.findFragmentById(R.id.fragmentLocationInfo) as LocationInfoFragment?
            locationFrag?.onLocationChanged(location)
        } catch (exception:Exception){
            printError(exception.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager


        // Check internet permission for web GIS interaction
        if(!checkPermission(this, Manifest.permission.INTERNET)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.INTERNET)) {
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.INTERNET),
                    NGT_PERMISSIONS_REQUEST_INTERNET
                )
            }
        } else {
            mHasInternetPerm = true
            mHasGPSPerm = true
        }

        if (!hasPermissions()) {
            val permissions: ArrayList<String>
            permissions = arrayListOf( Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions( R.string.permissions, R.string.requested_permissions,
                PERMISSIONS_REQUEST,
                permissions.toTypedArray()
            )
        }

        if(!checkPermission(this, Manifest.permission.WAKE_LOCK)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.WAKE_LOCK)) {
            }
            else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WAKE_LOCK),
                    NGT_PERMISSIONS_REQUEST_WAKE_LOCK
                )
            }
        }

        var sentryDSN = ""
        sentryDSN = SENTRY_DSN

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        API.init(this@MainActivity, sentryDSN)

        val store = API.getStore()
        val tracksTable = store?.trackTable()
        if (tracksTable != null) {
            binding.contentMain.tracksList.setHasFixedSize(true)
            mTracksAdapter = TrackAdapter(this, tracksTable) {
                trackInfo , view ->
                run {
//                    this.trackInfo = trackInfo
//                    val popup = PopupMenu(this, view)
//                    popup.menuInflater.inflate(R.menu.track_actions, popup.menu)
//                    popup.setOnMenuItemClickListener(this)
//                    popup.show()
                }
            }
            binding.contentMain.tracksList.layoutManager = LinearLayoutManager(this)
            binding.contentMain.tracksList.adapter = mTracksAdapter

        }

        val sendInterval = sharedPref.getInt(sendIntervalKey, 10).toLong()
        val syncWithNGW = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if (syncWithNGW) {
            enableSync(sendInterval)
        } else {
            disableSync()
        }

        if(mHasGPSPerm) {
            setupFab()
        }
    }

    fun stopGPS(){
        mLocationManager?.removeUpdates(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationManager?.unregisterGnssStatusCallback(mGnssStatusListener)
        } else
            mLocationManager?.removeGpsStatusListener(this)
        localGPSOn = false
    }

    fun startGPS(){
        if (!mIsServiceRunning ) {
            if (localGPSOn)
                return
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                val minTime =
                    sharedPref.getInt("timeInterval", 1).toLong() * Constants.millisecondsInSecond
                val minDist = sharedPref.getInt("minDistance", 10).toFloat()
                localGPSOn = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mLocationManager?.registerGnssStatusCallback(mGnssStatusListener)
                } else
                    mLocationManager?.addGpsStatusListener(this)

                val provider = LocationManager.GPS_PROVIDER
                mLocationManager?.requestLocationUpdates(provider, minTime, minDist, this)
            }
        }
//        else {
//            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
//                AlertDialog.Builder(this)
//                    .setTitle("Permission")
//                    .setMessage("for work app need Fine Location perm")
//                    .setPositiveButton("OK") { _, _ ->
//
//                    }
//                    .setNegativeButton(android.R.string.cancel) { _, _ -> {} }
//                    .create()
//                    .show()
//            }
//        }
    }

    fun deleteTrack(trackInfo: TrackInfo){
        val store = API.getStore()
        val tracksTable = store?.trackTable()

        tracksTable?.deletePoints(trackInfo.start, trackInfo.stop)
        mTracksAdapter?.refresh()
    }

    protected fun isPermissionGranted(permission: String?): Boolean {
        return ContextCompat.checkSelfPermission(this, permission!! ) == PackageManager.PERMISSION_GRANTED
    }
    protected fun hasPermissions(): Boolean {
        var permissions = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                //isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                isPermissionGranted(Manifest.permission.GET_ACCOUNTS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) permissions = permissions &&
                isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return permissions
    }

    protected fun requestPermissions(
        title: Int,
        message: Int,
        requestCode: Int,
        permissions: Array<String>) {
        var shouldShowDialog = false
        for (permission in permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                shouldShowDialog = true
                break
            }
        }
        if (shouldShowDialog) {
            val activity: Activity = this
            val builder = AlertDialog.Builder(this).setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null).create()

            builder.setCanceledOnTouchOutside(false)
            builder.show()
            builder.setOnDismissListener {
                ActivityCompat.requestPermissions(activity, permissions,requestCode )
            }
        } else ActivityCompat.requestPermissions(this,
            permissions,
            requestCode)

    }

    private fun setupFab() {
        updateServiceStatus("",Date())
        binding.fab.setOnClickListener {
            if(mIsServiceRunning) {
                startService(this, TrackerService.Command.STOP)
            }
            else {
//                if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
//                    AlertDialog.Builder(this)
//                        .setTitle("Permission")
//                        .setMessage("for work, app need Fine Location perm")
//                        .setPositiveButton("ask perm") { _, _ ->
//                            askFineLocalPermission()
//                        }
//                        .setNegativeButton(android.R.string.cancel) { _, _ -> {} }
//                        .create()
//                        .show()
//                    return@setOnClickListener
//                }

                TrackerService.showBackgroundDialog(this, object : TrackerService.BackgroundPermissionCallback {
                    override fun beforeAndroid10(hasBackgroundPermission: Boolean) {
                        if (!hasBackgroundPermission) {
                            val permissions = arrayListOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            requestPermissions(R.string.permissions,
                                R.string.requested_permissions,
                                LOCATION_REQUEST,
                                permissions.toTypedArray()
                            )
                        } else {
                            startService(baseContext, TrackerService.Command.START)
                            if(!mIsBound) {
                                val intent = Intent(baseContext, TrackerService::class.java)
                                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
                            }                         }
                    }

                    @RequiresApi(api = Build.VERSION_CODES.Q)
                    override fun onAndroid10(hasBackgroundPermission: Boolean) {
                        if (!hasBackgroundPermission) {
                            requestPermissions()
                        } else {
                            startService(baseContext, TrackerService.Command.START)
                            if(!mIsBound) {
                                val intent = Intent(baseContext, TrackerService::class.java)
                                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
                            }                        }
                    }

                    @RequiresApi(api = Build.VERSION_CODES.Q)
                    override fun afterAndroid10(hasBackgroundPermission: Boolean) {
                        if (!hasBackgroundPermission) {
                            requestPermissions()
                        } else {
                            startService(baseContext, TrackerService.Command.START)
                            if(!mIsBound) {
                                val intent = Intent(baseContext, TrackerService::class.java)
                                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
                            }
                        }
                    }
                })
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION            )
        ActivityCompat.requestPermissions(this, permissions, LOCATION_REQUEST)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
//            R.id.sync_manual -> {
//                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
//                val syncWithNGW = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
//                if (syncWithNGW)
//                    startManualSync()
//                else {
//                    val intent = Intent(this, SettingsActivity::class.java)
//                    startActivity(intent)
//                }
//                return true
//            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NGT_PERMISSIONS_REQUEST_INTERNET -> {
                mHasInternetPerm = (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                if(mHasInternetPerm) {
                    setupFab()
                }
                return
            }
            NGT_PERMISSIONS_REQUEST_GPS -> {
                mHasGPSPerm = (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                if(mHasGPSPerm) {
                    startGPS()
                    setupFab()
                }
                return
            }
            LOCATION_REQUEST ->{
                if (permissions.size ==1 && permissions[0].equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    && grantResults.size == 1 && grantResults[0] == 0){
                    startService(baseContext, TrackerService.Command.START)
                    if(!mIsBound) {
                        val intent = Intent(baseContext, TrackerService::class.java)
                        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
                    }
                }
                return
            }
        }
    }

    private fun updateServiceStatus(trackName:String, trackStartTime : Date) {
        if (mIsServiceRunning) {
            mTracksAdapter?.setOnProgress(true, trackName, trackStartTime)
            binding.fab.setImageResource(R.drawable.ic_pause)
            mTracksAdapter?.refresh()
            stopGPS()

        }
        else {
            binding.fab.setImageResource(R.drawable.ic_play)
            mTracksAdapter?.setOnProgress(false, "", Date())
            mTrackerService?.clearTrackNameInProgress()
            startGPS()
        }
        mTracksAdapter?.refresh()
        binding.contentMain.tracksGroup.text = getString(R.string.tracks) + " (${mTracksAdapter?.itemCount})"
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, TrackerService::class.java)
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()

        if(mIsBound) {
            mTrackerService?.removeDelegate(mTrackerDelegate)
            unbindService(mServiceConnection)
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.menu_delete){

            if (trackInfo != null) {
                AlertDialog.Builder(this)
                    .setTitle("Deletion")
                    .setMessage("Delete track? " + trackInfo!!.name + " ?")
                    .setPositiveButton(R.string.delete) { _, _ -> deleteTrack(trackInfo!!) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> {} }
                    .create()
                    .show()
            }
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        stopGPS()
    }

    override fun onResume()    {
        super.onResume()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        startGPS()
    }

    override fun onGpsStatusChanged(event: Int) {
        if(event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            var satelliteCount = 0

            if (checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                val satellites = mLocationManager?.getGpsStatus(null)?.satellites
                if(satellites != null) {
                    for (sat in satellites) {
                        if (sat.usedInFix()) {
                            satelliteCount++
                        }
                    }
                }
            }
            if(satelliteCount > 0) {
                mSatelliteCount = satelliteCount
            }
        }

    }

    override fun onLocationChanged(location: android.location.Location) {
        processLocationChanges(Location(location, mSatelliteCount))
    }

    override fun onProviderEnabled(provider: String) {
        printMessage("onProviderEnabled: provider: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        printMessage("onProviderDisabled: provider: $provider")
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        printMessage("onStatusChanged: provider: $provider, status: $status")
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
    }

//    override fun onNewIntent(intent: Intent?) {
//        super.onNewIntent(intent)
//
//        if (intent?.action == "SAVE_FILE") {
//            val fileUri: Uri? = intent.getParcelableExtra("fileUri")
//            fileUri?.let {
//                val  fileOfData = (applicationContext as MainApplication).getFileToSave()
//                saveFileToDestination(intent, fileOfData)
//            }
//        }
//    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SAVE_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val fileOfData = (applicationContext as MainApplication).getFileToSave()
                saveFileToDestination(data, fileOfData)
            }

        }
    }

    private fun saveFileToDestination(destination: Intent, inputData: File?) {
            // сохраняем в выбранное место
            if (destination == null)
                return
            try {
                val resolver: ContentResolver = contentResolver
                val docUri: Uri? = destination?.getData()

                if (docUri != null) {
                    if (docUri != null) {
                        val output = resolver.openOutputStream(docUri)
                        try {
                            val inputStream: InputStream = FileInputStream(inputData)
                            val buffer = ByteArray(10240) // or other buffer size
                            var read: Int
                            while ((inputStream.read(buffer).also { read = it }) != -1) {
                                output!!.write(buffer, 0, read)
                            }
                            output!!.flush()
                            Toast.makeText(MainActivity@ this, R.string.save_file_complete, Toast.LENGTH_LONG)
                                .show()
                        } catch (ex: java.lang.Exception) {
                            AlertDialog.Builder(MainActivity@this)
                                .setMessage(R.string.error_save_file)
                                .setPositiveButton(android.R.string.ok, null)
                                .create()
                                .show()
                            //Toast.makeText(activity, R.string.error_on_save, Toast.LENGTH_LONG).show();
                            printError(if (ex.message == null ) ex.toString() else ex.message!! )
                        } finally {
                            output!!.close()
                        }
                    }
                }
            } catch (exception: java.lang.Exception) {
                AlertDialog.Builder(MainActivity@ this)
                    .setMessage(R.string.error_save_file)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                    .show()
                //Toast.makeText(activity, R.string.error_on_save, Toast.LENGTH_LONG).show();
                printError(if (exception.message == null ) exception.toString() else exception.message!!)
            }
        }
    }

//    private fun saveFileToDownloads(fileUri: Uri) {
//        try {
//            // Папка для загрузок
//            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//            if (!downloadsDir.exists()) downloadsDir.mkdirs()
//
//            // Копируем файл в папку Downloads
//            val inputStream: InputStream? = contentResolver.openInputStream(fileUri)
//            val outputFile = File(downloadsDir, "shared_gpx_file.gpx")
//            val outputStream: OutputStream = FileOutputStream(outputFile)
//
//            val buffer = ByteArray(1024)
//            var length: Int
//            while (inputStream?.read(buffer).also { length = it ?: -1 } != -1) {
//                outputStream.write(buffer, 0, length)
//            }
//
//            outputStream.close()
//            inputStream?.close()
//
//            Toast.makeText(this, "Файл сохранен в папку Downloads", Toast.LENGTH_SHORT).show()
//        } catch (e: Exception) {
//            Toast.makeText(this, "Ошибка сохранения файла: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }

    //    fun askFineLocalPermission(){
//        val permissions: ArrayList<String>
//        permissions = arrayListOf( Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
//
//        requestPermissions( R.string.permissions, R.string.requested_permissions_fineloc,
//            PERMISSIONS_REQUEST,
//            permissions.toTypedArray())
//
//
//        //ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST)
//    }
//}
