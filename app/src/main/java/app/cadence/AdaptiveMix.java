package app.cadence;

import androidx.media3.common.*;
import java.util.*;

/** A queue snapshot prevents an asynchronous refresh from replacing the user's later edits. */
final class AdaptiveMix {
    private final List<MediaItem> snapshot;private final int index;final Recommendations.Mood mood;
    private AdaptiveMix(List<MediaItem> snapshot,int index,Recommendations.Mood mood){this.snapshot=snapshot;this.index=index;this.mood=mood;}
    static AdaptiveMix capture(Player player){
        MediaItem item=player.getCurrentMediaItem();if(item==null||item.mediaMetadata.extras==null||player.getShuffleModeEnabled())return null;
        String mode=item.mediaMetadata.extras.getString("musedock.autoMix");if(mode==null)return null;
        Recommendations.Mood mood;try{mood=Recommendations.Mood.valueOf(mode);}catch(IllegalArgumentException e){return null;}if(mood==Recommendations.Mood.REPEAT)return null;
        List<MediaItem> items=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)items.add(player.getMediaItemAt(i));return new AdaptiveMix(items,player.getCurrentMediaItemIndex(),mood);
    }
    boolean apply(Player player,List<Track> candidates,Library library){return apply(player,candidates,Recommendations.profile(library,MusicLanguage.ANY,0));}
    boolean apply(Player player,List<Track> candidates,Recommendations.Profile profile){
        if(player.getShuffleModeEnabled()||player.getCurrentMediaItemIndex()!=index||player.getMediaItemCount()!=snapshot.size())return false;
        for(int i=0;i<snapshot.size();i++)if(!snapshot.get(i).equals(player.getMediaItemAt(i)))return false;
        Set<String> used=new HashSet<>();for(int i=0;i<=index;i++)used.add(snapshot.get(i).mediaId);
        List<MediaItem> next=new ArrayList<>();for(Track t:candidates)if(!Recommendations.blocked(profile,t.id())&&used.add(t.id())){next.add(PlaybackService.autoMixItem(t,null,mood.name()));}
        if(next.isEmpty())return false;player.replaceMediaItems(index+1,player.getMediaItemCount(),next);return true;
    }
}
