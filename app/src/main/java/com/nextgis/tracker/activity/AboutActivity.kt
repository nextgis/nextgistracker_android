package com.nextgis.tracker.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import com.nextgis.tracker.R
import com.nextgis.tracker.databinding.ActivityAboutBinding
import com.nextgis.tracker.highlightText
import java.lang.String

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        highlightText(binding.telegram)
        binding.telegram.setOnClickListener {
            try {
                val telegramPart = getString(R.string.tracker_details_telegram_url_bin)
                val telegram = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("tg://resolve?domain=$telegramPart")
                )
                startActivity(telegram)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(baseContext, R.string.not_installed, Toast.LENGTH_SHORT).show()
            }
        }

        val copyrText = String.format(getString(R.string.tracker_details_newngw))
        binding.createGis.text = Html.fromHtml(copyrText)
        binding.createGis.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun showProgress() {
        TODO("Not yet implemented")
    }

    override fun hideProgress() {
        TODO("Not yet implemented")
    }

}