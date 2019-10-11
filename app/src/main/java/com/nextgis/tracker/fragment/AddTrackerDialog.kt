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

package com.nextgis.tracker.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nextgis.tracker.R
import com.nextgis.tracker.activity.ContentInstanceActivity


class AddTrackerDialog : BottomSheetDialogFragment() {
    private var activity: ContentInstanceActivity? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tracker, null, false)
        dialog.setContentView(view)
        val parent = activity?.isParentTracker ?: false

        val folder = view.findViewById<TextView>(R.id.create_folder)
        folder.setOnClickListener {
            if (parent) {
                Toast.makeText(context, R.string.parent_tracker_not, Toast.LENGTH_LONG).show()
            } else {
                dismiss()
                activity?.createFolder()
            }
        }

        val group = view.findViewById<TextView>(R.id.create_group)
        group.setOnClickListener {
            if (parent) {
                Toast.makeText(context, R.string.parent_tracker_not, Toast.LENGTH_LONG).show()
            } else {
                dismiss()
                activity?.createTrackerGroup()
            }
        }

        val tracker = view.findViewById<TextView>(R.id.add_tracker)
        if (!parent) {
            tracker.setTextColor(Color.GRAY)
            tracker.setBackgroundColor(Color.WHITE)
        } else {
            folder.setTextColor(Color.GRAY)
            folder.setBackgroundColor(Color.WHITE)
            group.setTextColor(Color.GRAY)
            group.setBackgroundColor(Color.WHITE)
        }

        tracker.setOnClickListener {
            if (parent) {
                dismiss()
                activity?.addTracker()
            } else {
                Toast.makeText(context, R.string.parent_tracker, Toast.LENGTH_LONG).show()
            }
        }
        return dialog
    }

    fun show(activity: ContentInstanceActivity) {
        this.activity = activity
        show(activity.supportFragmentManager, TAG)
    }

    companion object {
        const val TAG = "AddTrackerBottomSheet"
    }
}