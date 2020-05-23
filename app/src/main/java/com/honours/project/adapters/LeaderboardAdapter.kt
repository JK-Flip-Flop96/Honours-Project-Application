package com.honours.project.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.honours.project.R
import com.honours.project.models.User

class LeaderboardAdapter (private val data: ArrayList<User>) :
    RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    class LeaderboardViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val host = LayoutInflater.from(parent.context)
            .inflate(R.layout.leaderboard_card, parent, false)
        return LeaderboardViewHolder(host)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val positionText = "#${position + 1}"
        holder.view.findViewById<TextView>(R.id.rank_text).text = positionText
        holder.view.findViewById<TextView>(R.id.name_text).text = data[position].name
        holder.view.findViewById<TextView>(R.id.score_text).text = data[position].score.toString()
    }
}