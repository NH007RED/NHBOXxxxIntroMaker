package com.nhbox.intromaker;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Studio Activity for NH BOX Intro Maker Pro v2.0.
 * - CORS-Free TMDB Poster Proxy (`shouldInterceptRequest`) so Canvas `toDataURL` never taints!
 * - Lock-step background H.264 YUV420 + AAC MP4 Encoder (`Mp4VideoEncoder`)
 * - Pure 16:9 Cinema Stage Preview + Built-in MP4 Video Player Dialog
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Spinner spTemplates, spResolution, spFps;
    private EditText etBrandText, etTmdbKey;
    private Button btnImportHtml, btnApplyBrand, btnExportMp4, btnPlayExported, btnShareVideo;
    private Button btnPreviewPlay, btnPreviewRestart;
    private Button chipNetflix, chipPrime, chipHotstar, chipMarvel;
    private ProgressBar progressBar;
    private TextView tvStatus, tvActiveTemplateTitle;

    private String currentHtmlContent = "";
    private Mp4VideoEncoder activeEncoder = null;
    private File lastExportedMp4 = null;
    private byte[] pendingWavAudioBytes = null;
    private int currentFrameIdx = 0;
    private int totalExportFrames = 0;
    private int exportFps = 30;
    private boolean isExporting = false;
    private long exportStartTimeMs = 0;

    private final ExecutorService encodeExecutor = Executors.newSingleThreadExecutor();

    private final String[] TEMPLATE_NAMES = new String[]{
            "Netflix Cinema Intro",
            "Amazon Prime Video Intro",
            "Disney+ Hotstar Intro",
            "Marvel Studios Poster Intro"
    };

    private final String[] TEMPLATE_FILES = new String[]{
            "intros/netflix_nh_box_realistic_tmdb.html",
            "intros/amazon_prime_nh_box_realistic_tmdb.html",
            "intros/disney_plus_hotstar_nh_box_realistic_tmdb.html",
            "intros/marvel_nh_box_realistic_tmdb.html"
    };

    private final ActivityResultLauncher<String> htmlPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    loadExternalHtmlUri(uri);
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        spTemplates = findViewById(R.id.spTemplates);
        spResolution = findViewById(R.id.spResolution);
        spFps = findViewById(R.id.spFps);
        etBrandText = findViewById(R.id.etBrandText);
        etTmdbKey = findViewById(R.id.etTmdbKey);
        btnImportHtml = findViewById(R.id.btnImportHtml);
        btnApplyBrand = findViewById(R.id.btnApplyBrand);
        btnExportMp4 = findViewById(R.id.btnExportMp4);
        btnPlayExported = findViewById(R.id.btnPlayExported);
        btnShareVideo = findViewById(R.id.btnShareVideo);
        btnPreviewPlay = findViewById(R.id.btnPreviewPlay);
        btnPreviewRestart = findViewById(R.id.btnPreviewRestart);
        chipNetflix = findViewById(R.id.chipNetflix);
        chipPrime = findViewById(R.id.chipPrime);
        chipHotstar = findViewById(R.id.chipHotstar);
        chipMarvel = findViewById(R.id.chipMarvel);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvActiveTemplateTitle = findViewById(R.id.tvActiveTemplateTitle);

        setupSpinners();
        setupWebView();
        setupButtons();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            loadExternalHtmlUri(intent.getData());
        } else {
            selectTemplateChip(0);
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> tplAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, TEMPLATE_NAMES);
        spTemplates.setAdapter(tplAdapter);

        String[] resolutions = new String[]{
                "Full HD 1080p (1920x1080 • Fast & Crisp)",
                "HD 720p (1280x720 • Ultra-Fast Export)",
                "4K Ultra HD (3840x2160 • Master Quality)"
        };
        spResolution.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resolutions));

        String[] fpsOptions = new String[]{"30 FPS (Fast)", "60 FPS (Smooth)"};
        spFps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fpsOptions));
    }

    private void selectTemplateChip(int index) {
        if (isExporting) return;
        Button[] chips = new Button[]{chipNetflix, chipPrime, chipHotstar, chipMarvel};
        for (int i = 0; i < chips.length; i++) {
            chips[i].setBackgroundResource(i == index ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
            chips[i].setTextColor(i == index ? 0xFFFFFFFF : 0xFFCBD5E1);
        }
        tvActiveTemplateTitle.setText("🔴 LIVE STAGE • " + TEMPLATE_NAMES[index]);
        loadAssetTemplate(index);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlStr = request.getUrl().toString();
                // Proxy TMDB Poster images with explicit CORS headers (`Access-Control-Allow-Origin: *`)
                // so HTML5 Canvas is NEVER tainted and `cv.toDataURL` always succeeds!
                if (urlStr.contains("image.tmdb.org/t/p/")) {
                    try {
                        URL url = new URL(urlStr);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
                        conn.setRequestProperty("User-Agent", "NHBOX-Studio/2.0");
                        conn.connect();
                        String mime = conn.getContentType();
                        if (mime == null) mime = "image/jpeg";
                        InputStream is = conn.getInputStream();
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Access-Control-Allow-Origin", "*");
                        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
                        headers.put("Cache-Control", "max-age=86400");
                        return new WebResourceResponse(mime, "binary", 200, "OK", headers, is);
                    } catch (Exception e) {
                        return super.shouldInterceptRequest(view, request);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
    }

    private void setupButtons() {
        chipNetflix.setOnClickListener(v -> selectTemplateChip(0));
        chipPrime.setOnClickListener(v -> selectTemplateChip(1));
        chipHotstar.setOnClickListener(v -> selectTemplateChip(2));
        chipMarvel.setOnClickListener(v -> selectTemplateChip(3));

        btnImportHtml.setOnClickListener(v -> {
            if (!isExporting) {
                htmlPickerLauncher.launch("text/html");
            }
        });

        btnPreviewPlay.setOnClickListener(v -> {
            if (!isExporting) {
                webView.evaluateJavascript("(function(){ var b=document.getElementById('pv'); if(b) b.click(); })();", null);
            }
        });

        btnPreviewRestart.setOnClickListener(v -> {
            if (!isExporting) {
                webView.evaluateJavascript("(function(){ if(window.stopPlay) window.stopPlay(); if(window.playAt) window.playAt(0); })();", null);
            }
        });

        btnApplyBrand.setOnClickListener(v -> {
            if (!isExporting && !currentHtmlContent.isEmpty()) {
                renderCustomizedHtmlIntoWebView();
                Toast.makeText(this, "Live Brand & TMDB Key Applied!", Toast.LENGTH_SHORT).show();
            }
        });

        btnExportMp4.setOnClickListener(v -> {
            if (!isExporting) {
                startUltraHdExport();
            }
        });

        btnPlayExported.setOnClickListener(v -> {
            if (lastExportedMp4 != null && lastExportedMp4.exists()) {
                showBuiltInVideoPlayer(lastExportedMp4);
            }
        });

        btnShareVideo.setOnClickListener(v -> {
            if (lastExportedMp4 != null && lastExportedMp4.exists()) {
                shareExportedVideo(lastExportedMp4);
            }
        });
    }

    private void loadAssetTemplate(int index) {
        try (InputStream is = getAssets().open(TEMPLATE_FILES[index])) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            currentHtmlContent = baos.toString(StandardCharsets.UTF_8.name());
            renderCustomizedHtmlIntoWebView();
            tvStatus.setText("Ready • " + TEMPLATE_NAMES[index] + " loaded.");
        } catch (Exception e) {
            tvStatus.setText("Error loading asset: " + e.getMessage());
        }
    }

    private void loadExternalHtmlUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            currentHtmlContent = baos.toString(StandardCharsets.UTF_8.name());
            renderCustomizedHtmlIntoWebView();
            String name = getFileName(uri);
            tvActiveTemplateTitle.setText("🔴 CUSTOM HTML • " + name);
            tvStatus.setText("Imported custom HTML: " + name);
        } catch (Exception e) {
            tvStatus.setText("Failed to read HTML file: " + e.getMessage());
        }
    }

    /**
     * Injects the user's custom Brand Name, TMDB API Key, and clean Full-Bleed Stage CSS
     * so the WebView shows ONLY the 16:9 `#cv` canvas without inner HTML scrollbars or buttons.
     */
    private void renderCustomizedHtmlIntoWebView() {
        String brand = etBrandText.getText().toString().trim();
        if (brand.isEmpty()) brand = "NH BOX";
        String tmdbKey = etTmdbKey.getText().toString().trim();
        if (tmdbKey.isEmpty()) tmdbKey = "2e211dfda888f7cc55ce433d743f9bc3";

        String quote = String.valueOf((char) 34);
        String compactBrand = brand.replace(" ", "");
        String modified = currentHtmlContent
                .replace("2e211dfda888f7cc55ce433d743f9bc3", tmdbKey)
                .replace(quote + "NH BOX" + quote, quote + brand + quote)
                .replace(quote + "NHBOX" + quote, quote + compactBrand + quote);

        // Inject Stage Mode CSS so inside the mobile preview only the 16:9 Canvas fills the box cleanly
        String stageCss = "<style>"
                + "html,body{margin:0!important;padding:0!important;background:#000!important;overflow:hidden!important;height:100%!important;display:flex!important;align-items:center!important;justify-content:center!important;}"
                + ".header,.controls-bar,.status-bar,.ctl,h1,p,#st{display:none!important;}"
                + ".wrap,.w,.canvas-holder{width:100%!important;max-width:100%!important;margin:0!important;padding:0!important;border:none!important;border-radius:0!important;box-shadow:none!important;}"
                + "canvas#cv{width:100vw!important;height:100vh!important;object-fit:contain!important;border:none!important;border-radius:0!important;}"
                + "</style>";

        modified = modified.replace("</head>", stageCss + "</head>");

        webView.loadDataWithBaseURL("https://api.themoviedb.org/", modified, "text/html", "UTF-8", null);
    }

    private void startUltraHdExport() {
        int resPos = spResolution.getSelectedItemPosition();
        int outW = 1920, outH = 1080;
        if (resPos == 1) {
            outW = 1280;
            outH = 720;
        } else if (resPos == 2) {
            outW = 3840;
            outH = 2160;
        }

        exportFps = (spFps.getSelectedItemPosition() == 1) ? 60 : 30;

        try {
            File exportDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "NHBOX_Intros");
            if (!exportDir.exists()) exportDir.mkdirs();

            String fileName = "NHBOX_Intro_" + outW + "x" + outH + "_" + exportFps + "fps_" + System.currentTimeMillis() + ".mp4";
            lastExportedMp4 = new File(exportDir, fileName);

            activeEncoder = new Mp4VideoEncoder(lastExportedMp4, outW, outH, exportFps);
            currentFrameIdx = 0;
            pendingWavAudioBytes = null;
            isExporting = true;
            exportStartTimeMs = System.currentTimeMillis();

            btnExportMp4.setEnabled(false);
            btnPlayExported.setEnabled(false);
            btnShareVideo.setEnabled(false);
            progressBar.setProgress(0);
            tvStatus.setText("Synthesizing studio audio track & preparing frames...");

            // Lock-step export controller injected into WebView:
            // 1. Renders offline WAV audio first and pushes it via Android.pushWavBase64
            // 2. Starts lock-step frame rendering (`window.__exportNextFrame`) where Java
            //    processes each frame on a background thread and requests the next one!
            String jsExportInit = "(function(){"
                    + "if(window.stopPlay) window.stopPlay();"
                    + "var dur = (typeof TOTAL !== 'undefined' && TOTAL > 0) ? TOTAL : 8.5;"
                    + "window.__expFps = " + exportFps + ";"
                    + "window.__expTotal = Math.ceil(dur * window.__expFps);"
                    + "window.__expFrame = 0;"
                    + "window.__exportStep = function(){"
                    + "  if(window.__expFrame >= window.__expTotal){ Android.done(); return; }"
                    + "  var t = window.__expFrame / window.__expFps;"
                    + "  frameAt(t);"
                    + "  var dataUrl = '';"
                    + "  try { dataUrl = cv.toDataURL('image/jpeg', 0.90); } catch(e) { dataUrl = ''; }"
                    + "  Android.onFrameCaptured(dataUrl, window.__expFrame, window.__expTotal);"
                    + "  window.__expFrame++;"
                    + "};"
                    + "if(window.renderSfxWav){"
                    + "  window.renderSfxWav(function(blob){"
                    + "    var r = new FileReader();"
                    + "    r.onloadend = function(){ Android.pushWavBase64(r.result); window.__exportStep(); };"
                    + "    r.readAsDataURL(blob);"
                    + "  });"
                    + "} else { window.__exportStep(); }"
                    + "})();";

            webView.evaluateJavascript(jsExportInit, null);

        } catch (Exception e) {
            isExporting = false;
            btnExportMp4.setEnabled(true);
            tvStatus.setText("Encoder init error: " + e.getMessage());
        }
    }

    /**
     * Built-in Modal Video Player so the user can watch the exported MP4 with sound right inside the app.
     */
    private void showBuiltInVideoPlayer(File mp4File) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        VideoView videoView = new VideoView(this);
        videoView.setVideoPath(mp4File.getAbsolutePath());
        MediaController mc = new MediaController(this);
        mc.setAnchorView(videoView);
        videoView.setMediaController(mc);

        dialog.setContentView(videoView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.show();
        videoView.start();
    }

    private void publishToGallery(File mp4File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, mp4File.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NHBOX_Intros");
                Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri);
                         FileInputStream fis = new FileInputStream(mp4File)) {
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = fis.read(buf)) != -1) {
                            os.write(buf, 0, r);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void shareExportedVideo(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("video/mp4");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share or Play Exported Ultra HD Intro"));
    }

    private String getFileName(Uri uri) {
        String result = "custom_intro.html";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * JavaScript Bridge (`window.Android`) supporting both lock-step fast export (`onFrameCaptured`)
     * and legacy `NH.run` (`pushFrame`, `progress`, `done`).
     */
    public class AndroidBridge {

        @JavascriptInterface
        public void pushWavBase64(String dataUrl) {
            if (dataUrl == null) return;
            try {
                int comma = dataUrl.indexOf(',');
                String b64 = (comma >= 0) ? dataUrl.substring(comma + 1) : dataUrl;
                pendingWavAudioBytes = Base64.decode(b64, Base64.DEFAULT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void onFrameCaptured(String dataUrl, int frameIdx, int totalFrames) {
            if (!isExporting || activeEncoder == null) return;
            encodeExecutor.execute(() -> {
                try {
                    if (dataUrl != null && !dataUrl.isEmpty()) {
                        int commaIdx = dataUrl.indexOf(',');
                        String base64Part = (commaIdx >= 0) ? dataUrl.substring(commaIdx + 1) : dataUrl;
                        byte[] jpegBytes = Base64.decode(base64Part, Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                        if (bmp != null) {
                            activeEncoder.encodeFrame(bmp, frameIdx);
                            bmp.recycle();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                int pct = (totalFrames > 0) ? (int) (((frameIdx + 1) * 100L) / totalFrames) : 0;
                runOnUiThread(() -> {
                    progressBar.setProgress(pct);
                    tvStatus.setText("Encoding MP4 Frame " + (frameIdx + 1) + " / " + totalFrames + " (" + pct + "%)");
                    if (isExporting) {
                        webView.evaluateJavascript("if(window.__exportStep) window.__exportStep();", null);
                    }
                });
            });
        }

        @JavascriptInterface
        public void pushFrame(String dataUrl) {
            if (!isExporting || activeEncoder == null || dataUrl == null) return;
            final int fIdx = currentFrameIdx++;
            encodeExecutor.execute(() -> {
                try {
                    int commaIdx = dataUrl.indexOf(',');
                    String base64Part = (commaIdx >= 0) ? dataUrl.substring(commaIdx + 1) : dataUrl;
                    byte[] jpegBytes = Base64.decode(base64Part, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                    if (bmp != null) {
                        activeEncoder.encodeFrame(bmp, fIdx);
                        bmp.recycle();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @JavascriptInterface
        public void progress(int frame, int totalFrames) {
            int pct = (totalFrames > 0) ? (int) ((frame * 100L) / totalFrames) : 0;
            runOnUiThread(() -> {
                progressBar.setProgress(pct);
                tvStatus.setText("Encoding Frame " + frame + " / " + totalFrames + " (" + pct + "%)");
            });
        }

        @JavascriptInterface
        public void done() {
            if (!isExporting) return;
            isExporting = false;
            runOnUiThread(() -> tvStatus.setText("Muxing H.264 Video + AAC Audio into MP4..."));

            encodeExecutor.execute(() -> {
                if (activeEncoder != null) {
                    activeEncoder.finishAndMuxAudio(pendingWavAudioBytes);
                    activeEncoder = null;
                }
                if (lastExportedMp4 != null && lastExportedMp4.exists()) {
                    publishToGallery(lastExportedMp4);
                }
                long elapsedSec = Math.max(1, (System.currentTimeMillis() - exportStartTimeMs) / 1000);
                runOnUiThread(() -> {
                    progressBar.setProgress(100);
                    btnExportMp4.setEnabled(true);
                    btnPlayExported.setEnabled(true);
                    btnShareVideo.setEnabled(true);
                    tvStatus.setText("✅ Exported in " + elapsedSec + "s! Saved to Movies/NHBOX_Intros");
                    Toast.makeText(MainActivity.this, "MP4 Ready! Tap '▶ Watch' to play.", Toast.LENGTH_LONG).show();
                });
            });
        }
    }
}
