package app.cadence;

import android.app.Application;

public final class MuseDockApp extends Application {
    public Library library;
    public YouTubeRepository youtube;
    public PlaylistSource playlists;
    public MusicCatalog catalog;
    public okhttp3.OkHttpClient artworkClient;
    public AudioEqualizer equalizer;
    public final android.util.LruCache<String,android.graphics.Bitmap> artworkCache=new android.util.LruCache<String,android.graphics.Bitmap>(16*1024*1024){@Override protected int sizeOf(String key,android.graphics.Bitmap value){return value.getByteCount();}};
    @Override public void onCreate(){
        super.onCreate();library=new Library(this);library.recoverDownloads();
        java.io.File[] parts=new java.io.File(getFilesDir(),"audio").listFiles((dir,name)->name.endsWith(".part"));
        if(parts!=null)for(java.io.File part:parts){if(!part.delete())android.util.Log.w("MuseDock","Could not clear an interrupted download fragment");}
        youtube=new YouTubeRepository();artworkClient=youtube.client.newBuilder().cache(new okhttp3.Cache(new java.io.File(getCacheDir(),"artwork-http"),24L*1024*1024)).build();playlists=youtube;catalog=youtube;equalizer=new AudioEqualizer(this);
    }
    public static MuseDockApp of(android.content.Context c){return (MuseDockApp)c.getApplicationContext();}
}
