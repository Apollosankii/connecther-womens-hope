package com.womanglobal.connecther

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProviderDocumentsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_documents)
        findViewById<Toolbar>(R.id.toolbar).apply {
            title = "Provider documents"
            setNavigationIcon(R.drawable.arrow_back_24px)
            setNavigationOnClickListener { finish() }
        }
        val docs = getSharedPreferences("provider_docs_prefs", MODE_PRIVATE)
            .getString("provider_docs", "")
            .orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        findViewById<TextView>(R.id.emptyText).visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerDocuments).apply {
            layoutManager = LinearLayoutManager(this@ProviderDocumentsActivity)
            adapter = SimpleTextListAdapter(docs, null)
        }
    }
}


