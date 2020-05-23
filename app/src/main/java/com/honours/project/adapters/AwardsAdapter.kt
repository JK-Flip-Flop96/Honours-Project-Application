package com.honours.project.adapters

import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.storage.FirebaseStorage
import com.honours.project.R
import com.honours.project.models.Award
import com.honours.project.models.Report
import com.squareup.picasso.Picasso

class AwardsAdapter (private val data: ArrayList<Award>) :
    RecyclerView.Adapter<AwardsAdapter.ContributionsViewHolder>() {

    class ContributionsViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContributionsViewHolder {
        val host = LayoutInflater.from(parent.context)
            .inflate(R.layout.awards_card, parent, false)
        return ContributionsViewHolder(host)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: ContributionsViewHolder, position: Int) {
        val award = data[position]
        val star = ResourcesCompat.getDrawable(
            holder.itemView.context.resources,
            R.drawable.ic_stars_black_24dp, null)

        if (star != null) {
            star.mutate()
            if (award.awarded) {
                star.colorFilter = PorterDuffColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP)
            } else {
                star.colorFilter = PorterDuffColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_ATOP)
            }
        }

        holder.view.findViewById<ImageView>(R.id.award_badge).setImageDrawable(star)
        holder.view.findViewById<TextView>(R.id.award_name).text = award.title
        holder.view.findViewById<TextView>(R.id.award_desc). text = award.desc
    }
}