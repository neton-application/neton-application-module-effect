package init

import effect.logic.*
import effect.table.EffectTable
import neton.core.component.NetonContext
import neton.core.config.ConfigLoader
import neton.database.api.DbContext
import neton.storage.StorageManager

object EffectRuntimeBootstrap {
    fun initialize(ctx: NetonContext) {
        val config=ConfigLoader.loadModuleConfig(moduleName="effect")
        val base=ConfigLoader.getString(config,"public_base_url")?.trim()?.trimEnd('/') ?: ""
        require(Regex("https?://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9._~%/-]*)?").matches(base)) { "effect.public_base_url must be an absolute API base URL" }
        val storage=ctx.get(StorageManager::class)
        require(storage.get("effect-private").baseUrl.isBlank()) { "effect-private must not have public mapping" }
        ctx.bindIfAbsent(EffectLogic::class,EffectLogic(EffectTable(ctx.get(DbContext::class)),storage,base,
            configuredPagInspector(ConfigLoader.getString(config,"pag_inspector_executable"))))
    }
}
