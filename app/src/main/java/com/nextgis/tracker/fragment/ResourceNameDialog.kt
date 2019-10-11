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
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.nextgis.maplib.util.NonNullObservableField
import com.nextgis.tracker.R
import com.nextgis.tracker.databinding.DialogResourceNameBinding


class ResourceNameDialog() : DialogFragment() {
    private lateinit var listener: (name: String) -> Unit
    private lateinit var binding: DialogResourceNameBinding
    val resource = NonNullObservableField("")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.dialog_resource_name, null, false)
        dialog.setContentView(binding.root)
        binding.fragment = this

        return dialog
    }

    fun show(activity: FragmentActivity?, listener: (name: String) -> Unit) {
        this.listener = listener
        activity?.let {
            show(it.supportFragmentManager, TAG)
        }
    }

    fun add() {
        if (resource.get().isBlank()) {
            Toast.makeText(context, R.string.empty_field, Toast.LENGTH_SHORT).show()
            return
        }
        listener.invoke(resource.get())
        dismiss()
    }

    fun cancel() {
        dismiss()
    }

    companion object {
        const val TAG = "ResourceNameDialog"
    }
}