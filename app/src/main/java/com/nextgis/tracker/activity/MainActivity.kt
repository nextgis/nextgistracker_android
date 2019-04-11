/*
 * Project:  NextGIS Tracker
 * Purpose:  Software tracker for nextgis.com cloud
 * Author:   Dmitry Baryshnikov <dmitry.baryshnikov@nextgis.com>
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
import android.accounts.Account
import android.accounts.AccountManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.checkPermission
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.R
import com.nextgis.tracker.adapter.TrackAdapter
import com.nextgis.tracker.startService
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.util.*

private const val SENTRY_DSN = "https://7055a21dbcbd4b43ac0843d004aa4a92:130162eecf424376ad02f3947e1c1068@sentry.nextgis.com/15"
private const val NGT_PERMISSIONS_REQUEST_INTERNET = 771
private const val NGT_PERMISSIONS_REQUEST_GPS = 772
const val AUTHORITY = "com.nextgis.tracker"
const val ACCOUNT_TYPE = "com.nextgis.account2"
const val ACCOUNT = "NextGIS Tracker"

class MainActivity : AppCompatActivity() {

    private var mHasInternetPerm = false
    private var mHasGPSPerm = false
    private var mIsServiceRunning = false
    private var mCurrentTrackName = ""
    private var mCurrentTrackDate = Date()
    private lateinit var mAccount: Account

    private var mTracksAdapter: TrackAdapter? = null

    private val mBroadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            when (intent?.action) {
                TrackerService.MessageType.STATUS_CHANGED.code -> handleStatusChanged(intent)
            }
        }
    }

    private fun handleStatusChanged(intent: Intent?) {
        mIsServiceRunning = intent?.getBooleanExtra("is_running", false) ?: false
        if(mIsServiceRunning) {
            mCurrentTrackName = intent?.getStringExtra("name") ?: ""
            mCurrentTrackDate = intent?.getSerializableExtra("start") as Date
        }
        else {
            mCurrentTrackName = ""
            mCurrentTrackDate = Date()
        }
        updateServiceStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Check internet permission for web GIS interaction
        if(!checkPermission(this, Manifest.permission.INTERNET)) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.INTERNET)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.INTERNET),
                    NGT_PERMISSIONS_REQUEST_INTERNET
                )
            }

        } else {
            // Permission has already been granted
            mHasInternetPerm = true
        }

        // Check GPS permission
        if(!checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    NGT_PERMISSIONS_REQUEST_GPS
                )
            }
        } else {
            // Permission has already been granted
            mHasGPSPerm = true
        }

        Sentry.init(SENTRY_DSN, AndroidSentryClientFactory(this))

        API.init(this@MainActivity)

        if(mHasGPSPerm) {
            setupFab()
        }

        val filter = IntentFilter()
        filter.addAction(TrackerService.MessageType.STATUS_CHANGED.code)
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadCastReceiver, filter)

        val store = API.getStore()
        val tracksTable = store.trackTable()
        if(tracksTable != null) {
            tracksList.setHasFixedSize(true)
            mTracksAdapter = TrackAdapter(this, tracksTable)
            tracksList.layoutManager = LinearLayoutManager(this)
            tracksList.adapter = mTracksAdapter
        }

        // Get current status
        startService(this, TrackerService.Command.STATUS)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val sendInterval = sharedPref.getInt("sendInterval", 1200).toLong()
        val syncWithNGW = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if(syncWithNGW) {
            mAccount = createSyncAccount()
            ContentResolver.setSyncAutomatically(mAccount, AUTHORITY, true)
            ContentResolver.addPeriodicSync(mAccount, AUTHORITY, Bundle.EMPTY, sendInterval)
        }
    }

    private fun createSyncAccount(): Account {
        val accountManager = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        return Account(ACCOUNT, ACCOUNT_TYPE).also { newAccount ->
            if (accountManager.addAccountExplicitly(newAccount, null, null)) {

            }
            else {

            }
        }
    }

    private fun setupFab() {
        updateServiceStatus()
        fab.setOnClickListener {
            if(mIsServiceRunning) {
                startService(this, TrackerService.Command.STOP)
            }
            else {
                startService(this, TrackerService.Command.START)
            }
        }
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
            fab.setImageResource(android.R.drawable.ic_media_pause)
        }
        else {
            fab.setImageResource(android.R.drawable.ic_media_play)
        }

        tracksGroup.text = getString(R.string.tracks) + " (${mTracksAdapter?.itemCount})"
        mTracksAdapter?.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadCastReceiver)
    }
}
