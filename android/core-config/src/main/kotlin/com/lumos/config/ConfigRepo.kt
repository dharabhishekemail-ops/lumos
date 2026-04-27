
package com.lumos.config
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
data class ConfigEnvelope(val schemaVersion:Int,val keyId:String,val signatureB64:String,val payloadJson:String)
interface ConfigVerifier { fun verifyEd25519(payloadCanonicalJson:String, signatureB64:String, keyId:String):Boolean }
sealed interface ApplyResult { data object Applied:ApplyResult; data object InvalidSignature:ApplyResult; data object IoError:ApplyResult }
object CanonicalJson{
 fun canonicalize(json:String):String = canonical(JSONObject(json))
 private fun canonical(v:Any?):String = when(v){
  null -> "null"
  is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(prefix="{",postfix="}"){ k -> JSONObject.quote(k)+":"+canonical(v.get(k)) }
  is JSONArray -> (0 until v.length()).joinToString(prefix="[",postfix="]"){ i -> canonical(v.get(i)) }
  is String -> JSONObject.quote(v)
  is Number, is Boolean -> v.toString()
  else -> JSONObject.quote(v.toString())
 }
}
class ConfigRepository(private val dir:File, private val verifier:ConfigVerifier){
 private val active=File(dir,"active_config.json"); private val lkg=File(dir,"last_known_good_config.json")
 fun applySignedConfig(env:ConfigEnvelope):ApplyResult = try{
  dir.mkdirs(); val canonical=CanonicalJson.canonicalize(env.payloadJson)
  if(!verifier.verifyEd25519(canonical, env.signatureB64, env.keyId)) return ApplyResult.InvalidSignature
  val tmp=File(dir,"active_config.json.tmp"); tmp.writeText(env.payloadJson)
  Files.move(tmp.toPath(), active.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  active.copyTo(lkg, overwrite=true); ApplyResult.Applied
 }catch(_:Exception){ ApplyResult.IoError }
 fun loadLastKnownGood():String? = if(lkg.exists()) lkg.readText() else null
}
