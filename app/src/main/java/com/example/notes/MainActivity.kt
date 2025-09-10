package com.example.notes

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var noteAdapter: NoteAdapter
    private val notes = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val inputNote = findViewById<EditText>(R.id.inputNote)
        val addButton = findViewById<Button>(R.id.addButton)

        noteAdapter = NoteAdapter(notes) { note ->
            notes.remove(note)
            noteAdapter.notifyDataSetChanged()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = noteAdapter

        addButton.setOnClickListener {
            val note = inputNote.text.toString()
            if (note.isNotEmpty()) {
                notes.add(note)
                noteAdapter.notifyDataSetChanged()
                inputNote.text.clear()
            }
        }
    }
}
