package effect.table

import neton.database.api.DbContext
import neton.database.api.Row

class EffectTable(val db: DbContext) {
    suspend fun rows(sql: String, vararg params: Pair<String, Any?>): List<Row> = db.fetchAll(sql,mapOf(*params))
    suspend fun execute(sql: String, vararg params: Pair<String, Any?>): Long = db.execute(sql,mapOf(*params))
    suspend fun lock() { rows("SELECT 1 AS ok FROM pg_advisory_xact_lock(hashtext('effect:catalog'))") }
}
