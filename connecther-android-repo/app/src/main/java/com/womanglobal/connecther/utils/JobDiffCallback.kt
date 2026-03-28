package com.womanglobal.connecther.utils

import androidx.recyclerview.widget.DiffUtil
import com.womanglobal.connecther.data.Job

class JobDiffCallback(
    private val oldList: List<Job>?,
    private val newList: List<Job>?
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList?.size ?: 0

    override fun getNewListSize(): Int = newList?.size ?: 0

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val o = oldList ?: return false
        val n = newList ?: return false
        if (oldItemPosition >= o.size || newItemPosition >= n.size) return false
        return o[oldItemPosition].job_id == n[newItemPosition].job_id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val o = oldList ?: return false
        val n = newList ?: return false
        if (oldItemPosition >= o.size || newItemPosition >= n.size) return false
        return o[oldItemPosition] == n[newItemPosition]
    }
}
