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
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Constants.Settings.divTracksByDayKey
import com.nextgis.maplib.Constants.Settings.minDistanceKey
//import com.nextgis.maplib.Constants.Settings.restoreTrackAfterRebootKey
import com.nextgis.maplib.Constants.Settings.sendIntervalKey
import com.nextgis.maplib.Constants.Settings.timeIntervalKey
import com.nextgis.maplib.Constants.Settings.webGisNameKey
import com.nextgis.maplib.NGWResourceGroup
import com.nextgis.maplib.NGWTrackerGroup
import com.nextgis.maplib.Object
import com.nextgis.maplib.Track
import com.nextgis.maplib.activity.AddInstanceActivity
import com.nextgis.maplib.printError
import com.nextgis.maplib.printMessage
import com.nextgis.maplib.service.TrackerService
import com.nextgis.maplib.util.NonNullObservableField
import com.nextgis.maplib.util.isInternetAvailable
import com.nextgis.maplib.util.runAsync
import com.nextgis.tracker.R
import com.nextgis.tracker.databinding.ActivitySettingsBinding
import java.util.Stack

const val CONTENT_ACTIVITY = 604

class SettingsActivity : BaseActivity() {

    companion object {
        const val START_FROM_MAIN = 45698
    }

    private lateinit var binding: ActivitySettingsBinding


    private val stack = Stack<Object>()
    val path = NonNullObservableField("/")

    private var mRegenerateDialogIsShown = false

    private var mTrackerService: TrackerService? = null
    private var mIsBound = false
    private var hasChanges = false
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TrackerService.LocalBinder
            mTrackerService = binder.getService()
            if (mTrackerService?.getStatus() ==TrackerService.Status.RUNNING)
                mIsBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mIsBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set values from properties
        val sharedPref = getDefaultSharedPreferences(this)
        binding.divTrackByDay.isChecked = sharedPref.getBoolean(divTracksByDayKey, true)
//        binding.restoreTrackAfterRestart.isChecked = sharedPref.getBoolean(restoreTrackAfterRebootKey, false)

        binding.timeInterval.intValue = sharedPref.getInt(timeIntervalKey, 10)    // 10 sec
        binding.minDistance.intValue = sharedPref.getInt(minDistanceKey, 10)     // 10 m

        binding.sendInterval.intValue = sharedPref.getInt(sendIntervalKey, 10)
        binding.sendPointMax.intValue = sharedPref.getInt(Constants.Settings.sendTracksPointsMaxKey, 100)
        binding.deviceId.text = Track.getId()
        binding.progressloaderarea.setOnClickListener {  }
        binding.sendToNgw.isChecked = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)

        val sharedPrefMain = getDefaultSharedPreferences(this)
        updateSendToNGWPrompt(binding.syncNgwTv, sharedPrefMain)

        checkSendStillAvailable(sharedPrefMain, binding.sendToNgw, binding.syncNgwTv)
        setupOnSendClick(binding.sendToNgw)

        binding.shareId.setOnClickListener(){
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("uid", Track.getId())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied, Toast.LENGTH_LONG).show()
        }
    }



    override fun showProgress(){
        binding.progressloaderarea.visibility = View.VISIBLE
    }

    override fun hideProgress(){
        binding.progressloaderarea.visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val sharedPref = getDefaultSharedPreferences(this)
        val sendToNGWCPrefValue = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if (binding.sendToNgw.isChecked && sendToNGWCPrefValue  == false && requestCode == AddInstanceActivity.ADD_INSTANCE_REQUEST)
            binding.sendToNgw.isChecked = false

        when (requestCode) {
            AddInstanceActivity.ADD_INSTANCE_REQUEST -> {
                if (resultCode == Activity.RESULT_OK)
                    getInstanceURL()?.let {
                        launchContentSelector(it)
                    }
            }
            AddInstanceActivity.ADD_INSTANCE_TO_CREATE_TRACKER_REQUEST -> {
               createTracker(resultCode, binding.sendToNgw, binding.syncNgwTv)
            }

            CONTENT_ACTIVITY -> {clearConnections()
                if (requestCode == CONTENT_ACTIVITY){
                    if (resultCode == RESULT_OK){
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun clearConnections() {
        API.getCatalog()?.children()?.let {
            for (child in it)
                if (child.type == 72) {
                    child.children().map { connection -> connection.delete() }
                }
        }
    }

    private fun showRegenerateDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.refresh_device_id)
            .setMessage(R.string.refresh_device_id_message)
            .setPositiveButton(R.string.change) { _, _ -> finishGenerate(true) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finishGenerate(false) }
            .create()
            .show()
    }

    private fun finishGenerate(change: Boolean) {
        mRegenerateDialogIsShown = false
        if(change) {
            binding.deviceId.text = Track.getId(change)
            binding.sendToNgw.isChecked = false
        }
    }

    override fun onPause() {
        super.onPause()
        // save settings
        val sharedPref = getDefaultSharedPreferences(this)
        val diffTrackByDays =sharedPref.getBoolean(divTracksByDayKey, true)
        val timeInterval = sharedPref.getInt(timeIntervalKey, 10)
        val minDistance = sharedPref.getInt(minDistanceKey, 10)
        val sendInterval = sharedPref.getInt(sendIntervalKey, 10)
        val sendTracksPointsMaxKey = sharedPref.getInt(Constants.Settings.sendTracksPointsMaxKey,100)

        if (diffTrackByDays != binding.divTrackByDay.isChecked ||
            timeInterval !=  binding.timeInterval.intValue ||
            minDistance != binding.minDistance.intValue ||
            sendInterval != binding.sendInterval.intValue||
            sendTracksPointsMaxKey !=  binding.sendPointMax.intValue)
            hasChanges = true

        with (sharedPref.edit()) {
            putBoolean(divTracksByDayKey, binding.divTrackByDay.isChecked)
//            putBoolean(restoreTrackAfterRebootKey, binding.restoreTrackAfterRestart.isChecked)
            putInt(timeIntervalKey, binding.timeInterval.intValue)
            putInt(minDistanceKey, binding.minDistance.intValue)
            putInt(sendIntervalKey, binding.sendInterval.intValue)
            putInt(Constants.Settings.sendTracksPointsMaxKey, binding.sendPointMax.intValue)
            //putBoolean(Constants.Settings.sendTracksToNGWKey, binding.sendToNgw.isChecked)
            commit()
        }

        if (binding.sendToNgw.isChecked) {
            enableSync(binding.sendInterval.intValue.toLong())
        } else {
            disableSync()
        }

        // restart service if it is running
        if(mIsBound && hasChanges) {
            Toast.makeText(this, R.string.settings_changes, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean("showRegenerateDialog", mRegenerateDialogIsShown)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mRegenerateDialogIsShown = savedInstanceState?.getBoolean("showRegenerateDialog") ?: false
        if(mRegenerateDialogIsShown) {
            showRegenerateDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        // Get current status
        val intent = Intent(this, TrackerService::class.java)
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if(mIsBound) {
            unbindService(mServiceConnection)
        }
    }

}