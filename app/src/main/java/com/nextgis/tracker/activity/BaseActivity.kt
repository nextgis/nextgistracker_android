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
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nextgis.maplib.Constants
import com.nextgis.maplib.Constants.Settings.sendIntervalKey
import com.nextgis.tracker.R

const val AUTHORITY = "com.nextgis.tracker"
const val ACCOUNT_TYPE = "com.nextgis.account3"
const val ACCOUNT = "NextGIS Tracker"


abstract class BaseActivity : AppCompatActivity() {
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


    public fun startManualSync(){
            val account = getAccount() ?: createSyncAccount()
            val settingsBundle = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(account, AUTHORITY, settingsBundle);
    }

}