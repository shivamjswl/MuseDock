package app.cadence;

import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={29,37},application=MuseDockApp.class)
public final class LibraryUpgradeTest {
    private MuseDockApp app;private Library library;
    private Track song(int i){return new Track(String.format(Locale.ROOT,"lib%08d",i),new String[]{"Zulu","Alpha","Middle"}[i%3],"Artist "+i,"",100);}
    @Before public void setup(){app=(MuseDockApp)RuntimeEnvironment.getApplication();library=app.library;}
    @After public void cleanup(){library.close();}
    @Test public void versionOneMigrationKeepsTracksPlaylistsOfflineAndHistory()throws Exception{
        library.close();app.deleteDatabase("cadence.db");File path=app.getDatabasePath("cadence.db");File audio=new File(app.getFilesDir(),"kept.audio");try(FileOutputStream out=new FileOutputStream(audio)){out.write(new byte[]{1,2,3});}
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(path,null)){
            old.execSQL("CREATE TABLE tracks(id TEXT PRIMARY KEY,title TEXT NOT NULL,artist TEXT NOT NULL,artwork TEXT NOT NULL,duration INTEGER NOT NULL,played INTEGER NOT NULL DEFAULT 0)");old.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL)");old.execSQL("CREATE TABLE members(playlist TEXT,track TEXT,position INTEGER,PRIMARY KEY(playlist,track))");old.execSQL("CREATE TABLE downloads(track TEXT PRIMARY KEY,status TEXT,path TEXT,error TEXT)");old.execSQL("INSERT INTO tracks VALUES('lib00000001','Kept song','Kept artist','',100,1234)");old.execSQL("INSERT INTO playlists VALUES('liked','Liked songs'),('kept','My kept playlist')");old.execSQL("INSERT INTO members VALUES('kept','lib00000001',0)");old.execSQL("INSERT INTO downloads VALUES('lib00000001','ready',?,'')",new Object[]{audio.getAbsolutePath()});old.setVersion(1);
        }
        library=new Library(app);app.library=library;assertEquals(3,library.getReadableDatabase().getVersion());assertEquals("Kept song",library.playlistTracks("kept").get(0).title());assertEquals(audio.getAbsolutePath(),library.offline("lib00000001"));assertEquals(1,library.taste().get(0).plays());library.recordSearch("Punjabi acoustic");assertEquals(1,library.searches().size());assertTrue(audio.exists());
    }
    @Test public void playlistToolsPersistOrderingDescriptionsPinsAndUniqueMerges()throws Exception{
        String id=library.createPlaylist("Mix");library.addAll(id,List.of(song(0),song(1),song(2)));library.details(id,"Updated mix","A description");library.pin(id,true);library.sortPlaylist(id,"title");assertEquals("Alpha",library.playlistTracks(id).get(0).title());String copy=library.duplicate(id,"Copy");library.addAll(copy,library.playlistTracks(id));assertEquals(3,library.playlistTracks(copy).size());File audio=new File(app.getFilesDir(),"song.audio");try(FileOutputStream out=new FileOutputStream(audio)){out.write(1);}library.download(song(0),"ready",audio.getAbsolutePath(),"");library.removeAll(id,List.of(song(0).id(),song(1).id()));assertEquals(1,library.playlistTracks(id).size());assertTrue(audio.exists());Library reopened=new Library(app);var saved=reopened.playlists().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();assertTrue(saved.pinned());assertEquals("A description",saved.description());reopened.close();
    }
    @Test public void failedBatchAdditionRollsBackPlaylistUpdates(){String id=library.createPlaylist("Mix");library.add(id,song(0));try{library.addAll(id,Arrays.asList(song(1),new Track("lib00000009",null,"Artist","",10)));fail();}catch(android.database.SQLException expected){}assertEquals(List.of(song(0)),library.playlistTracks(id));}
    @Test public void searchesAreBoundedLinksExcludedAndCacheIsTasteScoped(){library.recordSearch("jazz");library.recordSearch("JAZZ");assertEquals(1,library.searches().size());assertEquals(1,library.searches().get(0).count());library.recordSearch("https://www.youtube.com/watch?v=dQw4w9WgXcQ");assertEquals(1,library.searches().size());for(int i=0;i<40;i++)library.recordSearch("query "+i);try(var c=library.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM searches",null)){assertTrue(c.moveToFirst());assertEquals(32,c.getInt(0));}library.cacheDiscovery("FOCUS","taste-a","[]");assertEquals("[]",library.cachedDiscovery("FOCUS","taste-a"));assertNull(library.cachedDiscovery("FOCUS","taste-b"));library.hideSuggestion(song(0).id());assertTrue(library.hidden().contains(song(0).id()));assertNull(library.cachedDiscovery("FOCUS","taste-a"));}
    @Test public void feedbackPersistsWithoutDeletingPlaylistOrDownload(){Track t=song(0);library.add("liked",t);library.played(t);library.listening(t,true);assertEquals(1,library.feedback().get(0).skips());assertTrue(library.contains("liked",t.id()));assertTrue(Recommendations.blocked(Recommendations.profile(library,MusicLanguage.ANY,0),t.id()));library.listening(t,false);assertEquals(2,library.taste().get(0).plays());assertEquals(0,library.feedback().get(0).lastSkip());Library reopened=new Library(app);assertEquals(1,reopened.feedback().get(0).qualified());reopened.close();}
    @Test public void versionTwoMigrationPreservesSearchHistory(){library.recordSearch("bhangra");library.played(song(0));library.getWritableDatabase().execSQL("DROP TABLE listening_feedback");library.getWritableDatabase().setVersion(2);library.close();library=new Library(app);app.library=library;assertEquals(3,library.getReadableDatabase().getVersion());assertEquals("bhangra",library.searches().get(0).query());assertEquals(1,library.taste().get(0).plays());assertTrue(library.feedback().isEmpty());library.listening(song(0),true);assertEquals(1,library.feedback().size());}
}
