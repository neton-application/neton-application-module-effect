package controller.app.effect

import effect.logic.EffectLogic
import neton.core.annotations.*
import neton.core.http.*

@Controller("/effect")
class EffectAppController(private val logic: EffectLogic) {
    @Get("/catalog") @RequireAuth
    suspend fun catalog(response: HttpResponse) = logic.catalog().also {
        response.header("Cache-Control","private, no-store")
    }
    @Get("/resource/get/{id}") @AllowAnonymous
    suspend fun resource(@PathVariable id: String,response: HttpResponse) {
        response.header("Cache-Control","public, max-age=31536000, immutable")
        response.header("X-Content-Type-Options","nosniff")
        response.header("Content-Security-Policy","default-src 'none'; sandbox")
        response.bytes(data=logic.bytes(id,false),contentType="application/octet-stream")
    }
}
