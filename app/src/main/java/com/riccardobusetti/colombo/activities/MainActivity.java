package com.riccardobusetti.colombo.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.graphics.Palette;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.amqtech.permissions.helper.objects.Permission;
import com.amqtech.permissions.helper.objects.Permissions;
import com.amqtech.permissions.helper.objects.PermissionsActivity;
import com.riccardobusetti.colombo.R;
import com.riccardobusetti.colombo.util.IconMenuPopupHelper;
import com.riccardobusetti.colombo.util.RecentSuggestionsProvider;
import com.riccardobusetti.colombo.util.StaticUtils;
import com.riccardobusetti.colombo.view.CustomWebChromeClient;
import com.riccardobusetti.colombo.view.ObservableWebView;

import java.lang.reflect.Field;

import static com.riccardobusetti.colombo.R.id.webview;

public class MainActivity extends PlaceholderUiActivity {

    private static final int REQUEST_SELECT_FILE = 100;
    private static final int FILE_CHOOSER_RESULT_CODE = 1;

    public static final int SEARCH_GOOGLE = 0, SEARCH_YAHOO = 1, SEARCH_DUCKDUCKGO = 2, SEARCH_BING = 3;

    private ValueCallback<Uri[]> uploadMessage;
    private ValueCallback<Uri> uploadMessagePreLollipop;

    private Toolbar toolbar;
    private AppBarLayout appbar;
    private CoordinatorLayout coordinatorLayout;

    private SearchView searchView;
    private SearchManager searchManager;
    private SearchRecentSuggestions suggestions;
    boolean desktop = true;
    private ObservableWebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private CustomWebChromeClient webChromeClient;
    private NetworkChangeReceiver networkChangeReceiver;

