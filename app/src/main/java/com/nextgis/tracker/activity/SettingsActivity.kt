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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.nextgis.maplib.*
import com.nextgis.maplib.activity.AddInstanceActivity
import com.nextgis.tracker.R
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.databinding.ActivitySettingsBinding

const val CONTENT_ACTIVITY = 604

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val mHandler = Handler()
    private var mRegenerateDialogIsShown = false

    private var mTrackerService: TrackerService? = null
    private var mIsBound = false
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
        binding.divTrackByDay.isChecked = sharedPref.getBoolean("divTracksByDay", true)
        binding.timeInterval.intValue = sharedPref.getInt("timeInterval", 1)    // 1 sec
        binding.minDistance.intValue = sharedPref.getInt("minDistance", 10)     // 10 m

        binding.sendInterval.intValue = sharedPref.getInt("sendInterval", 1200)
        binding.sendPointMax.intValue = sharedPref.getInt(Constants.Settings.sendTracksPointsMaxKey, 100)

        binding.deviceId.text = Track.getId()

        binding.sendToNgw.isChecked = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if(binding.sendToNgw.isChecked) {
            binding.sendToNgw.isEnabled = true
        }
        else {
            binding.sendToNgw.isEnabled = false
            val checkTrackerInNGW = object : Runnable {
                override fun run() {
                    // Check tracker is in web GIS
                    if (Track.isRegistered()) {
                        binding.sendToNgw.isEnabled = true
                    } else {
                        binding.sendToNgw.isEnabled = false
                        mHandler.postDelayed(this, 5000) // check every 5 seconds
                    }
                }
            }

            if (checkPermission(this, Manifest.permission.INTERNET)) {
                // Permission is granted
                mHandler.post(checkTrackerInNGW)
            }
        }

        binding.shareButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(R.string.share_text).format(Track.getId())
                )
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.share_subj)
                )
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.share_tracker_id)))
        }

        binding.createInNgwButton.setOnClickListener {
            val intent = Intent(this, AddInstanceActivity::class.java)
            startActivityForResult(intent, AddInstanceActivity.ADD_INSTANCE_REQUEST)
        }

        binding.regenerateId.setOnClickListener {
            mRegenerateDialogIsShown = true
            showRegenerateDialog()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AddInstanceActivity.ADD_INSTANCE_REQUEST -> {
                if (resultCode == Activity.RESULT_OK)
                    getInstanceURL()?.let { launchContentSelector(it) }
            }
            CONTENT_ACTIVITY -> clearConnections()
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

    private fun getInstanceURL(): String? {
        return API.getCatalog()?.children()?.firstOrNull { it.type == 72 }?.children()?.firstOrNull()?.name
    }

    private fun launchContentSelector(instance: String) {
        val intent = Intent(this, ContentInstanceActivity::class.java)
        intent.putExtra("instance", instance)
        startActivityForResult(intent, CONTENT_ACTIVITY)
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
        with (sharedPref.edit()) {
            putBoolean("divTracksByDay", binding.divTrackByDay.isChecked)
            putInt("timeInterval", binding.timeInterval.intValue)
            putInt("minDistance", binding.minDistance.intValue)
            putInt("sendInterval", binding.sendInterval.intValue)
            putInt(Constants.Settings.sendTracksPointsMaxKey, binding.sendPointMax.intValue)
            putBoolean(Constants.Settings.sendTracksToNGWKey, binding.sendToNgw.isChecked)
            commit()
        }

        if (binding.sendToNgw.isChecked) {
            enableSync(binding.sendInterval.intValue.toLong())
        } else {
            disableSync()
        }

        // restart service if it is running
//        if(mIsBound) {
//            mTrackerService?.update()
//        }
        if(mIsBound) {
            Toast.makeText(this, "to apply changes need to restart track recording", Toast.LENGTH_SHORT).show()
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
