package com.qwe7002.telegram_sms

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class WeekPageAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    // 52 weeks = 1 year of history
    override fun getItemCount() = 52
    override fun createFragment(position: Int): Fragment = WeekPageFragment.newInstance(position)
}
