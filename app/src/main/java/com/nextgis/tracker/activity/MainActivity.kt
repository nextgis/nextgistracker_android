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
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.GpsStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Location
import com.nextgis.maplib.TrackInfo
import com.nextgis.maplib.checkPermission
import com.nextgis.maplib.fragment.LocationInfoFragment
import com.nextgis.maplib.service.TrackerDelegate
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.BuildConfig
import com.nextgis.tracker.R
import com.nextgis.tracker.adapter.TrackAdapter
import com.nextgis.tracker.databinding.ActivityMainBinding
import com.nextgis.tracker.startService
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

public const val SENTRY_DSN = BuildConfig.TRACKER_SENTRY_DSN
private const val NGT_PERMISSIONS_REQUEST_INTERNET = 771
private const val NGT_PERMISSIONS_REQUEST_GPS = 772
private const val NGT_PERMISSIONS_REQUEST_WAKE_LOCK = 773

const val PERMISSIONS_REQUEST = 1
const val LOCATION_REQUEST = 2



class MainActivity : BaseActivity(),
    PopupMenu.OnMenuItemClickListener, GpsStatus.Listener, LocationListener  {

    companion object const {
        val PERMISSIONS_REQUEST: Int = 1
    }

    private lateinit var binding: ActivityMainBinding


    protected var mLocationManager: LocationManager? = null
    private var mSatelliteCount = 0
    var localGPSOn = false

//    enum class MessageType(val code: String) {
//        PROCESS_LOCATION_UPDATES("com.nextgis.tracker.PROCESS_LOCATION_UPDATES")
//    }


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

//    private val mBroadCastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            Log.e("TRACKK", "onReceive mBroadCastReceiver")
//            when (intent?.action) {
//                MessageType.PROCESS_LOCATION_UPDATES.code -> {
//                    if(intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED)) {
//                        val location = intent.extras?.get(LocationManager.KEY_LOCATION_CHANGED) as? android.location.Location
//                        if(location != null) {
//                            Log.e("TRACKK", "location != null")
//                            processLocationChanges(Location(location,0))
//                        }
//                    }
//                }
//            }
//        }
//    }

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
//            printMessage("onSatelliteStatusChanged: Satellite count: $mSatelliteCount")
        }
    }

    private val mGpsLocationListener = object : LocationListener {

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {        }
        override fun onProviderEnabled(provider: String) {        }
        override fun onProviderDisabled(provider: String) {        }

        override fun onLocationChanged(location: android.location.Location) {
            processLocationChanges(Location(location, mSatelliteCount))
        }
    }

//    @SuppressLint("MissingPermission")
//    @Suppress("DEPRECATION")
//    private val mGpsStatusListener = GpsStatus.Listener { event ->
//        if(event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
//            var satelliteCount = 0
//
//            if (checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
//                val satellites = mLocationManager?.getGpsStatus(null)?.satellites
//                if(satellites != null) {
//                    for (sat in satellites) {
//                        if (sat.usedInFix()) {
//                            satelliteCount++
//                        }
//                    }
//                }
//            }
//            if(satelliteCount > 0) {
//                mSatelliteCount = satelliteCount
//            }
//        }
//    }

    fun processLocationChanges(location: Location){
        try {
            val fm = supportFragmentManager
            var locationFrag =
                fm.findFragmentById(R.id.fragmentLocationInfo) as LocationInfoFragment
            locationFrag?.onLocationChanged(location)
        } catch (exception:Exception){
            Log.e("TAG", exception.toString())
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
            requestPermissions( R.string.permissions, R.string.requested_permissions,PERMISSIONS_REQUEST,
                permissions.toTypedArray()
            )
        }

        if(!checkPermission(this, Manifest.permission.WAKE_LOCK)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WAKE_LOCK)) {
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
        //if(mHasInternetPerm) {
            sentryDSN = SENTRY_DSN
        //}

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

        val sendInterval = sharedPref.getInt("sendInterval", 10).toLong()
        val syncWithNGW = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if (syncWithNGW) {
            enableSync(sendInterval)
        } else {
            disableSync()
        } // not needed for tracker  -   track sends to NGW directly - no

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


//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            mLocationManager?.removeUpdates(getPendingIntent())
//            try {
//                unregisterReceiver(mBroadCastReceiver)
//            } catch (exeception: Exception ){
//                Log.e("TAG", exeception.toString())
//            }
//            mLocationManager?.unregisterGnssStatusCallback(mGnssStatusListener)
//        }
//        else {
//            mLocationManager?.removeUpdates(mGpsLocationListener)
//            mLocationManager?.removeGpsStatusListener(mGpsStatusListener)
//        }
        localGPSOn = false
    }

    fun startGPS(){
        if (!mIsServiceRunning ) {
            if (localGPSOn)
                return
            if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this,ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ){

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

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    val filter = IntentFilter()
//                    filter.addAction(MessageType.PROCESS_LOCATION_UPDATES.code)
//                    registerReceiver(mBroadCastReceiver, filter)
//                    mLocationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,minTime,minDist,getPendingIntent())
//                    mLocationManager?.registerGnssStatusCallback(mGnssStatusListener)
//                } else {
//                    mLocationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,minTime,minDist,mGpsLocationListener,mainLooper)
//                    mLocationManager?.addGpsStatusListener(mGpsStatusListener)
//                }
            }
        }
    }

    fun deleteTrack(trackInfo: TrackInfo){
        val store = API.getStore()
        val tracksTable = store?.trackTable()

        tracksTable?.deletePoints(trackInfo.start, trackInfo.stop)
        mTracksAdapter?.refresh()
    }

//    fun onTrackClick(){
//    }

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
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        Log.e("TTRRAACCKKEERR", "requestCode " + requestCode)
        //Log.e("TTRRAACCKKEERR", "permissions " + permissions.toString())
//        for (perStr in permissions)
//            Log.e("TTRRAACCKKEERR", "permissions " + perStr)
//
//        for (grant in grantResults)
//            Log.e("TTRRAACCKKEERR", "grantResults " + grant)
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

//    private fun getPendingIntent() : PendingIntent {
//        val intent = Intent()
//        intent.action = MessageType.PROCESS_LOCATION_UPDATES.code
//        return PendingIntent.getBroadcast(this, 0, intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
//    }

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
}
