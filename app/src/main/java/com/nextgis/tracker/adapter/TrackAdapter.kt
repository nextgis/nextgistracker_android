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

package com.nextgis.tracker.adapter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.nextgis.maplib.*
import com.nextgis.tracker.MainApplication
import com.nextgis.tracker.R
import com.nextgis.tracker.activity.MainActivity.const.REQUEST_SAVE_FILE
import com.nextgis.tracker.databinding.TrackViewBinding
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private var continueExecution = true
private var exportTask: TrackAdapter.ExportToGPXAsyncTask? = null

@Suppress("UNUSED_PARAMETER")
fun progressCallback(status: StatusCode, complete: Double, message: String) : Boolean {
    exportTask?.publishProgress(complete, message)
    return continueExecution
}

class TrackAdapter(private val context: Context,
                   private val tracksTable: Track,
                   private val onItemClick: (TrackInfo, View) -> Unit
) :
    RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

        fun setOnProgress(isOnProgress:Boolean, trackName : String, trackStartTime : Date){
            topInProgress = isOnProgress
            progressTrackName = trackName
            progressTrackStartTime = trackStartTime
        }


    private var topInProgress = false
    private var  progressTrackName = ""
    private var progressTrackStartTime = Date()
    private var tracks: Array<TrackInfo> = tracksTable.getTracks()


    inner class TrackViewHolder(val binding: TrackViewBinding,
                                onItemClicked: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root){
        init {
            binding.clickArea.setOnClickListener{// click all area exept  share image
                onItemClicked(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackAdapter.TrackViewHolder {
        // create a new view
        val binding = TrackViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding) {
            val newPosition = tracks.size - it - 1
            onItemClick(tracks[newPosition], binding.clickArea)
        }
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        // Reverse sort
        val newPosition = tracks.size - position - 1
        val context = holder.itemView.context

        val formatter = DateFormat.getTimeInstance()
        formatter.timeZone = TimeZone.getDefault()

        with (holder){

            if (topInProgress && position==0
                && progressTrackName.equals(tracks[newPosition].name) &&
                progressTrackStartTime.time <= tracks[newPosition].start.time)
                binding.trackImage.setImageResource(com.nextgis.maplib.R.drawable.ic_track_active)
            else
                binding.trackImage.setImageResource(R.drawable.ic_track)
            binding.trackName.text = tracks[newPosition].name
            binding.trackDescription.text = createDescription(context, tracks[newPosition].start, tracks[newPosition].stop)
            binding.shareImage.setOnClickListener {

                val savedTrackFileName = "track_" + getStartDateDescription(context, tracks[newPosition].start).lowercase()
                    .replace(" ", "_")
                    .replace(",", "") + "_" +
                 getStartTimeDescription(context, tracks[newPosition].start)
                    .replace(" ", "_")
                    .replace(",", "") + ".gpx"


                shareGPX(tracks[newPosition].start, tracks[newPosition].stop, tracks[newPosition].name,
                    savedTrackFileName)
            }
        }
    }

    override fun getItemCount() = tracks.size

    fun refresh() {
        tracks = tracksTable.getTracks()
        notifyDataSetChanged()
    }

    private fun createDescription(context: Context, start: Date, stop: Date) : String {
        val diff = stop.time - start.time
        val days = diff / Constants.millisecondsPerDay
        if(days > 0) {
            return context.getString(R.string.track_days).format(days)
        }
        val formatter = DateFormat.getTimeInstance()
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(start) + " - " + formatter.format(stop)
    }

    private fun getStartDateDescription(context: Context, start: Date) : String {

        val  DATE_TIME_FORMAT = "yyyy-MM-dd"
        val dateFormatter = SimpleDateFormat(DATE_TIME_FORMAT)
        return dateFormatter.format(start)


//        val formatter = DateFormat.getTimeInstance()
//        formatter.timeZone = TimeZone.getDefault()
//        return formatter.format(start)
    }

    private fun getStartTimeDescription(context: Context, start: Date) : String {

        val  DATE_TIME_FORMAT = "HH:mm"
        val dateFormatter = SimpleDateFormat(DATE_TIME_FORMAT)
        return dateFormatter.format(start)


//        val formatter = DateFormat.getTimeInstance()
//        formatter.timeZone = TimeZone.getDefault()
//        return formatter.format(start)
    }


    inner class ExportToGPXAsyncTask(private val start: Date,
                                     private val stop: Date,
                                     private val name: String,
                                     private val mDisplayedTrackName:String) :
        AsyncTask<Void, Int, String>() {

        private var currentMessage = ""
        private var progressDialog: AlertDialog? = null
        val displayedTrackName = mDisplayedTrackName

        fun publishProgress(complete: Double, message: String) {
            currentMessage = message
            publishProgress((complete * 100).toInt())
        }

        override fun onPreExecute() {
            super.onPreExecute()
            continueExecution = true
            exportTask = this
            val builder = AlertDialog.Builder(context)
            builder.setView(R.layout.share_gpx_progress)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    continueExecution = false
                }

            progressDialog = builder.create()
            progressDialog?.show()
        }

        @Synchronized
        @Throws(RuntimeException::class)
        fun createDir(dir: File) {
            if (dir.exists()) {
                return
            }
            if (!dir.mkdirs()) {
                throw RuntimeException("Can not create dir $dir")
            }
        }

        override fun doInBackground(vararg p: Void): String {

            var result = ""
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val newName = "track_${sdf.format(start)}"
            val tmp = API.getTmpDirectory()
            // Get or create share directory in tmp folder. Path to share directory is in file provider config.
            var tmpShare = tmp?.child("share")
            if(tmpShare == null) {
                tmpShare = tmp?.createDirectory("share")
            }
            if(tmpShare != null) {
                if (tracksTable.export(start, stop, newName, tmpShare, ::progressCallback)) {
                    val properties = tmpShare.getProperties()
                    val systemPath = properties["system_path"]
                    //result = "$systemPath/$newName.gpx"
                    result = "$systemPath/nga_tracks_pt.gpx"
                }
            }
            // If user click cancel, but callback not intercept it
            if(!continueExecution) {
                result = ""
            }
            return result
        }

        override fun onProgressUpdate(vararg values: Int?) {
            super.onProgressUpdate(*values)

            val progressBar : ProgressBar? = progressDialog?.findViewById<ProgressBar>(R.id.loader)

            progressBar.let {
                progressBar?.progress = values[0] ?: 0
            }
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            progressDialog?.dismiss()
            if(result.isEmpty()) {
                // Show error message into a toast
                Toast.makeText(context, API.lastError(), Toast.LENGTH_SHORT).show()
            }
            else {
                val from: File = File(result)
                val to: File = File(from.parent + "/" + displayedTrackName)
                from.renameTo(to)
                val resultnew = to.absolutePath

                val gpxFile = File(resultnew)
                val fileUri: Uri? = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        gpxFile)
                } catch (e: IllegalArgumentException) {
                    Log.e("tracker","The selected file can't be shared: $resultnew")
                    Toast.makeText(context, R.string.error_export_track, Toast.LENGTH_SHORT).show()
                    null
                }

                if(fileUri != null) {

                    val shareIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.send_track).format(name))
                        type = "application/gpx+xml"
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }


                    val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, displayedTrackName)
                        //action = "SAVE_FILE"
                        putExtra("fileUri", fileUri)
                        type = "application/gpx+xml"

                    }
                    val chooserIntent = Intent.createChooser(shareIntent, "Share GPX File").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveIntent))
                    }

                    (context.applicationContext as MainApplication).setFileToSave(gpxFile)
                    (context as Activity).startActivityForResult(chooserIntent, REQUEST_SAVE_FILE)

//                    val fileName: String = "track.gpx"
//                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
//                    intent.addCategory(Intent.CATEGORY_OPENABLE)
//                    intent.setType("*/*")
//                    intent.putExtra(Intent.EXTRA_TITLE, fileName)
//                    (context as Activity).startActivityForResult(intent, REQUEST_SAVE_FILE)

                }
            }
            exportTask = null
        }
    }

    private fun shareGPX(start: Date, stop: Date, name: String, mDisplayedTrackName:String) {
        ExportToGPXAsyncTask(start, stop, name, mDisplayedTrackName).execute()
    }
}