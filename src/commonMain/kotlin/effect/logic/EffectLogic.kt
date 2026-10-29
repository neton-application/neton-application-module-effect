package effect.logic

import effect.dto.*
import effect.table.EffectTable
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import neton.core.http.NotFoundException
import neton.database.api.Row
import neton.storage.*
import kotlin.random.Random
import kotlin.time.Clock

class EffectLogic(private val table: EffectTable, private val storageManager: StorageManager,
    private val publicBaseUrl: String, private val inspector: PagInspector = PagValidation.unavailable) {
    private val storage get() = storageManager.get("effect-private").also { check(it.baseUrl.isBlank()) }
    private fun now() = Clock.System.now().toEpochMilliseconds()
    private fun missing(): Nothing = throw NotFoundException("EFFECT_NOT_FOUND")
    private fun hash(bytes: ByteArray) = CryptographyProvider.Default.get(SHA256).hasher().hashBlocking(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    private suspend fun row(id: String) = table.rows("SELECT * FROM effect_versions WHERE id=:id","id" to EffectRules.id(id)).firstOrNull() ?: missing()
    private fun resource(r: Row, admin: Boolean): EffectResource {
        val m=Json.decodeFromString<PagMetadata>(r.string("metadata_json"))
        val id=r.long("id").toString()
        return EffectResource(id,if(admin) "/admin/effect/resource/get/$id" else "$publicBaseUrl/app/effect/resource/get/$id",r.string("sha256"),r.long("size"),m.width,m.height,m.durationMs)
    }
    private suspend fun dto(r: Row): EffectDto {
        val image=r.longOrNull("resource_id")?.let { id -> table.rows("SELECT * FROM effect_resources WHERE id=:id AND ready=1","id" to id).firstOrNull() }
        return EffectDto(r.long("id").toString(),r.string("effect_id"),r.int("version"),r.string("name"),r.string("rights"),r.int("publication_state"),image?.let { resource(it,true) })
    }
    suspend fun get(id: String) = dto(row(id))
    suspend fun list() = table.rows("SELECT * FROM effect_versions ORDER BY id DESC LIMIT 1000").map { dto(it) }
    suspend fun save(p: EffectWrite, actor: Long, update: Boolean): EffectDto = table.db.transaction {
        EffectRules.effect(p); table.lock()
        val id=if(update) {
            val old=row(p.id ?: "")
            EffectRules.check(old.int("publication_state")==1,"draft_required")
            EffectRules.check(old.string("effect_id")==p.effectId && old.int("version")==p.version,"immutable_identity")
            table.execute("UPDATE effect_versions SET name=:name,rights=:rights,operator_id=:actor WHERE id=:id","name" to p.name,"rights" to p.rights,"actor" to actor,"id" to old.long("id")); old.long("id")
        } else {
            EffectRules.check(p.id==null,"unexpected_id")
            val max=table.rows("SELECT COALESCE(MAX(version),0) AS n FROM effect_versions WHERE effect_id=:effect","effect" to p.effectId).single().int("n")
            EffectRules.check(p.version>max,"version_conflict")
            table.rows("INSERT INTO effect_versions(effect_id,version,name,rights,operator_id,created_at) VALUES(:effect,:version,:name,:rights,:actor,:now) RETURNING id","effect" to p.effectId,"version" to p.version,"name" to p.name,"rights" to p.rights,"actor" to actor,"now" to now()).single().long("id")
        }
        get(id.toString())
    }
    suspend fun delete(id: String): Boolean = table.db.transaction {
        table.lock(); val old=row(id); EffectRules.check(old.int("publication_state")==1,"draft_required")
        EffectRules.check(table.rows("SELECT id FROM effect_rules WHERE effect_version_id=:id LIMIT 1","id" to old.long("id")).isEmpty(),"effect_in_use")
        table.execute("DELETE FROM effect_versions WHERE id=:id","id" to old.long("id"))==1L
    }
    suspend fun importPag(id: String, bytes: ByteArray, actor: Long): EffectDto {
        val metadata=PagValidation.inspect(bytes,PagValidation.EFFECT,inspector)
        val digest=hash(bytes)
        val reserved=table.db.transaction {
            table.lock(); val effect=row(id); EffectRules.check(effect.int("publication_state")==1,"draft_required")
            if(effect.longOrNull("resource_id")!=null) {
                val existing=table.rows("SELECT * FROM effect_resources WHERE id=:id","id" to effect.long("resource_id")).single()
                EffectRules.check(existing.string("sha256")==digest && existing.int("ready")==1,"resource_conflict")
                null
            } else {
                val nonce=Random.nextBytes(24).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
                table.rows("INSERT INTO effect_resources(object_key,sha256,size,metadata_json,operator_id,created_at) VALUES(:key,:hash,:size,:metadata,:actor,:now) RETURNING id",
                    "key" to "platform/effects/$nonce","hash" to digest,"size" to bytes.size.toLong(),"metadata" to Json.encodeToString(metadata),"actor" to actor,"now" to now()).single().long("id")
            }
        }
        if(reserved==null) return get(id)
        return table.db.transaction {
            table.lock(); val effect=row(id); EffectRules.check(effect.int("publication_state")==1,"draft_required")
            if(effect.longOrNull("resource_id")!=null) {
                val existing=table.rows("SELECT * FROM effect_resources WHERE id=:id","id" to effect.long("resource_id")).single()
                EffectRules.check(existing.string("sha256")==digest && existing.int("ready")==1,"resource_conflict")
                table.execute("DELETE FROM effect_resources WHERE id=:id AND ready=0","id" to reserved)
            } else {
                val r=table.rows("SELECT * FROM effect_resources WHERE id=:id","id" to reserved).single()
                storage.write(r.string("object_key"),bytes,WriteOptions(overwrite=false))
                table.execute("UPDATE effect_resources SET ready=1 WHERE id=:id","id" to reserved)
                table.execute("UPDATE effect_versions SET resource_id=:resource WHERE id=:id","resource" to reserved,"id" to effect.long("id"))
            }
            get(id)
        }
    }
    suspend fun transition(id: String, publish: Boolean, actor: Long): EffectDto = table.db.transaction {
        table.lock(); val effect=row(id)
        EffectRules.check(effect.int("publication_state")==if(publish) 1 else 2,"invalid_transition")
        if(publish) {
            val r=table.rows("SELECT * FROM effect_resources WHERE id=:id AND ready=1","id" to effect.longOrNull("resource_id")).firstOrNull() ?: missing()
            EffectRules.check(effect.string("rights").isNotBlank() && storage.exists(r.string("object_key")),"unverified_resource")
            table.execute("UPDATE effect_resources SET ever_published=1 WHERE id=:id","id" to r.long("id"))
        }
        table.execute("UPDATE effect_versions SET publication_state=:state WHERE id=:id","state" to if(publish) 2 else 3,"id" to effect.long("id"))
        if(!publish) {
            val previous=catalog()
            persistSnapshot(previous.rules.filterNot { it.effectId==effect.string("effect_id") && it.effectVersion==effect.int("version") },previous.expiresAtMs,actor)
        }
        get(id)
    }
    suspend fun rules(): List<RuleDto> = table.rows("SELECT * FROM effect_rules ORDER BY id").map {
        RuleDto(it.long("id").toString(),Json.decodeFromString<RuleWrite>(it.string("configuration_json")),it.long("rule_version"))
    }
    suspend fun rule(id: String): RuleDto = rules().firstOrNull { it.id==id } ?: missing()
    suspend fun saveRule(p: RuleWrite, update: Boolean): RuleDto = table.db.transaction {
        EffectRules.rule(p); table.lock(); row(p.effectVersionId)
        val id=if(update) {
            val id=EffectRules.id(p.id ?: ""); rule(id.toString())
            table.execute("UPDATE effect_rules SET effect_version_id=:effect,configuration_json=:json,enabled=:enabled,rule_version=rule_version+1 WHERE id=:id","effect" to EffectRules.id(p.effectVersionId),"json" to Json.encodeToString(p.copy(id=null)),"enabled" to p.enabled,"id" to id); id
        } else {
            EffectRules.check(p.id==null,"unexpected_id")
            val count=table.rows("SELECT COUNT(*) AS n FROM effect_rules").single().long("n")
            EffectRules.check(count<256,"rule_count")
            table.rows("INSERT INTO effect_rules(effect_version_id,configuration_json,enabled) VALUES(:effect,:json,:enabled) RETURNING id","effect" to EffectRules.id(p.effectVersionId),"json" to Json.encodeToString(p),"enabled" to p.enabled).single().long("id")
        }
        rule(id.toString())
    }
    suspend fun deleteRule(id: String): Boolean = table.db.transaction {
        table.lock(); table.execute("DELETE FROM effect_rules WHERE id=:id","id" to EffectRules.id(id))==1L
    }
    suspend fun catalog(): EffectCatalog {
        val r=table.rows("SELECT * FROM effect_catalogs ORDER BY id DESC LIMIT 1").firstOrNull() ?: return EffectCatalog(revision=0,expiresAtMs=0,rules=emptyList())
        return EffectCatalog(revision=r.long("id"),expiresAtMs=r.long("expires_at_ms"),rules=if(r.long("expires_at_ms")<=now()) emptyList() else Json.decodeFromString(r.string("rules_json")))
    }
    suspend fun publishCatalog(expiresAtMs: Long, actor: Long): EffectCatalog = table.db.transaction {
        table.lock(); EffectRules.expiry(expiresAtMs,now()); snapshot(expiresAtMs,actor)
    }
    private suspend fun snapshot(expiry: Long, actor: Long): EffectCatalog {
        val rules=mutableListOf<CatalogRule>()
        for(rule in table.rows("SELECT q.id AS rule_id,q.rule_version,q.configuration_json,v.effect_id,v.version,r.* FROM effect_rules q JOIN effect_versions v ON v.id=q.effect_version_id JOIN effect_resources r ON r.id=v.resource_id WHERE q.enabled=1 AND v.publication_state=2 AND r.ready=1 AND r.ever_published=1")) {
            val p=Json.decodeFromString<RuleWrite>(rule.string("configuration_json")); EffectRules.rule(p)
            val ruleExpiry=minOf(expiry,p.expiresAtMs ?: expiry)
            if(ruleExpiry<=now()) continue
            rules+=CatalogRule(rule.long("rule_id").toString(),rule.string("effect_id"),rule.int("version"),p.keywords,p.channelScope,p.placement,p.priority,p.cooldownMs,p.maxEventAgeMs,resource(rule,false),rule.long("rule_version"),ruleExpiry)
        }
        val sorted=rules.sortedWith(compareByDescending<CatalogRule> { it.priority }.thenBy { EffectRules.id(it.ruleId) })
        return persistSnapshot(sorted,expiry,actor)
    }
    private suspend fun persistSnapshot(rules: List<CatalogRule>, expiry: Long, actor: Long): EffectCatalog {
        table.rows("INSERT INTO effect_catalogs(expires_at_ms,rules_json,operator_id,created_at) VALUES(:expiry,:rules,:actor,:now) RETURNING id","expiry" to expiry,"rules" to Json.encodeToString(rules),"actor" to actor,"now" to now())
        return catalog()
    }
    suspend fun bytes(id: String, admin: Boolean): ByteArray {
        val r=table.rows("SELECT * FROM effect_resources WHERE id=:id AND ready=1"+(if(admin) "" else " AND ever_published=1"),"id" to EffectRules.id(id)).firstOrNull() ?: missing()
        return storage.read(r.string("object_key"))
    }
    suspend fun cleanup(): Int = table.db.transaction {
        table.lock()
        val rows=table.rows("SELECT r.* FROM effect_resources r WHERE r.ever_published=0 AND r.created_at<:cutoff AND NOT EXISTS(SELECT 1 FROM effect_versions v WHERE v.resource_id=r.id) ORDER BY r.id LIMIT 100","cutoff" to now()-86400000L)
        for(r in rows) {
            if(storage.exists(r.string("object_key"))) storage.delete(r.string("object_key"))
            table.execute("DELETE FROM effect_resources WHERE id=:id","id" to r.long("id"))
        }
        rows.size
    }
}
