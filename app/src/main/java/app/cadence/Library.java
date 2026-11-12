package app.cadence;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.File;
import java.util.*;

public final class Library extends SQLiteOpenHelper {
    public record Playlist(String id, String name, int count, String description, long updated, boolean pinned) {}
    public record Download(Track track, String status, String path, String error) {}
    private final File audioDirectory;
    public Library(Context context) { super(context, "cadence.db", null, 3); audioDirectory=new File(context.getFilesDir(),"audio"); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,title TEXT NOT NULL,artist TEXT NOT NULL,artwork TEXT NOT NULL,duration INTEGER NOT NULL,played INTEGER NOT NULL DEFAULT 0,play_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL DEFAULT 0,pinned INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE members(playlist TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,track TEXT NOT NULL REFERENCES tracks(id),position INTEGER NOT NULL,PRIMARY KEY(playlist,track))");
        db.execSQL("CREATE TABLE downloads(track TEXT PRIMARY KEY REFERENCES tracks(id),status TEXT NOT NULL,path TEXT,error TEXT NOT NULL DEFAULT '')");
        db.execSQL("INSERT INTO playlists(id,name) VALUES('liked','Liked songs')");createDiscoveryTables(db);createFeedbackTable(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2){db.execSQL("ALTER TABLE tracks ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0");db.execSQL("UPDATE tracks SET play_count=1 WHERE played>0");db.execSQL("ALTER TABLE playlists ADD COLUMN description TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE playlists ADD COLUMN updated INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE playlists ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");createDiscoveryTables(db);}
        if(oldVersion<3)createFeedbackTable(db);
    }
    private void createDiscoveryTables(SQLiteDatabase db){db.execSQL("CREATE TABLE searches(query TEXT PRIMARY KEY COLLATE NOCASE,searched INTEGER NOT NULL,count INTEGER NOT NULL DEFAULT 1)");db.execSQL("CREATE TABLE hidden_recommendations(track TEXT PRIMARY KEY)");db.execSQL("CREATE TABLE discovery_cache(key TEXT PRIMARY KEY,fingerprint TEXT NOT NULL,saved INTEGER NOT NULL,payload TEXT NOT NULL)");}
    private void createFeedbackTable(SQLiteDatabase db){db.execSQL("CREATE TABLE listening_feedback(track TEXT PRIMARY KEY REFERENCES tracks(id),skips INTEGER NOT NULL DEFAULT 0,qualified INTEGER NOT NULL DEFAULT 0,last_skip INTEGER NOT NULL DEFAULT 0)");}
    public record Feedback(String id,int skips,int qualified,long lastSkip){}
    public synchronized List<Feedback> feedback(){List<Feedback> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT track,skips,qualified,last_skip FROM listening_feedback",null)){while(c.moveToNext())out.add(new Feedback(c.getString(0),c.getInt(1),c.getInt(2),c.getLong(3)));}return out;}
    public synchronized void listening(Track track,boolean skipped){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{remember(track);db.execSQL("INSERT OR IGNORE INTO listening_feedback(track) VALUES(?)",new Object[]{track.id()});if(skipped)db.execSQL("UPDATE listening_feedback SET skips=skips+1,last_skip=? WHERE track=?",new Object[]{System.currentTimeMillis(),track.id()});else{played(track);db.execSQL("UPDATE listening_feedback SET qualified=qualified+1,last_skip=0 WHERE track=?",new Object[]{track.id()});}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public record Search(String query,long searched,int count){}
    public record Taste(Track track,int plays,long lastPlayed,boolean liked){}
    public synchronized void recordSearch(String value){
        String query=value.trim().replaceAll("\\s+"," ");if(query.isEmpty()||VideoIds.parse(query)!=null||PlaylistLinks.canonical(query)!=null)return;if(query.length()>120)query=query.substring(0,120);long now=System.currentTimeMillis();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{int count=1;try(Cursor c=db.rawQuery("SELECT searched,count FROM searches WHERE query=?",new String[]{query})){if(c.moveToFirst())count=c.getInt(1)+(now-c.getLong(0)>60000?1:0);}ContentValues v=new ContentValues();v.put("query",query);v.put("searched",now);v.put("count",count);db.insertWithOnConflict("searches",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.execSQL("DELETE FROM searches WHERE query NOT IN (SELECT query FROM searches ORDER BY searched DESC LIMIT 32)");db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized List<Search> searches(){List<Search> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT query,searched,count FROM searches ORDER BY searched DESC LIMIT 12",null)){while(c.moveToNext())out.add(new Search(c.getString(0),c.getLong(1),c.getInt(2)));}return out;}
    public synchronized List<Taste> taste(){List<Taste> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.title,t.artist,t.artwork,t.duration,t.play_count,t.played,EXISTS(SELECT 1 FROM members m WHERE m.playlist='liked' AND m.track=t.id) FROM tracks t WHERE t.played>0 OR EXISTS(SELECT 1 FROM members m WHERE m.playlist='liked' AND m.track=t.id) ORDER BY t.played DESC LIMIT 100",null)){while(c.moveToNext())out.add(new Taste(read(c),c.getInt(5),c.getLong(6),c.getInt(7)>0));}return out;}
    public synchronized Set<String> hidden(){Set<String> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT track FROM hidden_recommendations",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    public synchronized void hideSuggestion(String id){if(!VideoIds.valid(id))throw new IllegalArgumentException("Invalid song");getWritableDatabase().execSQL("INSERT OR IGNORE INTO hidden_recommendations VALUES(?)",new Object[]{id});getWritableDatabase().delete("discovery_cache",null,null);}
    public synchronized void clearDiscoveryHistory(){getWritableDatabase().delete("searches",null,null);getWritableDatabase().delete("hidden_recommendations",null,null);getWritableDatabase().delete("discovery_cache",null,null);}
    public synchronized String cachedDiscovery(String key,String fingerprint){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM discovery_cache WHERE key=? AND fingerprint=? AND saved>?",new String[]{key,fingerprint,Long.toString(System.currentTimeMillis()-6*60*60*1000L)})){return c.moveToFirst()?c.getString(0):null;}}
    public synchronized void cacheDiscovery(String key,String fingerprint,String payload){ContentValues v=new ContentValues();v.put("key",key);v.put("fingerprint",fingerprint);v.put("saved",System.currentTimeMillis());v.put("payload",payload);getWritableDatabase().insertWithOnConflict("discovery_cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);getWritableDatabase().execSQL("DELETE FROM discovery_cache WHERE key NOT IN (SELECT key FROM discovery_cache ORDER BY saved DESC LIMIT 12)");}

    public synchronized void remember(Track t) {
        ContentValues v = new ContentValues(); v.put("id",t.id()); v.put("title",t.title()); v.put("artist",t.artist()); v.put("artwork",t.artwork()); v.put("duration",t.duration());
        SQLiteDatabase db = getWritableDatabase();
        if (db.update("tracks",v,"id=?",new String[]{t.id()}) == 0) db.insertOrThrow("tracks",null,v);
    }
    public synchronized Track track(String id) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id,title,artist,artwork,duration FROM tracks WHERE id=?",new String[]{id})) { return c.moveToFirst()?read(c):null; }
    }
    private Track read(Cursor c) { return new Track(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)); }
    public synchronized List<Track> recent() { return tracks("SELECT id,title,artist,artwork,duration FROM tracks WHERE played>0 ORDER BY played DESC LIMIT 30",new String[]{}); }
    public synchronized void played(Track t) { remember(t); ContentValues v=new ContentValues();v.put("played",System.currentTimeMillis());getWritableDatabase().update("tracks",v,"id=?",new String[]{t.id()});getWritableDatabase().execSQL("UPDATE tracks SET play_count=play_count+1 WHERE id=?",new Object[]{t.id()}); }
    public synchronized List<Track> tracks(String sql,String[] args) {
        List<Track> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){ while(c.moveToNext())out.add(read(c)); }return out;
    }
    public synchronized List<Playlist> playlists() {
        List<Playlist> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id,p.name,COUNT(m.track),p.description,p.updated,p.pinned FROM playlists p LEFT JOIN members m ON p.id=m.playlist GROUP BY p.id ORDER BY CASE WHEN p.id='liked' THEN 0 ELSE 1 END,p.pinned DESC,p.updated DESC,p.name COLLATE NOCASE",null)) {
            while(c.moveToNext())out.add(new Playlist(c.getString(0),c.getString(1),c.getInt(2),c.getString(3),c.getLong(4),c.getInt(5)>0));
        } return out;
    }
    public synchronized String createPlaylist(String name) {
        if(name.trim().isEmpty() || name.trim().length()>80)throw new IllegalArgumentException("Use a name between 1 and 80 characters");
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("name",name.trim());v.put("updated",System.currentTimeMillis());getWritableDatabase().insertOrThrow("playlists",null,v);return id;
    }
    public synchronized String importPlaylist(String name,List<Track> tracks) {
        if(tracks.isEmpty())throw new IllegalArgumentException("Load some songs before saving this playlist.");
        String title=name==null||name.trim().isEmpty()?"YouTube playlist":name.trim();
        if(title.length()>80)title=title.substring(0,80);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            String id=createPlaylist(title);
            for(Track track:tracks)add(id,track);
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public synchronized String importHistory(List<HistoryImport.Entry> entries){
        if(entries.isEmpty())throw new IllegalArgumentException("Choose at least one song.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            List<Track> tracks=new ArrayList<>();
            for(HistoryImport.Entry entry:entries){Track existing=track(entry.track().id());tracks.add(existing==null?entry.track():existing);}
            String id=importPlaylist("YouTube listening history",tracks);
            for(HistoryImport.Entry entry:entries)if(entry.playedAt()>0)db.execSQL("UPDATE tracks SET played=MAX(played,?) WHERE id=?",new Object[]{entry.playedAt(),entry.track().id()});
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public synchronized void rename(String id,String name) {
        if(id.equals("liked"))return;
        if(name.trim().isEmpty()||name.trim().length()>80)throw new IllegalArgumentException("Use a name between 1 and 80 characters");
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("updated",System.currentTimeMillis());getWritableDatabase().update("playlists",v,"id=?",new String[]{id});
    }
    public synchronized void deletePlaylist(String id) { if(!id.equals("liked"))getWritableDatabase().delete("playlists","id=?",new String[]{id}); }
    public synchronized List<Track> playlistTracks(String id) { return tracks("SELECT t.id,t.title,t.artist,t.artwork,t.duration FROM members m JOIN tracks t ON t.id=m.track WHERE m.playlist=? ORDER BY m.position,m.rowid",new String[]{id}); }
    public synchronized boolean contains(String playlist,String track) { try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM members WHERE playlist=? AND track=?",new String[]{playlist,track})){return c.moveToFirst();} }
    public synchronized void add(String playlist,Track t) {
        remember(t); SQLiteDatabase db=getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO members(playlist,track,position) SELECT ?,?,COALESCE(MAX(position),-1)+1 FROM members WHERE playlist=?",new Object[]{playlist,t.id(),playlist});touch(playlist);
    }
    public synchronized void remove(String playlist,String track){ getWritableDatabase().delete("members","playlist=? AND track=?",new String[]{playlist,track});touch(playlist); }
    public synchronized void move(String playlist,String id,int offset) {
        List<Track> list=playlistTracks(playlist);int from=-1;for(int i=0;i<list.size();i++)if(list.get(i).id().equals(id))from=i;
        if(from<0||from+offset<0||from+offset>=list.size())return;
        Collections.swap(list,from,from+offset);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(int i=0;i<list.size();i++)db.execSQL("UPDATE members SET position=? WHERE playlist=? AND track=?",new Object[]{i,playlist,list.get(i).id()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void touch(String id){getWritableDatabase().execSQL("UPDATE playlists SET updated=? WHERE id=?",new Object[]{System.currentTimeMillis(),id});}
    public synchronized void details(String id,String name,String description){if(description==null||description.length()>280)throw new IllegalArgumentException("Use a description under 281 characters");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{rename(id,name);ContentValues v=new ContentValues();v.put("description",description.trim());db.update("playlists",v,"id=?",new String[]{id});touch(id);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void pin(String id,boolean pinned){getWritableDatabase().execSQL("UPDATE playlists SET pinned=?,updated=? WHERE id=?",new Object[]{pinned?1:0,System.currentTimeMillis(),id});}
    public synchronized String duplicate(String id,String name){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{String target=createPlaylist(name);for(Track t:playlistTracks(id))add(target,t);db.setTransactionSuccessful();return target;}finally{db.endTransaction();}}
    public synchronized void addAll(String id,List<Track> songs){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(Track t:songs)add(id,t);touch(id);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void removeAll(String id,Collection<String> songs){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String song:songs)remove(id,song);touch(id);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void sortPlaylist(String id,String order){List<Track> songs=playlistTracks(id);switch(order){case "title"->songs.sort(Comparator.comparing(Track::title,String.CASE_INSENSITIVE_ORDER));case "artist"->songs.sort(Comparator.comparing(Track::artist,String.CASE_INSENSITIVE_ORDER).thenComparing(Track::title));case "reverse"->Collections.reverse(songs);default->throw new IllegalArgumentException("Unknown order");}SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<songs.size();i++)db.execSQL("UPDATE members SET position=? WHERE playlist=? AND track=?",new Object[]{i,id,songs.get(i).id()});touch(id);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void download(Track t,String status,String path,String error){
        remember(t); ContentValues v=new ContentValues();v.put("track",t.id());v.put("status",status);v.put("path",path);v.put("error",error==null?"":error);
        getWritableDatabase().insertWithOnConflict("downloads",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized List<Download> downloads(){
        List<Download> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.title,t.artist,t.artwork,t.duration,d.status,d.path,d.error FROM downloads d JOIN tracks t ON t.id=d.track ORDER BY d.rowid DESC",null)){
            while(c.moveToNext())out.add(new Download(read(c),c.getString(5),c.getString(6),c.getString(7)));
        }return out;
    }
    public synchronized String offline(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT path FROM downloads WHERE track=? AND status IN ('ready','converting')",new String[]{id})){
            if(c.moveToFirst()&&c.getString(0)!=null){File f=new File(c.getString(0));if(f.isFile()&&f.length()>0)return f.getAbsolutePath();}
        }return null;
    }
    public synchronized boolean removeDownload(String id){
        String path=offline(id); if(path!=null){File f=new File(path);if(f.exists()&&!f.delete())return false;}
        if(VideoIds.valid(id))for(String suffix:new String[]{".source",".source.download",".source.download.meta",".mp3.part",".audio",".m4a",".webm",".mp3",".ogg",".wav",".aac"}){
            File fragment=new File(audioDirectory,id+suffix);if(fragment.exists()&&!fragment.delete())return false;
        }
        getWritableDatabase().delete("downloads","track=?",new String[]{id});return true;
    }
    public synchronized void recoverDownloads(){getWritableDatabase().execSQL("UPDATE downloads SET status=CASE WHEN path IS NULL THEN 'failed' ELSE 'ready' END,error='MP3 conversion interrupted. Original audio is kept when available.' WHERE status='converting'");getWritableDatabase().execSQL("UPDATE downloads SET status='failed',error='Download interrupted. Tap Retry to resume with a fresh connection.' WHERE status IN ('queued','downloading')");}
}
