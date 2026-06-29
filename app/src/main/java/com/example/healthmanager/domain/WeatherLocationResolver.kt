package com.example.healthmanager.domain

data class WeatherLocationCandidate(
    val displayCity: String,
    val queryCities: List<String>
)

object WeatherLocationResolver {
    private val municipalityNames = setOf("北京", "上海", "天津", "重庆")

    private val provinceLevelNames = setOf(
        "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西",
        "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
        "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门"
    )

    fun buildCandidate(
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        subLocality: String?
    ): WeatherLocationCandidate? {
        val localCity = normalizeCityName(locality)
        val subAdminCity = normalizeCityName(subAdminArea)
        val province = normalizeCityName(adminArea)
        val countyOrDistrict = normalizeCityName(subLocality)

        val displayCity = listOf(countyOrDistrict, localCity, subAdminCity, province)
            .firstOrNull { !it.isNullOrBlank() }
            ?: return null

        val queryCities = listOf(
            localCity?.takeUnless { it == countyOrDistrict },
            subAdminCity?.takeUnless { it == countyOrDistrict },
            localCity,
            subAdminCity,
            countyOrDistrict,
            province
        )
            .mapNotNull { it?.takeIf { city -> city.isNotBlank() } }
            .distinct()
            .filterNot(::isProvinceLevelName)

        if (queryCities.isEmpty()) return null

        return WeatherLocationCandidate(
            displayCity = displayCity,
            queryCities = queryCities
        )
    }

    fun normalizeCityName(rawName: String?): String? {
        val name = rawName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return name
            .removeSuffix("特别行政区")
            .removeSuffix("蒙古自治州")
            .removeSuffix("藏族自治州")
            .removeSuffix("回族自治州")
            .removeSuffix("哈萨克自治州")
            .removeSuffix("自治州")
            .removeSuffix("地区")
            .removeSuffix("盟")
            .removeSuffix("市")
            .removeSuffix("县")
            .removeSuffix("区")
            .removeSuffix("省")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    fun isProvinceLevelName(name: String): Boolean {
        return name !in municipalityNames && name in provinceLevelNames
    }
}
