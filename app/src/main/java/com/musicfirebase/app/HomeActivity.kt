package com.musicfirebase.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.musicfirebase.app.adapter.SongAdapter
import com.musicfirebase.app.databinding.ActivityHomeBinding
import com.musicfirebase.app.model.Song

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var songAdapter: SongAdapter
    private val songs = mutableListOf<Song>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupAddButton()
        loadSongsFromFirebase()
    }
    override fun onResume() {
        super.onResume()
        loadSongsFromFirebase()
    }
    override fun onRestart() {
        super.onRestart()
        loadSongsFromFirebase()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(songs) { song, position ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("song_name", song.name)
            intent.putExtra("song_url", song.url)
            intent.putExtra("song_position", position)
            intent.putExtra("songs_list", ArrayList(songs))
            startActivity(intent)
        }
        binding.recyclerViewSongs.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = songAdapter
        }
    }

    private fun setupAddButton() {
        binding.btnAddSong.setOnClickListener {
            showAddSongDialog()
        }
    }

    private fun showAddSongDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val nameEditText = EditText(this).apply {
            hint = "Song Name"
        }

        val urlEditText = EditText(this).apply {
            hint = "Song URL (MP3 link)"
        }

        dialogView.addView(nameEditText)
        dialogView.addView(urlEditText)

        AlertDialog.Builder(this)
            .setTitle("Add New Song")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val url = urlEditText.text.toString().trim()

                if (name.isNotEmpty() && url.isNotEmpty() && isValidUrl(url)) {
                    addSongToFirebase(name, url)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun isValidUrl(url: String): Boolean {
        return try {
            val validUrl = java.net.URL(url)
            val protocol = validUrl.protocol.lowercase()
            if (protocol != "http" && protocol != "https") {
                return false
            }
            val path = validUrl.path.lowercase()
            val audioExtensions = listOf(
                ".mp3", ".wav", ".flac", ".aac", ".ogg",
                ".m4a", ".wma", ".opus", ".mp4", ".webm"
            )
            audioExtensions.any { extension -> path.endsWith(extension) }
        } catch (e: Exception) {
            Log.e("ErrorURLValidation","Error was: "+e.message.toString())
            false
        }
    }

    private fun addSongToFirebase(name: String, url: String) {
        val song = hashMapOf(
            "name" to name,
            "url" to url
        )

        db.collection("songs")
            .add(song)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Song added successfully!", Toast.LENGTH_LONG).show()
                loadSongsFromFirebase()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding song: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadSongsFromFirebase() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        db.collection("songs")
            .get()
            .addOnSuccessListener { documents ->
                songs.clear()
                for (document in documents) {
                    val song = document.toObject(Song::class.java)
                    songs.add(song.copy(id = document.id))
                }
                songAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = android.view.View.GONE

                if (songs.isEmpty()) {
                    binding.textWhenLibraryIsEmpty.visibility = android.view.View.VISIBLE
                } else {
                    binding.textWhenLibraryIsEmpty.visibility = android.view.View.GONE
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error loading songs: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.progressBar.visibility = android.view.View.GONE
            }
    }
}