package com.avaneesh.exportcontacts

import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {
    private val contactList = mutableStateListOf<Contact>()
    private val selectedIndices = mutableStateListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        if (ContextCompat.checkSelfPermission(this, READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                READ_CONTACTS,
                WRITE_EXTERNAL_STORAGE
            ), 1)
        } else {
            contactList.addAll(getContacts())
        }

        setContent {
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Export Contacts", style = MaterialTheme.typography.titleLarge)

                        val allSelected = selectedIndices.size == contactList.size
                        Button(onClick = {
                            if (allSelected) {
                                selectedIndices.clear()
                            } else {
                                selectedIndices.clear()
                                selectedIndices.addAll(contactList.indices)
                            }
                        }) {
                            Text(if (allSelected) "Unselect All" else "Select All")
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(contactList) { index, contact ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedIndices.contains(index),
                                    onCheckedChange = {
                                        if (it) selectedIndices.add(index) else selectedIndices.remove(index)
                                    }
                                )
                                Text("${contact.name} — ${contact.number}")
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val selectedContacts = selectedIndices.map { contactList[it] }
                            val file = createVCF(this@MainActivity, selectedContacts)
                            shareFile(this@MainActivity, file)
                        }) {
                            Text("Export All")
                        }

                        Button(onClick = {
                            val selectedContacts = selectedIndices.map { contactList[it] }
                            val files = createVCFBatches(this@MainActivity, selectedContacts)
                            shareFiles(this@MainActivity, files)
                        }) {
                            Text("Export in Batches")
                        }
                    }
                }
            }
        }
    }

    private fun getContacts(): List<Contact> {
        val contacts = mutableSetOf<Pair<String, String>>() // use Set to auto-remove dups

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)?.trim() ?: "Unknown"
                val number = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                if (number.isNotEmpty()) {
                    contacts.add(Pair(name, number))
                }
            }
        }

        return contacts.map { Contact(it.first, it.second) }
    }


    private fun createVCF(context: Context, contacts: List<Contact>): File {
        val file = File(context.getExternalFilesDir(null), "contacts.vcf")
        file.bufferedWriter().use { writer ->
            for (contact in contacts) {
                writer.write("BEGIN:VCARD\n")
                writer.write("VERSION:3.0\n")
                writer.write("FN:${contact.name}\n")
                writer.write("TEL:${contact.number}\n")
                writer.write("END:VCARD\n")
            }
        }
        return file
    }

    private fun createVCFBatches(context: Context, contacts: List<Contact>, batchSize: Int = 100): List<File> {
        val files = mutableListOf<File>()
        val chunks = contacts.chunked(batchSize)
        for ((index, chunk) in chunks.withIndex()) {
            val file = File(context.getExternalFilesDir(null), "contacts${index + 1}.vcf")
            file.bufferedWriter().use { writer ->
                for (contact in chunk) {
                    writer.write("BEGIN:VCARD\n")
                    writer.write("VERSION:3.0\n")
                    writer.write("FN:${contact.name}\n")
                    writer.write("TEL:${contact.number}\n")
                    writer.write("END:VCARD\n")
                }
            }
            files.add(file)
        }
        return files
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Contacts"))
    }

    private fun shareFiles(context: Context, files: List<File>) {
        val uris = files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/x-vcard"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Contacts Batches"))
    }

}

data class Contact(val name: String, val number: String)
