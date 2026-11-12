package app.cadence;

import java.util.List;
import org.schabi.newpipe.extractor.Page;

/** Music-specific catalog searches; albums resolve to public track playlists. */
public interface MusicCatalog {
    enum Mode {
        SONGS("music_songs"), ALBUMS("music_albums"), VIDEOS("videos");
        public final String filter;
        Mode(String filter) { this.filter = filter; }
    }
    record Result(List<Track> tracks, List<PlaylistSource.Suggestion> albums, Page next, String query) {}
    default List<Track> related(Track seed) throws Exception {return List.of();}
    Result search(String query, Page next, Mode mode) throws Exception;
}
