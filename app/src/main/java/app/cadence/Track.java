package app.cadence;

import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import android.net.Uri;

public record Track(String id, String title, String artist, String artwork, long duration) {
    public String artworkUrl(){return artwork==null||artwork.isBlank()?(VideoIds.valid(id)?"https://i.ytimg.com/vi/"+id+"/hqdefault.jpg":""):artwork;}
    public String url() { return VideoIds.url(id); }
    public MediaItem mediaItem(String localPath) {
        Bundle extras = new Bundle();
        extras.putString("artist", artist); extras.putString("artwork", artwork); extras.putLong("duration", duration);
        MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(title).setArtist(artist).setExtras(extras);
        if (!artwork.isEmpty()) metadata.setArtworkUri(Uri.parse(artwork));
        return new MediaItem.Builder().setMediaId(id)
            .setUri(localPath == null ? url() : Uri.fromFile(new java.io.File(localPath)).toString())
            .setMimeType("audio/mp4").setMediaMetadata(metadata.build()).build();
    }
    public static Track from(MediaItem item) {
        MediaMetadata m = item.mediaMetadata;
        return new Track(item.mediaId, String.valueOf(m.title == null ? "Untitled" : m.title),
            String.valueOf(m.artist == null ? "Unknown artist" : m.artist),
            m.artworkUri == null ? "" : m.artworkUri.toString(), m.extras == null ? 0 : m.extras.getLong("duration"));
    }
}
