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
//const val CATLOG_FILE = "catlog_current_file"
//const val FILE_FLDR = "logcat"

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var connection: Object? = null
    private val stack = Stack<Object>()
    val path = NonNullObservableField("/")
    val current: Object? get() = if (stack.isNotEmpty()) stack.peek() else null

    private val mHandler = Handler()
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

        if (sharedPrefMain.getBoolean(Constants.Settings.sendTracksToNGWKey, false)){
            runAsync {
                if (!Track.isRegistered()){
                    sharedPrefMain.edit().remove(webGisNameKey).apply()
                    // turn send off
                    runOnUiThread{
                        binding.sendToNgw.isChecked = false
                        updateSendToNGWPrompt(binding.syncNgwTv, getDefaultSharedPreferences(this))
                    }

                    with (sharedPref.edit()) {
                        putBoolean(Constants.Settings.sendTracksToNGWKey, false)
                        commit()
                    // alert
                        runOnUiThread{
                            AlertDialog.Builder(this@SettingsActivity)
                                .setTitle(R.string.tracker_error_header)
                                .setMessage(R.string.tracker_error_text )
                                .setPositiveButton(android.R.string.ok) { _, _ -> {}}
                                .create()
                                .show()
                        }
                    }
                }
            }
        }

        binding.sendToNgw.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                showProgress()
                runAsync {
                    // check internet
                    if (!isInternetAvailable(context = this)){
                        runOnUiThread {
                            hideProgress()
                            binding.sendToNgw.isChecked=false
                            Toast.makeText(getBaseContext(), R.string.no_internet, Toast.LENGTH_LONG).show()
                        }
                        return@runAsync
                    }
                    if (!Track.isRegistered()){
                        // start tracker creation - first login to NGW
                        runOnUiThread({
                            val intent = Intent(this, AddInstanceActivity::class.java)
                            startActivityForResult(intent, AddInstanceActivity.ADD_INSTANCE_TO_CREATE_TRACKER_REQUEST)
                        })

                    } else {
                        // tracker already registered
                        val sharedPref = getDefaultSharedPreferences(this)
                        with (sharedPref.edit()) {
                            putBoolean(Constants.Settings.sendTracksToNGWKey, binding.sendToNgw.isChecked)
                            apply()
                        }
                        runOnUiThread({
                            hideProgress()
                        })
                    }
                }
            } else {
                val sharedPref = getDefaultSharedPreferences(this)
                with (sharedPref.edit()) {
                    putBoolean(Constants.Settings.sendTracksToNGWKey, binding.sendToNgw.isChecked)
                    commit()
                }
            }
        }

        if(binding.sendToNgw.isChecked) {
            binding.sendToNgw.isEnabled = true
        }

        binding.shareId.setOnClickListener(){
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("uid", Track.getId())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied, Toast.LENGTH_LONG).show()
        }
    }

    fun updateSendToNGWPrompt(textView : TextView,  sharedPref : SharedPreferences){
        val gisName = sharedPref.getString(webGisNameKey, "")
        if (TextUtils.isEmpty(gisName)){
            //default text
            val prompt = getString(R.string.send_to_ngw_explanation) + " " + getString(R.string.send_to_ngw_explanation_webgis)
            textView.setText(prompt)
        } else {
            val prompt = getString(R.string.send_to_ngw_explanation) + " " + gisName
            textView.setText(prompt)
        }
    }

    fun showProgress(){
        binding.progressloaderarea.visibility = View.VISIBLE
    }

    fun hideProgress(){
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
                showProgress()
                runAsync {
                    if (resultCode == Activity.RESULT_OK) {
                        runOnUiThread { showProgress() }
                        val instanceName = getInstanceURL()
                        instanceName.let {
                            val title = instanceName?.replace(".wconn", "")
                            val rootList = root(instanceName!!)

                            var trackerFolder: Object? = null
                            for (item in rootList) {
                                if ((item as Object).type == Object.Type.CONTAINER_NGWTRACKERGROUP.code) {
                                    trackerFolder = item
                                    break
                                }
                            }
                            if (trackerFolder == null) {
                                connection?.let {
                                    trackerFolder =
                                        NGWResourceGroup(it).createTrackerGroup("TrackersGroup")
                                    if (trackerFolder == null) {
                                        printError("tracker folder cannot be create: " + API.lastError())
                                        // start manual create
                                        runOnUiThread {  startManualTrackerCreate(true) }
                                    }
                                }
                            } else {
                                trackerFolder?.let {
                                    val id = Track.getId(false)
                                    val resultTracker = NGWTrackerGroup(it).createTracker(
                                        "my_tracker_" + id,
                                        tracker_id = id
                                    )
                                    if (resultTracker == null) {
                                        printError("tracker create failed: " + API.lastError())
                                        // start manual create
                                        runOnUiThread{ startManualTrackerCreate(false) }

                                    } else {
                                        val sharedPrefForTitle = getDefaultSharedPreferences(this)
                                        with(sharedPrefForTitle.edit()) {
                                            putString(Constants.Settings.webGisNameKey, title)
                                            commit()
                                        }
                                        runOnUiThread {
                                            Toast.makeText(
                                            this,
                                            R.string.tracker_created,
                                            Toast.LENGTH_LONG
                                        ).show()
                                            updateSendToNGWPrompt(binding.syncNgwTv, getDefaultSharedPreferences(this))

                                        }

                                        val sharedPref = getDefaultSharedPreferences(this)
                                        with(sharedPref.edit()) {
                                            putBoolean(Constants.Settings.sendTracksToNGWKey, true)
                                            commit()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        runOnUiThread{
                            binding.sendToNgw.isChecked = false
                        }

                    }
                    runOnUiThread {  hideProgress() }
                }
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

    private fun startManualTrackerCreate(isFolderError:Boolean ){
        binding.sendToNgw.isChecked = false
        AlertDialog.Builder(this)
            .setTitle(R.string.tracker_error_header)
            .setMessage(if (isFolderError) "\n" + getString(R.string.tracker_folder_unable_created)
                    + API.lastError() else getString(R.string.tracker_unable_created) + API.lastError())
            .setPositiveButton(R.string.create_by_manual) { _, _ ->
                getInstanceURL()?.let { launchContentSelector(it)}
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> {} }
            .create()
            .show()
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
        return API.getCatalog()?.children()?.firstOrNull { it.type == 72 }?.children()?.lastOrNull()?.name
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

    fun root(instanceName:String): List<Object> {
        val children = listOf<Object>()
        API.getCatalog()?.children()?.let {
            instanceName?.let { name ->
                for (child in it)
                    if (child.type == 72) {
                        var count = 0
                        for (connection in child.children()){
                            count++
                        }
                        for (connection in child.children())
                            if (connection.name.startsWith(name)) {
                                API.setProperty("http/timeout", "2500")
                                this.connection = Object.forceChildToNGWResourceGroup(connection)
                                val list = connection.children().toList()
                                return list
                            }
                    }
            }
            return arrayListOf()
        }
        return children
    }
}