package app.cadence;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.schabi.newpipe.extractor.ServiceList;
public class MusicCatalogRoutingTest {
    @Test public void everyDiscoveryModeTargetsMusicHostAndExtractor()throws Exception{assertEquals(2,MusicCatalog.Mode.values().length);for(var mode:MusicCatalog.Mode.values()){var query=ServiceList.YouTube.getSearchQHFactory().fromQuery("artist",List.of(mode.filter),"");assertEquals("music.youtube.com",java.net.URI.create(query.getUrl()).getHost());assertEquals("YoutubeMusicSearchExtractor",ServiceList.YouTube.getSearchExtractor(query).getClass().getSimpleName());}}
    @Test public void playlistSuggestionsUseTheMusicPlaylistFilter()throws Exception{var query=ServiceList.YouTube.getSearchQHFactory().fromQuery("artist mix",List.of("music_playlists"),"");assertEquals("music.youtube.com",java.net.URI.create(query.getUrl()).getHost());assertEquals("YoutubeMusicSearchExtractor",ServiceList.YouTube.getSearchExtractor(query).getClass().getSimpleName());}
    @Test public void directVideoIdCannotBypassMusicSearch()throws Exception{try{new YouTubeRepository().search("https://www.youtube.com/watch?v=dQw4w9WgXcQ",null,MusicCatalog.Mode.SONGS);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("YouTube Music"));}}
    @Test public void musicWatchLinksReachTheSharedAudioExtractor()throws Exception{assertEquals("dQw4w9WgXcQ",ServiceList.YouTube.getStreamExtractor("https://music.youtube.com/watch?v=dQw4w9WgXcQ").getId());}
}
