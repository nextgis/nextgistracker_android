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
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Location
import com.nextgis.maplib.checkPermission
import com.nextgis.maplib.service.TrackerDelegate
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.R
import com.nextgis.tracker.adapter.TrackAdapter
import com.nextgis.tracker.databinding.ActivityMainBinding
import com.nextgis.tracker.startService
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date

private const val SENTRY_DSN = "https://7055a21dbcbd4b43ac0843d004aa4a92@sentry.nextgis.com/15"
private const val NGT_PERMISSIONS_REQUEST_INTERNET = 771
private const val NGT_PERMISSIONS_REQUEST_GPS = 772
private const val NGT_PERMISSIONS_REQUEST_WAKE_LOCK = 773

const val PERMISSIONS_REQUEST = 1
const val LOCATION_REQUEST = 2


class MainActivity : BaseActivity() {

    companion object const {
        val PERMISSIONS_REQUEST: Int = 1
    }

    private lateinit var binding: ActivityMainBinding

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


    private val mTrackerDelegate = object : TrackerDelegate {
        override fun onLocationChanged(location: Location) {
        }

        override fun onStatusChanged(status: TrackerService.Status, trackName: String, trackStartTime: Date) {
            mIsServiceRunning = status == TrackerService.Status.RUNNING
            updateServiceStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

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
            val permissions: Array<String>
            permissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) else arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            requestPermissions(
                R.string.permissions,
                R.string.requested_permissions,
                MainActivity.PERMISSIONS_REQUEST,
                permissions
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
        if(mHasInternetPerm) {
            sentryDSN = SENTRY_DSN
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        API.init(this@MainActivity, sentryDSN)

        val store = API.getStore()
        val tracksTable = store?.trackTable()
        if (tracksTable != null) {
            binding.contentMain.tracksList.setHasFixedSize(true)
            mTracksAdapter = TrackAdapter(this, tracksTable)
            binding.contentMain.tracksList.layoutManager = LinearLayoutManager(this)
            binding.contentMain.tracksList.adapter = mTracksAdapter
        }

        val sendInterval = sharedPref.getInt("sendInterval", 1200).toLong()
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


    protected fun isPermissionGranted(permission: String?): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission!!
        ) == PackageManager.PERMISSION_GRANTED
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
                ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    requestCode
                )
            }
        } else ActivityCompat.requestPermissions(this,
            permissions,
            requestCode)

    }

    private fun setupFab() {
        updateServiceStatus()
        binding.fab.setOnClickListener {
            if(mIsServiceRunning) {
                startService(this, TrackerService.Command.STOP)
            }
            else {

                TrackerService.showBackgroundDialog(this, object : TrackerService.BackgroundPermissionCallback {
                    override fun beforeAndroid10(hasBackgroundPermission: Boolean) {
                        if (!hasBackgroundPermission) {
                            val permissions = arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            requestPermissions(R.string.permissions,
                                R.string.requested_permissions,
                                LOCATION_REQUEST,
                                permissions
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
                            }                        }
                    }
                })

            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun requestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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
                    setupFab()
                }
                return
            }
        }
    }

    private fun updateServiceStatus() {
        if (mIsServiceRunning) {
            binding.fab.setImageResource(R.drawable.ic_pause)
        }
        else {
            binding.fab.setImageResource(R.drawable.ic_play)
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
}
