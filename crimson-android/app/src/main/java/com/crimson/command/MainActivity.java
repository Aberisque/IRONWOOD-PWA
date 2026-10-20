package com.crimson.command;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    private static final String KEY_ALIAS = "CrimsonCommandSecureStorageV2";
    private WebView web;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean cancelled = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 7, 12));
        getWindow().setNavigationBarColor(Color.rgb(8, 7, 12));
        getWindow().getDecorView().setSystemUiVisibility(0);
        prefs = getSharedPreferences("crimson_v2", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(8, 7, 12));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 26) web.getSettings().setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new Bridge(), "Crimson");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String value = req.getUrl().toString();
                if (PAGE.equals(value)) return false;
                String scheme = req.getUrl().getScheme();
                if (req.isForMainFrame() && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl())); }
                    catch (Exception ex) { toast("No browser installed"); }
                }
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) toast("Could not load the local dashboard.");
            }
        });
        setContentView(web);
        web.loadUrl(PAGE);
    }

    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }
    private void emit(String type, JSONObject data) {
        try { data.put("type", type); } catch(Exception ignored) {}
        String payload = data.toString();
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window.nativeEvent(" + payload + ")", null);
        });
    }
    private void emitError(String context, Exception err) {
        String msg = err.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = "Unknown error";
        emit("error", obj("context", context, "message", msg.length() > 700 ? msg.substring(0,700) : msg));
    }
    private static JSONObject obj(Object... args) {
        JSONObject j = new JSONObject();
        try { for (int i=0; i+1<args.length; i+=2) j.put(String.valueOf(args[i]), args[i+1]); }
        catch (Exception ignored) {}
        return j;
    }
    private static boolean allowed(String name) {
        return name != null && (name.equals("gemini") || name.equals("groq") || name.equals("github") || name.equals("bluesky"));
    }
    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey)store.getKey(KEY_ALIAS,null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    private String encrypt(String raw) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,secretKey());
        byte[] iv=cipher.getIV();
        byte[] encrypted=cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
        byte[] combined=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,combined,0,iv.length);
        System.arraycopy(encrypted,0,combined,iv.length,encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }
    private String decrypt(String blob) throws Exception {
        if (blob==null || blob.isEmpty()) return "";
        byte[] combined=Base64.getDecoder().decode(blob);
        if (combined.length<29) return "";
        byte[] iv=new byte[12];
        System.arraycopy(combined,0,iv,0,12);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secretKey(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(combined,12,combined.length-12),StandardCharsets.UTF_8);
    }
    private String key(String provider) throws Exception {
        if (!allowed(provider)) throw new Exception("Unsupported provider");
        return decrypt(prefs.getString("cred_"+provider,""));
    }
    private JSONObject snapshot() {
        JSONObject result = obj("app","CRIMSON COMMAND","version","2.0.0","localMode",true,
            "provider",prefs.getString("provider","gemini"),
            "geminiModel",prefs.getString("model_gemini","gemini-2.5-flash"),
            "groqModel",prefs.getString("model_groq","llama-3.3-70b-versatile"),
            "blueskyHandle",prefs.getString("bluesky_handle",""),
            "busy",running.get());
        try {
            for (String p:new String[]{"gemini","groq","github","bluesky"})
                result.put("has_"+p,!prefs.getString("cred_"+p,"").isEmpty());
            result.put("history",new JSONArray(prefs.getString("history","[]")));
        }catch(Exception ignored){}
        return result;
    }
    private static String read(InputStream input, int limit) throws Exception {
        try (InputStream in=input) {
            if(in==null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n, total = 0;
            while ((n = in.read(buf,0,Math.min(buf.length,limit-total))) > 0) {
                out.write(buf,0,n);total+=n;if(total>=limit)break;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static String request(String method,String endpoint,String authHeader,String authValue, JSONObject body) throws Exception {
        URL url=new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new Exception("A secure HTTPS endpoint is required.");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(14000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("User-Agent","CrimsonCommandAndroid/2.0");
        if (authHeader!=null) conn.setRequestProperty(authHeader,authValue);
        if(body!=null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try (OutputStream out=conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code=conn.getResponseCode();
        String response=read(code>=200&&code<300?conn.getInputStream():conn.getErrorStream(),1024*1024);
        conn.disconnect();
        if(code<200||code>=300) {
            String info=response.length()>320?response.substring(0,320):response;
            throw new Exception("HTTP "+code+(code==429?" — Free-tier quota/rate limit reached.":"") +": "+info);
        }
        return response;
    }
    private String invoke(String provider,String prompt) throws Exception {
        String credential=key(provider);
        if(credential.isEmpty()) throw new Exception("Connect "+provider+" in the Vault before running AI missions.");
        if(provider.equals("gemini")) {
            String model=prefs.getString("model_gemini","gemini-2.5-flash");
            if(!model.matches("[a-zA-Z0-9._-]{2,75}")) throw new Exception("Invalid Gemini model ID");
            String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";
            JSONObject body=obj("contents",new JSONArray().put(obj("parts",new JSONArray().put(obj("text",prompt)))),
                "generationConfig",obj("temperature",0.5,"maxOutputTokens",1600));
            String response=request("POST",endpoint,"x-goog-api-key",credential,body);
            JSONObject json=new JSONObject(response);
            JSONArray parts=json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");
            StringBuilder answer=new StringBuilder();
            for(int i=0;i<parts.length();i++) answer.append(parts.getJSONObject(i).optString("text",""));
            if(answer.length()==0) throw new Exception("Gemini returned no text; check safety blocks or model configuration.");
            return answer.toString();
        } else if(provider.equals("groq")) {
            String model=prefs.getString("model_groq","llama-3.3-70b-versatile");
            if(!model.matches("[a-zA-Z0-9._/-]{2,90}")) throw new Exception("Invalid Groq model ID");
            JSONObject body=obj("model",model,
                "messages",new JSONArray().put(obj("role","user","content",prompt)),
                "temperature",0.5,"max_tokens",1600);
            JSONObject json=new JSONObject(request("POST","https://api.groq.com/openai/v1/chat/completions",
                "Authorization","Bearer "+credential,body));
            return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        }
        throw new Exception("Choose Gemini or Groq for missions.");
    }
    private void persistMission(JSONObject m) throws Exception {
        JSONArray old=new JSONArray(prefs.getString("history","[]"));
        JSONArray next=new JSONArray().put(m);
        for(int i=0;i<old.length()&&i<29;i++) next.put(old.get(i));
        prefs.edit().putString("history",next.toString()).apply();
    }
    private void runAgentMission(String goal,String division,String provider) {
        if(!running.compareAndSet(false,true)) { emit("error",obj("context","mission","message","A mission is already running."));return; }
        cancelled=false;
        executor.execute(() -> {
            long start=System.currentTimeMillis();
            String[] roles={"Strategist","Specialist","Quality auditor"};
            JSONArray steps=new JSONArray();
            String output="";
            try{
                emit("mission_started",obj("goal",goal,"division",division,"provider",provider));
                for(int i=0;i<3;i++){
                    if(cancelled) throw new Exception("Mission cancelled by operator.");
                    emit("mission_progress",obj("stage",i+1,"role",roles[i],"message","Calling "+provider+"…"));
                    String prompt;
                    if(i==0) prompt="You are a high-skill strategist in the "+division+" division. Create a concise actionable plan with measurable deliverables for this mission. Do not claim to have performed any actions. Mission: "+goal;
                    else if(i==1) prompt="You are the "+division+" specialist in a three-agent workflow. Work out a concrete, useful deliverable using this plan. Clearly label what is proposed versus actually completed. Mission: "+goal+"\n\nStrategist plan:\n"+output;
                    else prompt="You are an independent quality auditor. Critique the following proposed deliverable for factual gaps, hallucinations, security and practical issues. Provide a refined final answer with a clear next-actions list. Do not claim that tools were executed. Mission: "+goal+"\n\nDraft:\n"+output;
                    output=invoke(provider,prompt);
                    steps.put(obj("role",roles[i],"text",output));
                    emit("mission_progress",obj("stage",i+1,"role",roles[i],"message","Completed","text",output));
                }
                if(cancelled) throw new Exception("Mission cancelled by operator.");
                JSONObject mission=obj("id",String.valueOf(System.currentTimeMillis()),"goal",goal,
                    "division",division,"provider",provider,"status","completed",
                    "createdAt",Instant.ofEpochMilli(start).toString(),"durationSeconds",(System.currentTimeMillis()-start)/1000,
                    "steps",steps,"result",output);
                persistMission(mission);
                emit("mission_done",obj("mission",mission,"snapshot",snapshot()));
            }catch(Exception e){
                emitError("mission",e);
                try{
                    JSONObject m=obj("id",String.valueOf(start),"goal",goal,"division",division,"provider",provider,
                        "status",cancelled?"cancelled":"failed","createdAt",Instant.ofEpochMilli(start).toString(),
                        "error",e.getMessage()==null?"Unknown":e.getMessage(),"steps",steps);
                    persistMission(m);
                }catch(Exception ignored){}
                emit("snapshot",snapshot());
            }finally{running.set(false);emit("idle",obj("busy",false));}
        });
    }
    private static String safeRepo(String repo) throws Exception {
        String value=repo.trim();
        if(!value.matches("[a-zA-Z0-9_.-]{1,100}/[a-zA-Z0-9_.-]{1,100}")) throw new Exception("Use owner/repository format");
        return value;
    }
    private static String safeWorkflow(String workflow) throws Exception {
        String s=workflow.trim();
        if(!s.matches("[a-zA-Z0-9_.-]{1,100}")) throw new Exception("Use a workflow filename such as android.yml");
        return s;
    }
    private void githubRepos() {
        executor.execute(()->{
            try {
                String token=key("github");
                if(token.isEmpty())throw new Exception("Add a GitHub fine-grained access token first.");
                String response=request("GET","https://api.github.com/user/repos?per_page=30&sort=updated","Authorization","Bearer "+token,null);
                JSONArray raw=new JSONArray(response), repos=new JSONArray();
                for(int i=0;i<raw.length();i++){
                    JSONObject r=raw.getJSONObject(i);
                    repos.put(obj("name",r.optString("full_name"),"url",r.optString("html_url"),"visibility",r.optString("visibility")));
                }
                emit("github_repos",obj("repositories",repos));
            }catch(Exception e){emitError("github",e);}
        });
    }
    private void dispatch(String repo,String workflow,String branch) {
        executor.execute(()->{
            try{
                String token=key("github");
                if(token.isEmpty())throw new Exception("Connect GitHub first.");
                String full=safeRepo(repo),wf=safeWorkflow(workflow),ref=branch.trim();
                if(!ref.matches("[a-zA-Z0-9_./-]{1,120}")||ref.contains(".."))throw new Exception("Invalid Git ref");
                request("POST","https://api.github.com/repos/"+full+"/actions/workflows/"+wf+"/dispatches",
                    "Authorization","Bearer "+token,obj("ref",ref));
                emit("success",obj("context","github","message","GitHub workflow dispatched. Check GitHub Actions for execution status."));
            }catch(Exception e){emitError("github",e);}
        });
    }
    private void webhook(String target,String body) {
        executor.execute(()->{
            try{
                URI u=new URI(target.trim());
                if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null)
                    throw new Exception("Use a trusted HTTPS webhook URL.");
                JSONObject payload=obj("source","Crimson Command Android","event","manual_trigger",
                    "timestamp",Instant.now().toString(),"message",body);
                String res=request("POST",target.trim(),null,null,payload);
                emit("success",obj("context","webhook","message","Webhook accepted by server. This does not establish downstream workflow success.","response",res.substring(0,Math.min(res.length(),500))));
            }catch(Exception e){emitError("webhook",e);}
        });
    }
    private void blueskyPublish(String text) {
        executor.execute(()->{
            try{
                String handle=prefs.getString("bluesky_handle","").trim();
                String password=key("bluesky");
                if(handle.isEmpty()||password.isEmpty())throw new Exception("Connect Bluesky handle and app password first.");
                if(text.trim().isEmpty()||text.codePointCount(0,text.length())>300)throw new Exception("Post must contain 1–300 Unicode characters.");
                String login=request("POST","https://bsky.social/xrpc/com.atproto.server.createSession",null,null,
                    obj("identifier",handle,"password",password));
                JSONObject session=new JSONObject(login);
                String jwt=session.getString("accessJwt"),did=session.getString("did");
                JSONObject record=obj("$type","app.bsky.feed.post","text",text,"createdAt",Instant.now().toString());
                String response=request("POST","https://bsky.social/xrpc/com.atproto.repo.createRecord",
                    "Authorization","Bearer "+jwt,
                    obj("repo",did,"collection","app.bsky.feed.post","record",record));
                JSONObject result=new JSONObject(response);
                emit("success",obj("context","bluesky","message","Bluesky post published.",
                    "uri",result.optString("uri","")));
            }catch(Exception e){emitError("bluesky",e);}
        });
    }

    public final class Bridge {
        @JavascriptInterface public String getSnapshot() {return snapshot().toString();}
        @JavascriptInterface public void saveCredential(String provider,String value) {
            if(!allowed(provider))return;
            executor.execute(()->{
                try{
                    String v=value.trim();
                    if(v.length()>2048)throw new Exception("Credential is too long");
                    prefs.edit().putString("cred_"+provider,v.isEmpty()?"":encrypt(v)).apply();
                    emit("success",obj("context","vault","message",provider+" credentials "+(v.isEmpty()?"removed":"saved securely")));
                    emit("snapshot",snapshot());
                }catch(Exception e){emitError("vault",e);}
            });
        }
        @JavascriptInterface public void saveOption(String option,String value){
            if(!option.equals("model_gemini")&&!option.equals("model_groq")&&!option.equals("bluesky_handle")&&!option.equals("provider"))return;
            if(value==null||value.length()>160)return;
            prefs.edit().putString(option,value.trim()).apply();
            emit("snapshot",snapshot());
        }
        @JavascriptInterface public void testModel(String provider){
            executor.execute(()->{
                try {String result=invoke(provider,"Reply with a short confirmation, starting with CONNECTED. This is a connectivity test.");emit("success",obj("context","test","message",provider+": "+result));}
                catch(Exception e){emitError("test",e);}
            });
        }
        @JavascriptInterface public void runMission(String goal,String division,String provider) {
            if(goal==null||goal.trim().length()<5||goal.length()>2500){emit("error",obj("context","mission","message","Mission description must be 5–2500 characters."));return;}
            if(!("Engineering".equals(division)||"Marketing".equals(division)||"Research".equals(division)||"Operations".equals(division))) {
                emit("error",obj("context","mission","message","Unknown agent division."));return;
            }
            if(!"gemini".equals(provider)&&!"groq".equals(provider)){emit("error",obj("context","mission","message","Choose Gemini or Groq."));return;}
            runAgentMission(goal.trim(),division,provider);
        }
        @JavascriptInterface public void cancelMission(){cancelled=true;}
        @JavascriptInterface public void listGitHub(){githubRepos();}
        @JavascriptInterface public void dispatchGithub(String repo,String workflow,String ref){dispatch(repo,workflow,ref);}
        @JavascriptInterface public void sendWebhook(String url,String body){webhook(url,body);}
        @JavascriptInterface public void postBluesky(String text){blueskyPublish(text);}
        @JavascriptInterface public void clearHistory(){
            prefs.edit().remove("history").apply();
            emit("snapshot",snapshot());
        }
        @JavascriptInterface public void openLink(String url) {
            try {
                URI uri = new URI(url);
                if(!"https".equalsIgnoreCase(uri.getScheme()))return;
                runOnUiThread(()->{
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
                    catch(Exception e){toast("Could not open URL");}
                });
            }catch(Exception ignored){}
        }
    }
    @Override public void onBackPressed(){ if(web!=null&&web.canGoBack()){web.goBack();return;}super.onBackPressed(); }
    @Override protected void onDestroy(){ executor.shutdownNow();if(web!=null)web.destroy();super.onDestroy(); }
}
