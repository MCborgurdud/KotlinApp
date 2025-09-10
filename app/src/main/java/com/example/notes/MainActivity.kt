package com.example.notes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notes.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var noteAdapter: NoteAdapter
    private val notes = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteAdapter = NoteAdapter(notes) { note ->
            notes.remove(note)
            noteAdapter.notifyDataSetChanged()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = noteAdapter

        binding.addButton.setOnClickListener {
            val note = binding.inputNote.text.toString()
            if (note.isNotEmpty()) {
                notes.add(note)
                noteAdapter.notifyDataSetChanged()
                binding.inputNote.text.clear()
            }
        }
    }
}
