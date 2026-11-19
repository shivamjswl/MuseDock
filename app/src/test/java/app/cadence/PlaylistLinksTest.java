package app.cadence;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PlaylistLinksTest {
    @Test public void recognizesYouTubeMusicLinksOnly(){
        assertTrue(PlaylistLinks.isMusicLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(PlaylistLinks.isMusicLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(PlaylistLinks.isMusicLink("https://music.youtube.com.example.org/playlist?list=PL_test"));
    }
    @Test public void canonicalizesPlaylistsAndDropsTracking(){
        assertEquals("https://www.youtube.com/playlist?list=PL_test-123",PlaylistLinks.canonical("https://music.youtube.com/playlist?list=PL_test-123&si=tracking"));
        assertEquals("https://www.youtube.com/playlist?list=PL_test-123",PlaylistLinks.canonical("http://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL_test-123"));
    }
    @Test public void rejectsForeignHostsAndSchemes(){for(String url:new String[]{"https://youtube.com.evil.test/playlist?list=PL_test","file:///playlist?list=PL_test","https://evil.test/playlist?list=PL_test","javascript:alert(1)"})assertNull(PlaylistLinks.canonical(url));}
    @Test public void rejectsMissingOrMalformedPlaylistIds(){for(String url:new String[]{"https://youtube.com/playlist","https://youtube.com/playlist?list=","https://youtube.com/playlist?list=../../file","https://youtube.com/playlist?list=PL%26inject","https://youtube.com/shorts/PL_test?list=PL_test"})assertNull(PlaylistLinks.canonical(url));}
    @Test public void rejectsNullWithoutThrowing(){assertNull(PlaylistLinks.canonical(null));}
}
