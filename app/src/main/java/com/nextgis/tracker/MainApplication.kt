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

package com.nextgis.tracker

import android.app.Application
import java.io.File
import java.lang.ref.WeakReference

class MainApplication : Application() {

    protected var fileToSave: WeakReference<File>? = WeakReference(null)

    override fun onCreate() {
        super.onCreate()
    }

    public fun setFileToSave(file: File){
        fileToSave = WeakReference(file)
    }

    fun getFileToSave() : File? {
        return fileToSave?.get()
    }





}