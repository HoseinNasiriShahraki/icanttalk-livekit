package com.icanttalk.android.ui

import com.icanttalk.android.R

object AvatarCatalog {
    val ids = (1..37).map { it.toString().padStart(2, '0') }
    private val resources = listOf(
        R.drawable.pfp_01,R.drawable.pfp_02,R.drawable.pfp_03,R.drawable.pfp_04,R.drawable.pfp_05,
        R.drawable.pfp_06,R.drawable.pfp_07,R.drawable.pfp_08,R.drawable.pfp_09,R.drawable.pfp_10,
        R.drawable.pfp_11,R.drawable.pfp_12,R.drawable.pfp_13,R.drawable.pfp_14,R.drawable.pfp_15,
        R.drawable.pfp_16,R.drawable.pfp_17,R.drawable.pfp_18,R.drawable.pfp_19,R.drawable.pfp_20,
        R.drawable.pfp_21,R.drawable.pfp_22,R.drawable.pfp_23,R.drawable.pfp_24,R.drawable.pfp_25,
        R.drawable.pfp_26,R.drawable.pfp_27,R.drawable.pfp_28,R.drawable.pfp_29,R.drawable.pfp_30,
        R.drawable.pfp_31,R.drawable.pfp_32,R.drawable.pfp_33,R.drawable.pfp_34,R.drawable.pfp_35,
        R.drawable.pfp_36,R.drawable.pfp_37,
    )
    fun resource(id: String): Int = resources.getOrElse(ids.indexOf(id)) { resources.first() }
}
