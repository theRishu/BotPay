package com.qwe7002.telegram_sms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

class WeekSwiperFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val pager = ViewPager2(requireContext())
        pager.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        pager.adapter = WeekPageAdapter(requireActivity())
        // position 0 = current week, swipe left to go to previous weeks
        pager.currentItem = 0
        return pager
    }
}
