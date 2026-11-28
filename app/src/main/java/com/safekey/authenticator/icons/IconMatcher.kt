package com.safekey.authenticator.icons

import java.util.Locale

/**
 * Issuer → icon matching, compatible with Aegis' rule
 * (`IconPack.Icon#getMatchFor`): an icon is a NORMAL match when one of its
 * issuer aliases contains the entry's issuer, and an INVERSE match when the
 * entry's issuer contains the alias. NORMAL matches are listed first.
 *
 * On top of that, a small Chinese→English alias table is applied to the entry
 * issuer. The reference pack (aegis-icons) ships 1007 icons and **zero** CJK
 * aliases, so without this a vault whose issuers are "微信"/"微博" would never
 * match anything.
 */
object IconMatcher {

    /**
     * Chinese product names → the English names used by icon packs.
     * Matching only: the issuer stored in the vault is never rewritten.
     */
    private val CJK_ALIASES: List<Pair<String, List<String>>> = listOf(
        "微信" to listOf("wechat"),
        "企业微信" to listOf("wecom", "wechat work"),
        "腾讯云" to listOf("tencent cloud"),
        "腾讯" to listOf("tencent"),
        "QQ音乐" to listOf("qq music"),
        "QQ邮箱" to listOf("qq mail"),
        "微博" to listOf("weibo"),
        "支付宝" to listOf("alipay"),
        "淘宝" to listOf("taobao"),
        "天猫" to listOf("tmall"),
        "京东" to listOf("jd.com", "jingdong"),
        "拼多多" to listOf("pinduoduo"),
        "抖音" to listOf("douyin", "tiktok"),
        "快手" to listOf("kuaishou"),
        "哔哩哔哩" to listOf("bilibili"),
        "B站" to listOf("bilibili"),
        "知乎" to listOf("zhihu"),
        "百度" to listOf("baidu"),
        "百度网盘" to listOf("baidu netdisk"),
        "网易云音乐" to listOf("netease cloud music"),
        "网易" to listOf("netease"),
        "小米" to listOf("xiaomi"),
        "华为云" to listOf("huawei cloud"),
        "华为" to listOf("huawei"),
        "荣耀" to listOf("honor"),
        "阿里云" to listOf("alibaba cloud", "aliyun"),
        "钉钉" to listOf("dingtalk"),
        "飞书" to listOf("feishu", "lark"),
        "美团" to listOf("meituan"),
        "饿了么" to listOf("ele.me"),
        "滴滴" to listOf("didi"),
        "携程" to listOf("ctrip"),
        "米哈游" to listOf("mihoyo", "hoyoverse"),
        "原神" to listOf("genshin"),
        "币安" to listOf("binance"),
        "欧易" to listOf("okx"),
        "火币" to listOf("huobi"),
        "夸克" to listOf("quark"),
        "迅雷" to listOf("xunlei"),
        "中国移动" to listOf("china mobile"),
        "中国联通" to listOf("china unicom"),
        "中国电信" to listOf("china telecom"),
        "亚马逊" to listOf("amazon"),
        "微软" to listOf("microsoft"),
        "苹果" to listOf("apple"),
        "谷歌" to listOf("google")
    )

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    /** The issuer plus any English names implied by the Chinese alias table. */
    fun expandIssuer(issuer: String): List<String> {
        val base = normalize(issuer)
        if (base.isEmpty()) return emptyList()
        val terms = LinkedHashSet<String>()
        terms.add(base)
        for ((cjk, english) in CJK_ALIASES) {
            if (base.contains(cjk.lowercase(Locale.ROOT))) terms.addAll(english)
        }
        return terms.toList()
    }

    /** Icons suggested for [issuer], best matches first. */
    fun suggestions(pack: InstalledIconPack, issuer: String, limit: Int = 60): List<PackIcon> {
        val terms = expandIssuer(issuer)
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val normal = ArrayList<PackIcon>()
        val inverse = ArrayList<PackIcon>()
        for (icon in pack.icons) {
            var match = 0
            for (alias in icon.issuers) {
                val a = normalize(alias)
                if (a.isEmpty()) continue
                for (term in terms) {
                    if (a.contains(term)) {
                        match = 2
                        break
                    }
                    if (term.contains(a)) match = maxOf(match, 1)
                }
                if (match == 2) break
            }
            when (match) {
                2 -> normal.add(icon)
                1 -> inverse.add(icon)
            }
        }
        if (normal.isEmpty() && inverse.isEmpty()) return emptyList()
        return (normal + inverse).take(limit)
    }

    /** The single best icon for an issuer, or null when nothing matches. */
    fun bestMatch(pack: InstalledIconPack, issuer: String): PackIcon? =
        suggestions(pack, issuer, limit = 1).firstOrNull()

    /** Free-text search over icon names, aliases and categories. */
    fun search(pack: InstalledIconPack, query: String, limit: Int = 60): List<PackIcon> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return pack.icons.filter { icon ->
            normalize(icon.name).contains(q) ||
                icon.issuers.any { normalize(it).contains(q) } ||
                normalize(icon.category.orEmpty()).contains(q)
        }.take(limit)
    }
}
