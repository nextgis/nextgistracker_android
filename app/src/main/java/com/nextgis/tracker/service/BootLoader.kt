/*
 * Project:  NextGIS Tracker
 * Purpose:  Software tracker for nextgis.com cloud
 * Author:   Dmitry Baryshnikov <dmitry.baryshnikov@nextgis.com>
 * ****************************************************************************
 * Copyright (c) 2018-2024 NextGIS <info@nextgis.com>
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
package com.nextgis.tracker.service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
//import com.nextgis.maplib.Constants.Settings.restoreTrackAfterRebootKey
import com.nextgis.maplib.Constants.Settings.trackInProgress
import com.nextgis.tracker.startService
import com.nextgis.maplib.service.TrackerService
import java.util.HashMap

class BootLoader : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val boot = Intent.ACTION_BOOT_COMPLETED
        if (intent != null && intent.action != null && intent.action == boot) checkTrackerService(
            context
        )
    }

    fun checkTrackerService(context: Context?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
//        val restoreTrack = preferences.getBoolean(restoreTrackAfterRebootKey, false)
//        if (restoreTrack ) {
            val trackInProgressBool = preferences.getBoolean(trackInProgress, false)
            if (trackInProgressBool) {
                val map: HashMap<String, String> = HashMap()
                map.put(trackInProgress, "true")
                startService(context!!, TrackerService.Command.START, map)
            }
        //}
    }
}
