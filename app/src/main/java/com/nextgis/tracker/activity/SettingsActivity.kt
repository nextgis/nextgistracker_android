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
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Track
import com.nextgis.maplib.checkPermission
import com.nextgis.tracker.R
import com.nextgis.maplib.service.TrackerService
import com.nextgis.tracker.startService
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    private val mHandler = Handler()
    private var mRegenerateDialogIsShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set values from properties
        val sharedPref = getDefaultSharedPreferences(this)
        divTrackByDay.isChecked = sharedPref.getBoolean("divTracksByDay", true)
        timeInterval.intValue = sharedPref.getInt("timeInterval", 1)    // 1 sec
        minDistance.intValue = sharedPref.getInt("minDistance", 10)     // 10 m

        sendInterval.intValue = sharedPref.getInt("sendInterval", 1200)
        sendPointMax.intValue = sharedPref.getInt(Constants.Settings.sendTracksPointsMaxKey, 100)

        device_Id.text = Track.getId()

        sendToNgw.isChecked = sharedPref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)
        if(sendToNgw.isChecked) {
            sendToNgw.isEnabled = true
        }
        else {
            sendToNgw.isEnabled = false
            val checkTrackerInNGW = object : Runnable {
                override fun run() {
                    // Check tracker is in web GIS
                    if (Track.isRegistered()) {
                        sendToNgw.isEnabled = true
                    } else {
                        sendToNgw.isEnabled = false
                        mHandler.postDelayed(this, 2000)
                    }
                }
            }

            if (checkPermission(this, Manifest.permission.INTERNET)) {
                // Permission is granted
                mHandler.post(checkTrackerInNGW)
            }
        }

        shareButton.setOnClickListener {
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

        createInNgwButton.setOnClickListener {
            // TODO: Dialog to create tracker in NGW
        }

        regenerateId.setOnClickListener {
            mRegenerateDialogIsShown = true
            showRegenerateDialog()
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
            device_Id.text = Track.getId(change)
            sendToNgw.isChecked = false
        }
    }

    override fun onPause() {
        super.onPause()
        // save settings
        val sharedPref = getDefaultSharedPreferences(this)
        with (sharedPref.edit()) {
            putBoolean("divTracksByDay", divTrackByDay.isChecked)
            putInt("timeInterval", timeInterval.intValue)
            putInt("minDistance", minDistance.intValue)
            putInt("sendInterval", sendInterval.intValue)
            putInt(Constants.Settings.sendTracksPointsMaxKey, sendPointMax.intValue)
            putBoolean(Constants.Settings.sendTracksToNGWKey, sendToNgw.isChecked)
            commit()
        }
        // restart service if it is running
        startService(this, TrackerService.Command.UPDATE)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean("showRegenerateDialog", mRegenerateDialogIsShown)
    }


    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        mRegenerateDialogIsShown = savedInstanceState?.getBoolean("showRegenerateDialog") ?: false
        if(mRegenerateDialogIsShown) {
            showRegenerateDialog()
        }
    }
}