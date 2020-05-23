package com.honours.project.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.storage.FirebaseStorage
import com.honours.project.R
import com.honours.project.models.Report
import com.squareup.picasso.Picasso
import java.util.*

class ContributionsAdapter (private val data: ArrayList<Report>) :
    RecyclerView.Adapter<ContributionsAdapter.ContributionsViewHolder>() {

    class ContributionsViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContributionsViewHolder {
        val host = LayoutInflater.from(parent.context)
            .inflate(R.layout.contributions_card, parent, false)
        return ContributionsViewHolder(host)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: ContributionsViewHolder, position: Int) {
        val report = data[position]

        FirebaseStorage.getInstance()
            .reference.child("images/")
            .child(report.imgRef).downloadUrl.addOnSuccessListener {
            Picasso.get().load(it).fit().into(holder.view.findViewById<ImageView>(R.id.photo_view))
        }

        holder.view.findViewById<TextView>(R.id.category_text).text = report.category
        holder.view.findViewById<TextView>(R.id.time_text).text = report.time
        holder.view.findViewById<TextView>(R.id.description_text). text = report.description
    }
}