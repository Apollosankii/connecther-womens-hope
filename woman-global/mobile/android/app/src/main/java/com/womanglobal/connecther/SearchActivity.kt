package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.adapters.SearchAdapter
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.ActivitySearchBinding
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchAdapter: SearchAdapter
    private val searchResults = mutableListOf<User>()
    private var searchJob: Job? = null

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                binding.searchField.setText(spoken)
                searchUsers(spoken)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        focusSearchField() // Auto-focus and show keyboard

        // Get search query from intent (if any)
        val query = intent.getStringExtra("query") ?: ""
        binding.searchField.setText(query)

        // Handle search input
        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchQuery = s.toString().trim()
                if (searchQuery.isNotEmpty()) {
                    searchUsers(searchQuery)
                } else {
                    searchResults.clear()
                    searchAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle audio search button
        binding.audioSearchButton.setOnClickListener {
            startVoiceSearch()
        }

        // Handle back button
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(searchResults, "", "")
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchRecyclerView.adapter = searchAdapter
    }

    private fun searchUsers(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300)
            val results = SupabaseData.searchUsers(query)
            searchResults.clear()
            searchResults.addAll(results)
            searchAdapter = SearchAdapter(searchResults, query, "")
            binding.searchRecyclerView.adapter = searchAdapter
            if (results.isEmpty()) {
                Toast.makeText(this@SearchActivity, "No results found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Automatically focuses the search field and shows the keyboard
     */
    private fun focusSearchField() {
        binding.searchField.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchField, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Starts voice recognition for search
     */
    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something to search")
        }
        voiceSearchLauncher.launch(intent)
    }
}
