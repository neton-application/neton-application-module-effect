// Test fixture opens the database directly; that entry point is an internal Neton API.
@file:OptIn(neton.core.annotations.InternalNetonApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

import effect.dto.*
import effect.logic.*
import effect.table.EffectTable
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import neton.core.component.NetonContext
import neton.core.config.getEnv
import neton.database.adapter.sqlx.SqlxDatabase
import neton.database.config.*
import neton.storage.*
import platform.posix.*
import kotlin.test.*
import kotlin.time.Clock

class EffectPostgresTest {
    @Test fun officialPagAndPublishedRuleSnapshots() = runBlocking {
        val uri=getEnv("PAG_TEST_DATABASE_URI") ?: return@runBlocking
        val config=DatabaseConfig(DatabaseDriver.POSTGRESQL,uri)
        val info=config.parseUri() as PostgresUriInfo
        require(info.host=="127.0.0.1" && info.port==55476 && (info.database=="pag_backend_test" || info.database.startsWith("pag_backend_test_")))
        val root=getEnv("PAG_TEST_STORAGE_ROOT") ?: error("PAG_TEST_STORAGE_ROOT required")
        require(root.startsWith("/tmp/pag-postgres."))
        val cli=getEnv("PAG_TEST_INSPECTOR") ?: error("Official CLI path required")
        val file=fopen(getEnv("PAG_TEST_EFFECT_FILE") ?: error("PAG_TEST_EFFECT_FILE required"),"rb") ?: error("Real effect file required")
        val bytes=try {
            check(fseek(file,0,SEEK_END)==0); val size=ftell(file); require(size in 1..EffectRules.MAX_PAG.toLong()); rewind(file)
            ByteArray(size.toInt()).also { b -> b.usePinned { check(fread(it.addressOf(0),1u,b.size.convert(),file).toLong()==size) } }
        } finally { fclose(file) }
        val db=SqlxDatabase.initialize(config)
        try {
            require(db.fetchAll("SELECT COUNT(*) AS n FROM effect_versions").single().long("n")==0L) { "Requires empty migrated disposable effect tables; never wipes data" }
            val ctx=NetonContext(emptyArray())
            StorageComponent.init(ctx,StorageConfig().apply {
                source("default") { basePath="$root/effect-storage" }
                source("effect-private") { basePath="$root/effect-storage" }
            })
            val storage=ctx.get(StorageManager::class)
            val logic=EffectLogic(EffectTable(db),storage,"http://127.0.0.1:55477",configuredPagInspector(cli))
            val empty=logic.catalog(); assertEquals(0,empty.revision); assertTrue(empty.rules.isEmpty())
            assertFails { logic.save(EffectWrite("logo",1,"Logo",""),1,false) }
            val effect=logic.save(EffectWrite("original-kiss",1,"Weey Kiss","MIT; Weey / PrivChat contributors; resources/pag/LICENSE.txt"),1,false)
            assertFails { EffectLogic(EffectTable(db),storage,"http://127.0.0.1:55477").importPag(effect.id,bytes,1) }
            assertFails { logic.importPag(effect.id,byteArrayOf(80,65,71,0),1) }
            assertEquals(0,db.fetchAll("SELECT COUNT(*) AS n FROM effect_resources").single().long("n"))
            val imported=logic.importPag(effect.id,bytes,1)
            val resource=assertNotNull(imported.resource)
            assertEquals("pag-effect-v1",resource.profile)
            assertEquals(720,resource.width)
            assertEquals(1280,resource.height)
            assertEquals(4000L,resource.durationMs)
            assertFails { logic.bytes(resource.resourceId,false) }
            assertContentEquals(bytes,logic.bytes(resource.resourceId,true))
            assertEquals(resource.resourceId,logic.importPag(effect.id,bytes,1).resource?.resourceId)
            val rule=logic.saveRule(RuleWrite(effect.id,listOf("hello","想你了")),false)
            assertEquals(1,rule.ruleVersion)
            logic.transition(effect.id,true,1)
            assertTrue(logic.catalog().rules.isEmpty())
            val expiry=Clock.System.now().toEpochMilliseconds()+120000
            val first=logic.publishCatalog(expiry,1)
            assertEquals(rule.id,first.rules.single().ruleId)
            assertEquals(1,first.rules.single().ruleVersion)
            assertEquals(expiry,first.rules.single().expiresAtMs)
            assertContentEquals(bytes,logic.bytes(resource.resourceId,false))
            val updated=logic.saveRule(rule.configuration.copy(id=rule.id,priority=99),true)
            assertEquals(2,updated.ruleVersion)
            assertEquals(1,logic.catalog().rules.single().ruleVersion,"Draft edits must not mutate published JSON")
            val second=logic.publishCatalog(expiry,1)
            assertTrue(second.revision>first.revision); assertEquals(2,second.rules.single().ruleVersion)
            logic.transition(effect.id,false,1)
            val revoked=logic.catalog()
            assertTrue(revoked.revision>second.revision); assertTrue(revoked.rules.isEmpty())
            assertContentEquals(bytes,logic.bytes(resource.resourceId,false),"Previously public immutable bytes survive offline")
            assertFails { logic.save(EffectWrite("original-kiss",1,"Changed","MIT",effect.id),1,true) }
            assertFails { logic.delete(effect.id) }
            println("EFFECT_POSTGRES_EXECUTED: official libpag + real PG/storage + rights + rule versions + atomic snapshots + revocation")
        } finally { SqlxDatabase.close() }
    }
}
