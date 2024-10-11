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

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nextgis.maplib.*
import com.nextgis.maplib.activity.PickerActivity
import com.nextgis.maplib.fragment.FilePickerFragment
import com.nextgis.tracker.R
import com.nextgis.tracker.databinding.ActivityInstanceContentBinding
import com.nextgis.tracker.fragment.AddTrackerDialog
import com.nextgis.tracker.fragment.ResourceNameDialog


class ContentInstanceActivity : AppCompatActivity(), PickerActivity {
    private var instanceName: String? = null
    private var connection: Object? = null

    private lateinit var binding: ActivityInstanceContentBinding
    private var trackerCreated = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInstanceContentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { add() }

        instanceName = intent?.extras?.getString("instance")
        title = instanceName?.replace(".wconn", "")

        val picker = FilePickerFragment()
        picker.setTracker() // set tracker use
        supportFragmentManager.beginTransaction().add(R.id.container, picker, "PickerFragment")
            .commit()
    }
    internal val parent: Object?
        get() {
            (supportFragmentManager.findFragmentByTag("PickerFragment") as? FilePickerFragment)?.current?.let { return it }
            return connection
        }

    override fun onLayerSelected(file: Object?) {

    }

    override fun root(): List<Object> {
        runOnUiThread{
            binding.loader.setOnClickListener {  }
            binding.loader.visibility = View.VISIBLE
        }
        //Thread.sleep(4_000)
        val children = listOf<Object>()
        API.getCatalog()?.children()?.let {
            instanceName?.let { name ->
                for (child in it)
                    if (child.type == 72) {
                        for (connection in child.children())
                            if (connection.name.startsWith(name)) {
                                API.setProperty("http/timeout", "2500")
                                this.connection = Object.forceChildToNGWResourceGroup(connection)
                                val list = connection.children().toList()
                                runOnUiThread {
                                    binding.loader.visibility = View.GONE
                                }
                                return list
                            }
                    }
            }
            runOnUiThread {
                binding.loader.visibility = View.GONE
            }
            return arrayListOf()
        }
        runOnUiThread {
            binding.loader.visibility = View.GONE
        }
        return children
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // check fragment back action
                val filepickerFragment = supportFragmentManager.findFragmentByTag("PickerFragment") as FilePickerFragment
                if (filepickerFragment == null || !filepickerFragment.processback(true)){
                    finish()
                    true
                } else {
                    return true
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun add() {
        AddTrackerDialog().show(this)
    }

    fun createFolder() {
        parent?.let {
            ResourceNameDialog().show(this) { name ->
                refreshOrError(NGWResourceGroup(it).createResourceGroup(name))
            }
        }
    }

    fun createTrackerGroup() {
        parent?.let {
            ResourceNameDialog().show(this) { name ->
                refreshOrError(NGWResourceGroup(it).createTrackerGroup(name))
            }
        }
    }

    fun addTracker() {
        parent?.let {
            ResourceNameDialog().show(this) { name ->
                refreshOrError(
                    NGWTrackerGroup(it).createTracker(name, tracker_id = Track.getId(false))
                )
            }
        }
    }

    private fun refreshOrError(result: Object?) {
        (supportFragmentManager.findFragmentByTag("PickerFragment") as? FilePickerFragment)?.refresh()
        if (result == null) {
            runOnUiThread {
                Toast.makeText(this, API.lastError(), Toast.LENGTH_SHORT).show()
            }
        }
    }
}