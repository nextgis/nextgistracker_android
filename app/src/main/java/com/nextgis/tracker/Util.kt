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

package com.nextgis.tracker
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import android.widget.TextView
import com.nextgis.maplib.service.TrackerService
import com.nextgis.maplib.startTrackerService
import com.nextgis.tracker.activity.MainActivity

internal fun startService(context: Context, command: TrackerService.Command, options: Map<String, String> = mapOf()) {
    if (command == TrackerService.Command.START) {
        startTrackerService(context, command, Intent(context, MainActivity::class.java), options)
    } else {
        startTrackerService(context, command, null, options)
    }
}

internal fun highlightText(textView: TextView) {
    val text = textView.text
    val spannableString = SpannableString(text)
    spannableString.setSpan(
        URLSpan(""), 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    textView.setText(spannableString, TextView.BufferType.SPANNABLE)
}
