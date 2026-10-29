package effect.logic

import effect.dto.*
import kotlinx.serialization.json.*
import neton.core.http.BadRequestException
import kotlin.test.*

class EffectRulesTest {
    @Test fun requiresRightsAndStableVersionIdentity() {
        EffectRules.effect(EffectWrite("hearts",1,"Hearts","Licensed by author"))
        for(p in listOf(EffectWrite("hearts",1,"Hearts",""),EffectWrite("../unsafe",1,"Hearts","License"),EffectWrite("hearts",0,"Hearts","License"))) {
            assertFailsWith<BadRequestException> { EffectRules.effect(p) }
        }
    }
    @Test fun onlyBoundedExactFullscreenRulesAllowed() {
        val base=RuleWrite("1",listOf("birthday","想你了"))
        for(scope in listOf("all","dm","group")) EffectRules.rule(base.copy(channelScope=scope))
        for(p in listOf(base.copy(placement="bubble"),base.copy(channelScope="any"),base.copy(keywords=emptyList()),
            base.copy(keywords=listOf(" a")),base.copy(keywords=listOf("a","a")),base.copy(cooldownMs=9999),
            base.copy(maxEventAgeMs=5001),base.copy(enabled=2),base.copy(effectVersionId="0"))) {
            assertFailsWith<BadRequestException> { EffectRules.rule(p) }
        }
    }
    @Test fun catalogExpiryIsRequiredAndBounded() {
        EffectRules.expiry(1001,1000)
        assertFailsWith<BadRequestException> { EffectRules.expiry(1000,1000) }
        assertFailsWith<BadRequestException> { EffectRules.expiry(1000+7*86400000L+1,1000) }
    }
    @Test fun mobileCatalogUsesStableCamelCaseAndStringIds() {
        val resource=EffectResource("9007199254740993","https://example.test/app/effect/resource/get/9007199254740993","a".repeat(64),100,512,256,3000)
        val catalog=EffectCatalog(revision=1,expiresAtMs=1234567890123,rules=listOf(CatalogRule("1","hearts",1,listOf("hello"),"all",priority=0,cooldownMs=30000,maxEventAgeMs=5000,resource=resource,ruleVersion=2,expiresAtMs=1234567890123)))
        val json=Json { encodeDefaults=true }.encodeToJsonElement(catalog).jsonObject
        assertEquals(setOf("schemaVersion","revision","expiresAtMs","rules"),json.keys)
        val rule=json.getValue("rules").jsonArray.single().jsonObject
        assertEquals("fullscreen",rule.getValue("placement").jsonPrimitive.content)
        assertEquals(2,rule.getValue("ruleVersion").jsonPrimitive.long)
        assertEquals("pag-effect-v1",rule.getValue("resource").jsonObject.getValue("profile").jsonPrimitive.content)
        assertEquals("9007199254740993",rule.getValue("resource").jsonObject.getValue("resourceId").jsonPrimitive.content)
    }
}
