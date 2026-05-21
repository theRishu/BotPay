package com.qwe7002.telegram_sms

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 4
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> TodayFragment()
        1 -> WeekSwiperFragment()
        2 -> SummaryFragment()
        else -> HistoryFragment()
    }
}
