package com.womanglobal.connecther

import ApiServiceFactory
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.adapters.SearchAdapter
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.ActivitySearchBinding
import com.womanglobal.connecther.services.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var apiService: ApiService
    private lateinit var searchAdapter: SearchAdapter
    private val searchResults = mutableListOf<User>()

    companion object {
        private const val VOICE_SEARCH_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiService = ApiServiceFactory.createApiService()

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
        apiService.searchUsers(query).enqueue(object : Callback<List<User>> {
            override fun onResponse(call: Call<List<User>>, response: Response<List<User>>) {
                if (response.isSuccessful) {
                    searchResults.clear()
                    searchResults.addAll(response.body() ?: emptyList())
                    searchAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this@SearchActivity, "No results found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<User>>, t: Throwable) {
                Toast.makeText(this@SearchActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
        startActivityForResult(intent, VOICE_SEARCH_REQUEST_CODE)
    }

    /**
     * Handles the voice search result
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_SEARCH_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!result.isNullOrEmpty()) {
                binding.searchField.setText(result[0]) // Set recognized text to search field
                searchUsers(result[0]) // Perform search
            }
        }
    }
}