    private boolean isIncognito;
    private boolean isDekstop;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean("first_time", true)) {
            startActivity(new Intent(this, MainIntroActivity.class));
            prefs.edit().putBoolean("first_time", false).apply();
        }

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        appbar = (AppBarLayout) findViewById(R.id.appbar);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
        webView = (ObservableWebView) findViewById(webview);
        String action = getIntent().getAction();
        if (action != null && action.matches(Intent.ACTION_VIEW))
            webView.loadUrl(getIntent().getDataString());
        else
            webView.loadUrl(getHomepage());
        webView.setNavigationViews(findViewById(R.id.previous), findViewById(R.id.next));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(prefs.getBoolean("javascript", true));
        webSettings.setGeolocationEnabled(prefs.getBoolean("location_services", true));
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setSaveFormData(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        webSettings.setSupportZoom(prefs.getBoolean("zooming", false));
        webSettings.setDisplayZoomControls(false);
        webSettings.setBuiltInZoomControls(true);
        webSettings.supportZoom();

        webSettings.setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        webSettings.setAllowFileAccess(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ONLY);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        final View cardView = findViewById(R.id.card), search = findViewById(R.id.search);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (cardView != null) {
                cardView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            cardView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            cardView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        int a = (cardView.getLeft() + cardView.getRight()) / 2;
                        int b = (cardView.getTop() + cardView.getBottom()) / 2;

                        int radius = cardView.getWidth();

                        Animator anim = ViewAnimationUtils.createCircularReveal(cardView, a, b, 0, radius);
                        anim.setDuration(500);
                        anim.start();
                    }
                });
            }
            if (search != null && search.getVisibility() == View.VISIBLE) {
                search.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down_in));
            }
        }

        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchView.setVisibility(View.VISIBLE);
                searchView.setIconified(false);
            }
        });

        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Without GPS permissions the location won't work!", Toast.LENGTH_SHORT).show();
            } else {
                locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                    }

                    @Override
                    public void onStatusChanged(String s, int i, Bundle bundle) {
                    }

                    @Override
                    public void onProviderEnabled(String s) {
                    }

                    @Override
                    public void onProviderDisabled(String s) {
                    }
                };
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1000, locationListener);
            }
        }

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap facIcon) {
                swipeRefreshLayout.setRefreshing(true);

                if (!isIncognito && suggestions != null) suggestions.saveRecentQuery(url, null);

                if (url.startsWith("https")) {
                    Drawable drawable = StaticUtils.getVectorDrawable(MainActivity.this, R.drawable.ic_search);
                    DrawableCompat.setTint(drawable, ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                    toolbar.setNavigationIcon(drawable);
                } if (url.startsWith("http")) {
                    Drawable drawable = StaticUtils.getVectorDrawable(MainActivity.this, R.drawable.ic_search);
                    DrawableCompat.setTint(drawable, ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                    toolbar.setNavigationIcon(drawable);
                } else {
                    Drawable drawable = StaticUtils.getVectorDrawable(MainActivity.this, R.drawable.ic_search);
                    DrawableCompat.setTint(drawable, ContextCompat.getColor(MainActivity.this, R.color.colorIconGrey));
                    toolbar.setNavigationIcon(drawable);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
                swipeRefreshLayout.setEnabled(false);
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, final String contentDisposition, final String mimeType, long contentLength) {
                final String filename1 = URLUtil.guessFileName(url, contentDisposition, mimeType);

                Snackbar snackbar = Snackbar.make(coordinatorLayout, "Download " + filename1 + "?", Snackbar.LENGTH_LONG);
                View snackBarView = snackbar.getView();
                snackbar.setActionTextColor(Color.parseColor("#FAFAFA"));
                snackBarView.setBackgroundColor(Color.parseColor("#4690CD"));
                snackbar.setAction("DOWNLOAD", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            launchPerms();
                        } else {
                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);

                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

                            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                            dm.enqueue(request);

                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");

                            Toast.makeText(MainActivity.this, "Downloading: " + filename, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                snackbar.show();
            }
        });

        webChromeClient = new CustomWebChromeClient(this) {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    Toast.makeText(MainActivity.this.getApplicationContext(), "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (prefs.getBoolean("dynamic_colors", true)) {
                    Palette.from(icon).generate(new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            setColor(palette.getVibrantColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimary)));
                        }
                    });
                }

                toolbar.setTitle(webView.getUrl());
            }
        };

        webView.setWebChromeClient(webChromeClient);

        appbar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                webView.setCanScrollVertically((appBarLayout.getHeight() - appBarLayout.getBottom()) != 0);
            }
        });

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_layout);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.swipeRefresh));

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == REQUEST_SELECT_FILE) {
                if (uploadMessage == null)
                    return;
                uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                uploadMessage = null;
            }
        } else if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessagePreLollipop == null)
                return;
            Uri result = intent == null || resultCode != MainActivity.RESULT_OK ? null : intent.getData();
            uploadMessagePreLollipop.onReceiveValue(result);
            uploadMessagePreLollipop = null;
        } else
            Snackbar.make(coordinatorLayout, R.string.msg_upload_failed, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else if (searchView.getVisibility() == View.VISIBLE){
            searchView.setVisibility(View.GONE);
            searchView.setIconified(false);
        } else if (webView.canGoBack() && searchView.getVisibility() == View.VISIBLE) {
            searchView.setVisibility(View.GONE);
            searchView.setIconified(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putString("url", webView.getUrl());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));

        suggestions = new SearchRecentSuggestions(MainActivity.this, RecentSuggestionsProvider.AUTHORITY, RecentSuggestionsProvider.MODE);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query.startsWith("www") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".com") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".gov") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".net") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".org") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".ly") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);
                } if (query.endsWith(".gl") || URLUtil.isValidUrl(query)) {
                    if (!URLUtil.isValidUrl(query)) query = URLUtil.guessUrl(query);
                    webView.loadUrl(query);

                } else
                    webView.loadUrl(getSearchPrefix() + query);

                if (!isIncognito) suggestions.saveRecentQuery(query, null);

                searchView.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = searchView.getSuggestionsAdapter().getCursor();
                cursor.moveToPosition(position);
                String query = cursor.getString(2);

                searchView.setQuery(query, false);

                if (URLUtil.isValidUrl(query))
                    webView.loadUrl(query);
                else
                    webView.loadUrl(getSearchPrefix() + query);

                searchView.setIconified(false);
                searchView.setVisibility(View.GONE);

                return false;
            }
        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                searchView.setIconified(false);
                searchView.setVisibility(View.GONE);
                return false;
            }
        });

        searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (prefs.getBoolean("suggestions", true))
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        searchView.setBackground(new ColorDrawable(Color.TRANSPARENT));
        searchView.setVisibility(View.GONE);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                searchView.setVisibility(View.VISIBLE);
                searchView.setIconified(false);
                break;
            case R.id.action_overflow:
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, toolbar) {
                    @Override
                    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_home:
                                webView.clearHistory();
                                webView.loadUrl(getHomepage());
                                break;
                            case R.id.action_share:
                                String shareBody = webView.getUrl();
                                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                                sharingIntent.setType("text/plain");
                                sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Website Link");
                                sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                                startActivity(Intent.createChooser(sharingIntent, "Share link"));
                                break;
                            case R.id.action_refresh:
                                webView.reload();
                                break;
                            case R.id.action_search_words:
                                webView.showFindDialog(null, true);
                                break;
                            case R.id.action_dekstop:
                                if(item.isChecked()){
                                    item.setChecked(false);
                                }else{
                                    item.setChecked(true);
                                }
                                if(desktop){
                                    webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.41 Safari/537.36");
                                    webView.reload();
                                    desktop=false;
                                }else {
                                    webView.getSettings().setUserAgentString("");
                                    webView.reload();
                                    desktop = true;
                                }
                                break;
                            case R.id.action_incognito:
                                isIncognito = !isIncognito;
                                item.setChecked(isIncognito);

                                WebSettings webSettings = webView.getSettings();
                                CookieManager.getInstance().setAcceptCookie(!isIncognito);
                                webSettings.setAppCacheEnabled(!isIncognito);
                                webView.clearHistory();
                                webView.clearCache(isIncognito);
                                webView.clearFormData();
                                webView.getSettings().setSavePassword(!isIncognito);
                                webView.getSettings().setSaveFormData(!isIncognito);
                                webView.isPrivateBrowsingEnabled();

                                setColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimary));
                                break;
                            case R.id.action_add:
                                createShortCut();
                                Toast.makeText(MainActivity.this, "Shortcut added to home!", Toast.LENGTH_SHORT).show();
                                break;
                            case R.id.action_perms:
                                launchPerms();
                                break;
                            case R.id.action_settings:
                                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                                break;
                        }
                        return super.onMenuItemSelected(menu, item);
                    }
                };

                popupMenu.inflate(R.menu.menu_overflow);

                popupMenu.getMenu().findItem(R.id.action_perms).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);

                try {
                    Field menuField = PopupMenu.class.getDeclaredField("mMenu");
                    menuField.setAccessible(true);

                    Field menuAnchor = PopupMenu.class.getDeclaredField("mAnchor");
                    menuAnchor.setAccessible(true);

                    IconMenuPopupHelper popupHelper = new IconMenuPopupHelper(this, (MenuBuilder) menuField.get(popupMenu), (View) menuAnchor.get(popupMenu), false, android.support.v7.appcompat.R.attr.popupMenuStyle, 0);

                    Field field = PopupMenu.class.getDeclaredField("mPopup");
                    field.setAccessible(true);
                    field.set(popupMenu, popupHelper);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                popupMenu.setGravity(Gravity.TOP | Gravity.END);
                popupMenu.show();

                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        } else if (locationManager != null)
            locationManager.removeUpdates(locationListener);

        unregisterReceiver(networkChangeReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        } else if (locationManager != null)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1000, locationListener);

        networkChangeReceiver = new NetworkChangeReceiver();
        registerReceiver(networkChangeReceiver, new IntentFilter());

        if (prefs != null) {
            setPrefs();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        final WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(prefs.getBoolean("javascript", true));
        webSettings.setGeolocationEnabled(prefs.getBoolean("location_services", true));
        webSettings.setSupportZoom(prefs.getBoolean("zooming", false));
    }

    /** Method to get homepage from prefs */
    private String getHomepage() {
        String homepage = prefs.getString("homepage", "");
        if (homepage.length() > 0) {
            return homepage;
        } else {
            switch (Integer.parseInt(prefs.getString("search_engine", "0"))) {
                case SEARCH_GOOGLE:
                    return "https://google.com";
                case SEARCH_YAHOO:
                    return "https://www.yahoo.com/";
                case SEARCH_DUCKDUCKGO:
                    return "https://duckduckgo.com/";
                case SEARCH_BING:
                    return "https://www.bing.com/";
                default:
                    return "https://www.google.com/";
            }
        }
    }

    /** Method to get prefix for search engine */
    private String getSearchPrefix() {
        switch (Integer.parseInt(prefs.getString("search_engine", "0"))) {
            case SEARCH_GOOGLE:
                return "https://www.google.com/search?q=";
            case SEARCH_YAHOO:
                return "https://search.yahoo.com/search?p=";
            case SEARCH_DUCKDUCKGO:
                return "https://duckduckgo.com/?q=";
            case SEARCH_BING:
                return "https://www.bing.com/search?q=";
            default:
                return "https://www.google.com/search?q=";
        }
    }

    /** Perms activity for set them if not in the first time */
    private void launchPerms() {
        new PermissionsActivity(getBaseContext())
                .withAppName(getResources().getString(R.string.app_name))
                .withPermissions(new Permission(Permissions.WRITE_EXTERNAL_STORAGE, "To download files, Colombo must have access to your storage!"), new Permission(Permissions.ACCESS_FINE_LOCATION, "If you want to use WebApps with geolocation Colombo must have access to your position!"))
                .withPermissionFlowCallback(new PermissionsActivity.PermissionFlowCallback() {
                    @Override
                    public void onPermissionGranted(Permission permission) {
                        Toast.makeText(MainActivity.this, "The permissions are set!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionDenied(Permission permission) {
                        Toast.makeText(MainActivity.this, "You won't be able to download files!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setBackgroundColor(Color.parseColor("#03A9F4"))
                .setBarColor(Color.parseColor("#0288D1"))
                .setStatusBarColor(Color.parseColor("#0288D1"))
                .launch();
    }

    /** Set color class to change dinamically color of UI */
    private void setColor(int color) {
        color = isIncognito ? ContextCompat.getColor(this, R.color.colorPrimaryIncognito) : color;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), getWindow().getStatusBarColor(), StaticUtils.darkColor(color));
            colorAnimation.setDuration(150);
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        getWindow().setStatusBarColor((int) animator.getAnimatedValue());
                        if (prefs.getBoolean("navbar_tint", false))
                            getWindow().setNavigationBarColor((int) animator.getAnimatedValue());
                    }
                }
            });
            colorAnimation.start();

            setTaskDescription(new ActivityManager.TaskDescription(webView.getTitle(), webView.getFavicon(), color));
        }

        int colorFrom = ContextCompat.getColor(this, !isIncognito ? R.color.colorPrimaryIncognito : R.color.colorPrimary);
        Drawable backgroundFrom = appbar.getBackground();
        if (backgroundFrom instanceof ColorDrawable)
            colorFrom = ((ColorDrawable) backgroundFrom).getColor();

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, color);
        colorAnimation.setDuration(150);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                appbar.setBackgroundColor((int) animator.getAnimatedValue());
                swipeRefreshLayout.setColorSchemeColors((int) animator.getAnimatedValue());
            }
        });
        colorAnimation.start();

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, isIncognito ? R.color.swipeRefreshIncognito : R.color.swipeRefresh));
    }

    /** Method to set stuff when returning from settings */
    private void setPrefs() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(prefs.getBoolean("javascript", true));
        webSettings.setGeolocationEnabled(prefs.getBoolean("location_services", true));
        webSettings.setSupportZoom(prefs.getBoolean("zooming", false));
    }

    /** Get user agent method from Android AOSP code */
    public static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }

    /** Method to create shortcuts to home */
    private void createShortCut(){
        Intent shortcutintent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        Intent data = new Intent(getApplicationContext(), MainActivity.class);
        data.setData(Uri.parse(webView.getUrl()));
        shortcutintent.putExtra("duplicate", false);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_NAME, webView.getTitle());
        Parcelable icon = Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.mipmap.ic_launcher);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);
        shortcutintent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, data);
        sendBroadcast(shortcutintent);
        finish();
    }

    public static class NetworkChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo netInfo = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

            if (netInfo == null || !netInfo.isConnected())
                Toast.makeText(context, R.string.msg_connection_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
