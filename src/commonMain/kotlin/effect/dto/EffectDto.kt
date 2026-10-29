package effect.dto

import kotlinx.serialization.Serializable

@Serializable data class EffectResource(val resourceId: String, val url: String, val sha256: String, val size: Long, val width: Int, val height: Int, val durationMs: Long, val profile: String = "pag-effect-v1")
@Serializable data class EffectCatalog(val schemaVersion: Int = 1, val revision: Long, val expiresAtMs: Long, val rules: List<CatalogRule>)
@Serializable data class CatalogRule(val ruleId: String, val effectId: String, val effectVersion: Int, val keywords: List<String>, val channelScope: String, val placement: String = "fullscreen", val priority: Int, val cooldownMs: Long, val maxEventAgeMs: Long, val resource: EffectResource, val ruleVersion: Long, val expiresAtMs: Long)
@Serializable data class EffectWrite(val effectId: String, val version: Int, val name: String, val rights: String, val id: String? = null)
@Serializable data class EffectDto(val id: String, val effectId: String, val version: Int, val name: String, val rights: String, val publicationState: Int, val resource: EffectResource?)
@Serializable data class RuleWrite(val effectVersionId: String, val keywords: List<String>, val channelScope: String = "all", val placement: String = "fullscreen", val priority: Int = 0, val cooldownMs: Long = 30000, val maxEventAgeMs: Long = 5000, val enabled: Int = 1, val id: String? = null, val expiresAtMs: Long? = null)
@Serializable data class RuleDto(val id: String, val configuration: RuleWrite, val ruleVersion: Long)
@Serializable data class EffectId(val id: String)
@Serializable data class CatalogPublish(val expiresAtMs: Long)
