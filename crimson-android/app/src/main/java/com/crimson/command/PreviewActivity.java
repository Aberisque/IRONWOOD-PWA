package com.crimson.command;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

// Dedicated sandbox view: generated scripts have NO access to the trusted Chromium/Android JS bridge.
// All network, file, and content access is disabled. No JavascriptInterface is registered.
public final class PreviewActivity extends Activity {
    private WebView preview;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,8,13));
        getWindow().setNavigationBarColor(Color.rgb(10,8,13));
        String id=getIntent().getStringExtra("artifactId");
        if(id==null || !id.matches("[0-9]{10,16}")) { finish();return; }
        try{
            File src=new File(getFilesDir(),"crimson-project-"+id+".html");
            if(!src.isFile() || src.length() > 180000){finish();return;}
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(FileInputStream in=new FileInputStream(src)){
                byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1) out.write(buf,0,n);
            }
            String html=new String(out.toByteArray(),StandardCharsets.UTF_8);
            LinearLayout root=new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.rgb(8,7,11));
            LinearLayout bar=new LinearLayout(this);
            bar.setPadding(9,7,9,7);
            bar.setBackgroundColor(Color.rgb(24,14,22));
            Button back=new Button(this);
            back.setAllCaps(false);
            back.setText("← BACK");
            back.setTextColor(Color.WHITE);
            back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(155,22,52)));
            back.setOnClickListener(v->finish());
            TextView info=new TextView(this);
            info.setText("  LOCAL PREVIEW • AI-GENERATED / UNTESTED");
            info.setTextColor(Color.rgb(240,171,190));
            info.setTextSize(10f);
            info.setGravity(android.view.Gravity.CENTER_VERTICAL);
            bar.addView(back,new LinearLayout.LayoutParams(-2,-2));
            bar.addView(info,new LinearLayout.LayoutParams(0,-1,1));
            root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
            preview=new WebView(this);
            preview.setBackgroundColor(Color.rgb(14,12,16));
            WebSettings settings=preview.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setBlockNetworkLoads(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setDefaultTextEncodingName("UTF-8");
            preview.setWebChromeClient(new WebChromeClient());
            preview.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                    return true;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
            });
            root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
            setContentView(root);
            preview.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);
        }catch(Exception ex){finish();}
    }
    @Override public void onBackPressed(){finish();}
    @Override protected void onDestroy(){
        if(preview!=null){preview.loadUrl("about:blank");preview.destroy();}
        super.onDestroy();
    }
}
