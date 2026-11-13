package com.nawazishali.sonora;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST = 2002;
    private String songsJson = "[]";

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0d0218"));
        getWindow().setNavigationBarColor(Color.parseColor("#0d0218"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0218"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.addJavascriptInterface(new Bridge(), "SonoraNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url != null && "songs.local".equals(url.getHost())) {
                    return serveSong(url, request.getRequestHeaders());
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(Intent.createChooser(intent, "Choose songs"), FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (hasAudioPermission()) {
            refreshSongs();
            web.loadUrl("file:///android_asset/www/index.html");
        } else {
            // Ask for music access first; the app opens right after the answer.
            requestPermissions(new String[]{ audioPermission() }, PERMISSION_REQUEST);
            web.loadUrl("file:///android_asset/www/index.html");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            refreshSongs();
            if (web != null) {
                web.evaluateJavascript("if(window.onSonoraAccessChanged)window.onSonoraAccessChanged();", null);
            }
        }
    }

    /* ---------------- phone's music library ---------------- */

    private void refreshSongs() {
        if (!hasAudioPermission()) { songsJson = "[]"; return; }
        JSONArray arr = new JSONArray();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        };
        try (Cursor cursor = getContentResolver().query(
                collection, projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext() && arr.length() < 3000) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long durMs = cursor.getLong(durCol);
                    if (title == null || title.trim().isEmpty()) title = "Unknown song";
                    if (artist == null || artist.equals("<unknown>")) artist = "Unknown artist";
                    JSONObject o = new JSONObject();
                    o.put("id", "dev" + id);
                    o.put("title", title);
                    o.put("artist", artist);
                    o.put("duration", Math.round(durMs / 1000.0));
                    o.put("src", "https://songs.local/" + id);
                    arr.put(o);
                }
            }
        } catch (Exception e) {
            // leave whatever was collected
        }
        songsJson = arr.toString();
    }

    private WebResourceResponse serveSong(Uri url, Map<String, String> headers) {
        try {
            String path = url.getPath();
            if (path == null || path.length() < 2) return null;
            long id = Long.parseLong(path.substring(1));
            Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            ContentResolver cr = getContentResolver();
            String mime = cr.getType(contentUri);
            if (mime == null) mime = "audio/mpeg";

            ParcelFileDescriptor pfd = cr.openFileDescriptor(contentUri, "r");
            if (pfd == null) return null;
            long total = pfd.getStatSize();
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());

            String range = null;
            if (headers != null) {
                range = headers.get("Range");
                if (range == null) range = headers.get("range");
            }
            Map<String, String> resp = new HashMap<>();
            resp.put("Access-Control-Allow-Origin", "*");
            resp.put("Accept-Ranges", "bytes");
            if (range != null && range.startsWith("bytes=") && total > 0) {
                String spec = range.substring(6).split(",")[0];
                String[] parts = spec.split("-", -1);
                long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
                long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : total - 1;
                if (end >= total) end = total - 1;
                if (start > end || start >= total) {
                    return new WebResourceResponse(mime, null, 416, "Range Not Satisfiable", resp, null);
                }
                fis.getChannel().position(start);
                long len = end - start + 1;
                resp.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
                resp.put("Content-Length", String.valueOf(len));
                return new WebResourceResponse(mime, null, 206, "Partial Content", resp, fis);
            }
            if (total > 0) resp.put("Content-Length", String.valueOf(total));
            return new WebResourceResponse(mime, null, 200, "OK", resp, fis);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------------- bridge the web player can call ---------------- */

    public class Bridge {
        @JavascriptInterface
        public String getSongs() {
            return songsJson;
        }

        @JavascriptInterface
        public boolean hasAccess() {
            return hasAudioPermission();
        }

        @JavascriptInterface
        public void requestAccess() {
            runOnUiThread(() -> {
                if (!hasAudioPermission()) {
                    requestPermissions(new String[]{ audioPermission() }, PERMISSION_REQUEST);
                }
            });
        }
    }

    /* ---------------- file chooser (Library "add" button) ---------------- */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{ data.getData() };
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript(
            "(function(){var np=document.getElementById('nowPlaying');" +
            "if(np&&!np.hidden){np.hidden=true;return 'closed';}" +
            "var ms=document.querySelectorAll('.modal');" +
            "for(var i=0;i<ms.length;i++){if(!ms[i].hidden){ms[i].hidden=true;return 'closed';}}" +
            "return '';})()",
            value -> {
                if (value == null || value.equals("\"\"") || value.equals("null")) {
                    MainActivity.super.onBackPressed();
                }
            });
    }

    @Override
    protected void onPause() {
        // Deliberately do NOT pause the WebView: songs keep playing in the background.
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
