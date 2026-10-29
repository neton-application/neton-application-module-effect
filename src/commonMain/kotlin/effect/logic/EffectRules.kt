package effect.logic

import effect.dto.*
import neton.core.http.BadRequestException

object EffectRules {
    const val MAX_IMAGE = 500 * 1024
    const val MAX_PAG = 2 * 1024 * 1024
    fun check(ok: Boolean, code: String) { if (!ok) throw BadRequestException("EFFECT_${code.uppercase()}") }
    fun id(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 } ?: throw BadRequestException("EFFECT_INVALID_ID")
    fun effect(p: EffectWrite) {
        check(Regex("[A-Za-z0-9_-]{1,128}").matches(p.effectId) && p.version > 0, "invalid_identity")
        check(p.name.isNotBlank() && p.name.length <= 128 && p.rights.isNotBlank() && p.rights.length <= 4000,"rights_and_name_required")
    }
    fun rule(p: RuleWrite) {
        id(p.effectVersionId)
        check(p.keywords.size in 1..32 && p.keywords.all { it.isNotBlank() && it == it.trim() && it.length <= 128 } && p.keywords.toSet().size == p.keywords.size,"invalid_keywords")
        check(p.channelScope in setOf("all","dm","group") && p.placement == "fullscreen","unsupported_scope")
        check(p.priority in -1000..1000 && p.cooldownMs in 10000L..86400000L && p.maxEventAgeMs in 1L..5000L && p.enabled in 0..1,"rule_budget")
        check(p.expiresAtMs==null || p.expiresAtMs in 1L..9007199254740991L,"invalid_expiry")
    }
    fun expiry(value: Long, now: Long) = check(value > now && value-now <= 7*86400000L,"invalid_expiry")
}
