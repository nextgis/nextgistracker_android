/*
 * Project:  NextGIS Tracker
 * Purpose:  Software tracker for nextgis.com cloud
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ****************************************************************************
 * Copyright (c) 2019 NextGIS <info@nextgis.com>
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

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.nextgis.maplib.API
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Constants.Settings.webGisNameKey
import com.nextgis.maplib.NGWResourceGroup
import com.nextgis.maplib.NGWTrackerGroup
import com.nextgis.maplib.Object
import com.nextgis.maplib.Track
import com.nextgis.maplib.activity.AddInstanceActivity
import com.nextgis.maplib.printError
import com.nextgis.maplib.util.isInternetAvailable
import com.nextgis.maplib.util.runAsync
import com.nextgis.tracker.R

const val AUTHORITY = "com.nextgis.tracker"
const val ACCOUNT_TYPE = "com.nextgis.account3"
const val ACCOUNT = "NextGIS Tracker"

abstract class BaseActivity : AppCompatActivity() {
    private var connection: Object? = null

    protected fun disableSync() {
        getAccount()?.let {
            ContentResolver.setSyncAutomatically(it, AUTHORITY, false)
            ContentResolver.removePeriodicSync(it, AUTHORITY, Bundle.EMPTY)
        }
    }

    protected fun enableSync(interval: Long) {
//        val account = getAccount() ?: createSyncAccount()
//        ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
//        ContentResolver.setIsSyncable(account, AUTHORITY, 1)
//        ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, interval)
    }

    @SuppressLint("MissingPermission")
    private fun getAccount(): Account? {
        val accountManager = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
        return accounts.firstOrNull()
    }

    private fun createSyncAccount(): Account {
        val userData = Bundle()
        userData.putString("url", "sdfdsfdsf")
        userData.putString("login", "ddddd")
        val accountManager = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        return Account(ACCOUNT, ACCOUNT_TYPE).also { newAccount ->
            if (!accountManager.addAccountExplicitly(newAccount, "22", userData)) {
                Toast.makeText(this, R.string.create_account_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

//    public fun startManualSync(){
//            val account = getAccount() ?: createSyncAccount()
//            val settingsBundle = Bundle().apply {
//                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
//                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
//            }
//            ContentResolver.requestSync(account, AUTHORITY, settingsBundle);
//    }

    public fun setupOnSendClick(sendToNGWSwitch : SwitchCompat){
        if (sendToNGWSwitch == null)
            return
        sendToNGWSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                showProgress()
                runAsync {
                    // check internet
                    if (!isInternetAvailable(context = this)){
                        runOnUiThread {
                            hideProgress()
                            sendToNGWSwitch.isChecked=false
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
                            putBoolean(Constants.Settings.sendTracksToNGWKey, sendToNGWSwitch.isChecked)
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
                    putBoolean(Constants.Settings.sendTracksToNGWKey, sendToNGWSwitch.isChecked)
                    commit()
                }
            }
        }
    }

    public fun createTracker(resultCode: Int, sendToNGWSwitch : SwitchCompat,  sendToNGWTextView : TextView?){
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
                                runOnUiThread {
                                    startManualTrackerCreate(true, sendToNGWSwitch) }
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
                                runOnUiThread{ startManualTrackerCreate(false, sendToNGWSwitch) }

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
                                    updateSendToNGWPrompt(sendToNGWTextView, getDefaultSharedPreferences(this))

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
                    sendToNGWSwitch.isChecked = false
                }

            }
            runOnUiThread {  hideProgress() }
        }
    }

    fun updateSendToNGWPrompt(textView : TextView?, sharedPref : SharedPreferences){
        if (textView != null ) {
            val gisName = sharedPref.getString(webGisNameKey, "")
            if (TextUtils.isEmpty(gisName)) {
                //default text
                val prompt =
                    getString(R.string.send_to_ngw_explanation) + " " + getString(R.string.send_to_ngw_explanation_webgis)
                textView.setText(prompt)
            } else {
                val prompt = getString(R.string.send_to_ngw_explanation) + " " + gisName
                textView.setText(prompt)
            }
        }
    }

    private fun startManualTrackerCreate(isFolderError:Boolean, sendToNGWClick : SwitchCompat ){
        sendToNGWClick.isChecked = false
        val errorApiMessage = API.lastError()
        var message = ""
        if (isFolderError) message = "\n" + getString(R.string.tracker_folder_unable_created) + API.lastError()
        if (!isFolderError) {
         if (TextUtils.isEmpty(errorApiMessage))
             message=  getString(R.string.tracker_unable_created)
            else
                message = errorApiMessage
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.tracker_error_header)
            .setMessage(message)
            .setPositiveButton(R.string.create_by_manual) { _, _ ->
                getInstanceURL()?.let { launchContentSelector(it)}
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> {} }
            .create()
            .show()
    }

    public fun launchContentSelector(instance: String) {
        val intent = Intent(this, ContentInstanceActivity::class.java)
        intent.putExtra("instance", instance)
        startActivityForResult(intent, CONTENT_ACTIVITY)
    }

    public fun getInstanceURL(): String? {
        return API.getCatalog()?.children()?.firstOrNull { it.type == 72 }?.children()?.lastOrNull()?.name
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


    abstract fun showProgress()

    abstract fun hideProgress()


    public fun checkSendStillAvailable(pref: SharedPreferences, sendSwitcher : SwitchCompat?, sendingText: TextView?) {
        if (sendSwitcher == null)
            return
        if (pref.getBoolean(Constants.Settings.sendTracksToNGWKey, false)) {
            runAsync {
                if (!Track.isRegistered()) {
                    pref.edit().remove(webGisNameKey).apply()
                    // turn send off
                    runOnUiThread {
                        sendSwitcher.isChecked = false
                        if (sendingText != null)
                            updateSendToNGWPrompt(sendingText, getDefaultSharedPreferences(this))
                    }

                    with(pref.edit()) {
                        putBoolean(com.nextgis.maplib.Constants.Settings.sendTracksToNGWKey, false)
                        commit()
                        // alert
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this@BaseActivity)
                                .setTitle(com.nextgis.tracker.R.string.tracker_error_header)
                                .setMessage(com.nextgis.tracker.R.string.tracker_error_text)
                                .setPositiveButton(android.R.string.ok) { _, _ -> {} }
                                .create()
                                .show()
                        }
                    }
                }
            }
        }
    }
}