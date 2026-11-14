package com.ntplayone.tv

import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class PairingServer(private val app: AppModel, private val port: Int = 8899) {
    // token fixo por instalação: o QR continua valendo mesmo se o app reiniciar
    val token: String = runCatching {
        val f = File(app.ctx.filesDir, "pair_token.txt")
        val saved = if (f.exists()) f.readText().trim() else ""
        if (saved.length in 6..32) saved else {
            val t = UUID.randomUUID().toString().replace("-", "").take(10)
            f.writeText(t)
            t
        }
    }.getOrDefault(UUID.randomUUID().toString().take(8))

    class Job {
        @Volatile var done = false
        @Volatile var error: String? = null
        val startedAt = System.currentTimeMillis()
    }

    private val jobs = ConcurrentHashMap<String, Job>()

    fun start() {
        thread(isDaemon = true, name = "pairing") {
            try {
                ServerSocket(port).use { server ->
                    app.pairUrl = "http://${Net.localIp()}:$port/?t=$token"
                    while (true) {
                        val s = server.accept()
                        thread(isDaemon = true) { handle(s) }
                    }
                }
            } catch (e: Exception) {
                app.pairUrl = ""
            }
        }
    }

    private fun handle(s: Socket) {
        try {
            serve(s)
        } catch (e: Exception) {
            // celular desconectou no meio da resposta: não pode derrubar o app
        }
    }

    private fun serve(s: Socket) {
        s.use {
        val input = BufferedReader(InputStreamReader(s.getInputStream()))
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        var len = 0
        while (true) {
            val h = input.readLine() ?: break
            if (h.isEmpty()) break
            if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(":").trim().toIntOrNull() ?: 0
        }
        val out = s.getOutputStream()
        fun respond(code: String, type: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 $code\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(b)
            out.flush()
        }
        when {
            method == "GET" && path == "/" -> respond("200 OK", "text/html", page())
            method == "GET" && path.startsWith("/job/") -> {
                val job = jobs[path.removePrefix("/job/")]
                val body = when {
                    job == null -> """{"state":"error","error":"Tarefa não encontrada. Envie a lista novamente."}"""
                    job.error != null -> JSONObject().put("state", "error").put("error", job.error).toString()
                    job.done -> """{"state":"ok"}"""
                    else -> """{"state":"running"}"""
                }
                respond("200 OK", "application/json", body)
            }
            method == "POST" && path == "/add" -> {
                val buf = CharArray(len.coerceAtLeast(0))
                var read = 0
                while (read < len) {
                    val n = input.read(buf, read, len - read)
                    if (n < 0) break
                    read += n
                }
                val form = String(buf, 0, read).split("&").filter { it.isNotEmpty() }.associate {
                    val kv = it.split("=", limit = 2)
                    URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
                }
                if (form["t"] != token) {
                    respond("200 OK", "application/json", """{"ok":false,"error":"Token inválido. Escaneie o QR code da TV novamente."}""")
                    return
                }
                val kind = form["kind"] ?: "m3u"
                val name = form["name"] ?: ""
                val server = form["server"] ?: ""
                val user = form["user"] ?: ""
                val pass = form["pass"] ?: ""
                val url = form["url"] ?: ""
                if (kind == "m3u" && url.isBlank()) {
                    respond("200 OK", "application/json", """{"ok":false,"error":"Informe o link da lista."}""")
                    return
                }
                if (kind == "xtream" && (server.isBlank() || user.isBlank() || pass.isBlank())) {
                    respond("200 OK", "application/json", """{"ok":false,"error":"Preencha servidor, usuário e senha."}""")
                    return
                }
                val job = Job()
                val id = UUID.randomUUID().toString().take(8)
                jobs[id] = job
                pruneJobs()
                // responde na hora: listas grandes demoram e o celular não pode ficar esperando
                respond("200 OK", "application/json", """{"ok":true,"job":"$id"}""")
                thread(isDaemon = true, name = "pair-import") {
                    app.main.post {
                        app.importStatus = "Recebendo lista do celular..."
                    }
                    val err = try {
                        if (kind == "xtream") app.addXtream(name, server, user, pass)
                        else app.addM3u(name, url)
                    } catch (e: OutOfMemoryError) {
                        "Memória insuficiente para esta lista. Tente uma lista menor."
                    } catch (e: Exception) {
                        "Falha ao adicionar a lista: ${e.message}"
                    }
                    job.error = err
                    job.done = true
                    app.main.post {
                        if (err == null) {
                            app.importStatus = "Lista recebida do celular"
                            app.main.postDelayed({
                                if (app.importStatus == "Lista recebida do celular") app.importStatus = ""
                            }, 20000)
                        } else {
                            app.importStatus = ""
                            app.message = err
                        }
                    }
                }
            }
            else -> respond("404 Not Found", "text/plain", "NTPlayone")
        }
        }
    }

    private fun pruneJobs() {
        if (jobs.size <= 20) return
        val cut = System.currentTimeMillis() - 30 * 60 * 1000
        jobs.entries.removeAll { it.value.done && it.value.startedAt < cut }
    }

    private fun page() = PAGE.replace("TOKEN", token)

    companion object {
        private val PAGE = """
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>NTPlayone — enviar lista</title>
<style>
:root{color-scheme:dark}
*{box-sizing:border-box;margin:0}
body{font-family:system-ui,-apple-system,'Segoe UI',sans-serif;background:#070b14;color:#f9fafb;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
.card{width:100%;max-width:420px;background:#0d1424;border:1px solid #1e293b;border-radius:20px;padding:28px}
h1{font-size:26px;background:linear-gradient(90deg,#22d3ee,#8b5cf6,#d946ef);-webkit-background-clip:text;background-clip:text;color:transparent;margin-bottom:4px}
p.sub{color:#9ca3af;font-size:13px;margin-bottom:20px}
.tabs{display:flex;gap:8px;margin-bottom:18px}
.tab{flex:1;padding:10px;border-radius:10px;border:1px solid #1e293b;background:#111a2e;color:#9ca3af;font-size:14px;cursor:pointer}
.tab.on{background:linear-gradient(90deg,#22d3ee,#8b5cf6);color:#fff;border-color:transparent}
label{display:block;font-size:12px;color:#9ca3af;margin:12px 0 4px}
input{width:100%;padding:12px;border-radius:10px;border:1px solid #1e293b;background:#111a2e;color:#f9fafb;font-size:15px;outline:none}
input:focus{border-color:#22d3ee}
button.go{width:100%;margin-top:20px;padding:14px;border:none;border-radius:12px;background:linear-gradient(90deg,#22d3ee,#8b5cf6);color:#fff;font-size:16px;font-weight:600;cursor:pointer}
button.go{width:100%;margin-top:20px;padding:14px;border:none;border-radius:12px;background:linear-gradient(90deg,#22d3ee,#8b5cf6);color:#fff;font-size:16px;font-weight:600;cursor:pointer}
button.go:disabled{opacity:.6}
#msg{margin-top:14px;font-size:14px;min-height:20px;line-height:1.4}
.ok{color:#34d399}.err{color:#f87171}.wait{color:#22d3ee}
.hide{display:none}
</style></head><body><div class="card">
<h1>NTPlayone</h1><p class="sub">Envie a lista para a sua TV. Mantenha o celular no mesmo Wi-Fi e a TV ligada.</p>
<div class="tabs"><button class="tab on" id="tabX" type="button" onclick="setKind('xtream')">Xtream Codes</button><button class="tab" id="tabM" type="button" onclick="setKind('m3u')">Link M3U</button></div>
<form id="f" onsubmit="return send(event)">
<input type="hidden" name="t" value="TOKEN">
<input type="hidden" name="kind" id="kind" value="xtream">
<label>Nome da lista</label><input name="name" placeholder="Minha lista">
<div id="fx">
<label>Servidor</label><input name="server" placeholder="http://servidor.com:8080">
<label>Usuário</label><input name="user" placeholder="usuário">
<label>Senha</label><input name="pass" type="password" placeholder="senha">
</div>
<div id="fm" class="hide">
<label>Link da lista (M3U)</label><input name="url" placeholder="http://.../get.php?...">
</div>
<button class="go" id="bt" type="submit">Adicionar na TV</button>
</form><div id="msg"></div></div>
<script>
var job=null,polls=0;
function setKind(k){document.getElementById('kind').value=k;document.getElementById('tabX').className='tab'+(k=='xtream'?' on':'');document.getElementById('tabM').className='tab'+(k=='m3u'?' on':'');document.getElementById('fx').className=k=='xtream'?'':'hide';document.getElementById('fm').className=k=='m3u'?'':'hide';}
function say(t,c){var m=document.getElementById('msg');m.className=c||'';m.textContent=t;}
function send(e){e.preventDefault();job=null;polls=0;say('Enviando para a TV...','wait');document.getElementById('bt').disabled=true;
var data=new URLSearchParams(new FormData(e.target));
fetch('/add',{method:'POST',body:data}).then(function(r){return r.json()}).then(function(j){
  if(!j.ok){say(j.error||'Erro ao adicionar.','err');document.getElementById('bt').disabled=false;return;}
  job=j.job;say('Recebido! A TV está baixando a lista...','wait');poll();
}).catch(function(){say('Não foi possível falar com a TV. Confira se o celular está no mesmo Wi-Fi.','err');document.getElementById('bt').disabled=false;});
return false;}
function poll(){if(!job)return;fetch('/job/'+job).then(function(r){return r.json()}).then(function(j){
  polls++;
  if(j.state=='ok'){say('Lista adicionada! Pode voltar para a TV.','ok');document.getElementById('bt').disabled=false;job=null;return;}
  if(j.state=='error'){say(j.error||'Erro ao adicionar a lista.','err');document.getElementById('bt').disabled=false;job=null;return;}
  say('Baixando a lista na TV... isso pode levar alguns minutos. Não feche esta página.','wait');
  if(polls<300)setTimeout(poll,2000);
}).catch(function(){if(polls<300)setTimeout(poll,3000);});}
</script></body></html>
"""
    }
}