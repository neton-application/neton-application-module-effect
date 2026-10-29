package controller.admin.effect

import effect.dto.*
import effect.logic.*
import neton.core.annotations.*
import neton.core.http.*
import neton.core.interfaces.Identity

@Controller("/effect")
class EffectController(private val logic: EffectLogic) {
    @Get("/effect/list") @Permission("effect:effect:query")
    suspend fun list() = logic.list()
    @Get("/effect/get/{id}") @Permission("effect:effect:query")
    suspend fun get(@PathVariable id: String) = logic.get(id)
    @Post("/effect/create") @Permission("effect:effect:create")
    suspend fun create(@Body body: EffectWrite,identity: Identity) = logic.save(body,EffectRules.id(identity.id),false)
    @Put("/effect/update") @Permission("effect:effect:update")
    suspend fun update(@Body body: EffectWrite,identity: Identity) = logic.save(body,EffectRules.id(identity.id),true)
    @Delete("/effect/delete/{id}") @Permission("effect:effect:delete")
    suspend fun delete(@PathVariable id: String) = logic.delete(id)
    @Post("/effect/publish") @Permission("effect:effect:publish")
    suspend fun publish(@Body body: EffectId,identity: Identity) = logic.transition(body.id,true,EffectRules.id(identity.id))
    @Post("/effect/offline") @Permission("effect:effect:offline")
    suspend fun offline(@Body body: EffectId,identity: Identity) = logic.transition(body.id,false,EffectRules.id(identity.id))
    @Post("/effect/import") @Permission("effect:effect:import")
    suspend fun importPag(request: HttpRequest,identity: Identity): EffectDto {
        EffectRules.check(request.isMultipart(),"multipart_required")
        val file=request.uploadFiles().first("file") ?: throw BadRequestException("EFFECT_FILE_REQUIRED")
        EffectRules.check(file.size in 1..EffectRules.MAX_PAG.toLong(),"upload_size")
        val bytes=file.bytes(); EffectRules.check(bytes.size in 1..EffectRules.MAX_PAG,"upload_size")
        return logic.importPag(request.form()["id"] ?: "",bytes,EffectRules.id(identity.id))
    }
    @Get("/rule/list") @Permission("effect:rule:query")
    suspend fun rules() = logic.rules()
    @Get("/rule/get/{id}") @Permission("effect:rule:query")
    suspend fun rule(@PathVariable id: String) = logic.rule(id)
    @Post("/rule/create") @Permission("effect:rule:create")
    suspend fun createRule(@Body body: RuleWrite) = logic.saveRule(body,false)
    @Put("/rule/update") @Permission("effect:rule:update")
    suspend fun updateRule(@Body body: RuleWrite) = logic.saveRule(body,true)
    @Delete("/rule/delete/{id}") @Permission("effect:rule:delete")
    suspend fun deleteRule(@PathVariable id: String) = logic.deleteRule(id)
    @Post("/catalog/publish") @Permission("effect:catalog:publish")
    suspend fun publishCatalog(@Body body: CatalogPublish,identity: Identity) = logic.publishCatalog(body.expiresAtMs,EffectRules.id(identity.id))
    @Get("/catalog/get") @Permission("effect:rule:query")
    suspend fun catalog() = logic.catalog()
    @Get("/resource/get/{id}") @Permission("effect:effect:query")
    suspend fun resource(@PathVariable id: String,response: HttpResponse) {
        response.header("Cache-Control","private, no-store")
        response.header("X-Content-Type-Options","nosniff")
        response.header("Content-Security-Policy","default-src 'none'; sandbox")
        response.bytes(data=logic.bytes(id,true),contentType="application/octet-stream")
    }
    @Post("/resource/cleanup") @Permission("effect:effect:delete")
    suspend fun cleanup() = logic.cleanup()
}
