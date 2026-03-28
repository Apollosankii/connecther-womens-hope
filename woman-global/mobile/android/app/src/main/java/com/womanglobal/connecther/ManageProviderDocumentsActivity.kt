package com.womanglobal.connecther

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageProviderDocumentsActivity : AppCompatActivity() {
    private val key = "provider_docs"
    private val pickDocs = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            saveDocs(uris.map { it.toString() })
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_documents)
        findViewById<Toolbar>(R.id.toolbar).apply {
            title = "Manage documents"
            setNavigationIcon(R.drawable.arrow_back_24px)
            setNavigationOnClickListener { finish() }
            inflateMenu(R.menu.menu_manage_documents)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_add_document -> {
                        pickDocs.launch(arrayOf("*/*"))
                        true
                    }
                    else -> false
                }
            }
        }
        render()
    }

    private fun getDocs(): List<String> {
        val csv = getSharedPreferences("provider_docs_prefs", MODE_PRIVATE).getString(key, "") ?: ""
        return csv.split("|").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun saveDocs(docs: List<String>) {
        getSharedPreferences("provider_docs_prefs", MODE_PRIVATE).edit().putString(key, docs.joinToString("|")).apply()
    }

    private fun render() {
        val docs = getDocs()
        findViewById<TextView>(R.id.emptyText).visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        val rv = findViewById<RecyclerView>(R.id.recyclerDocuments)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SimpleTextListAdapter(docs.map { Uri.parse(it).lastPathSegment ?: it }) { index ->
            val updated = docs.toMutableList().apply { removeAt(index) }
            saveDocs(updated)
            render()
        }
    }
}
