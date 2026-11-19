package app.cadence;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class PlaylistLinks {
    private PlaylistLinks() {}
    public static boolean isMusicLink(String value) {
        try {
            URI uri=URI.create(value.trim());
            return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                &&"music.youtube.com".equalsIgnoreCase(uri.getHost());
        }catch(RuntimeException ignored){
            return false;
        }
    }
    /** Drop tracking parameters and reject destinations outside YouTube. */
    public static String canonical(String value) {
        try {
            URI uri=URI.create(value.trim());
            if(!Set.of("https","http").contains(uri.getScheme()))return null;
            if(uri.getHost()==null||!Set.of("youtube.com","www.youtube.com","music.youtube.com","m.youtube.com").contains(uri.getHost().toLowerCase(java.util.Locale.ROOT)))return null;
            if(!Set.of("/playlist","/watch").contains(uri.getPath())||uri.getRawQuery()==null)return null;
            for(String field:uri.getRawQuery().split("&")){
                String[] pair=field.split("=",2);
                if(pair.length==2&&pair[0].equals("list")){
                    String id=URLDecoder.decode(pair[1],StandardCharsets.UTF_8.name());
                    return id.matches("[A-Za-z0-9_-]{2,160}")?"https://www.youtube.com/playlist?list="+id:null;
                }
            }
        }catch(java.io.UnsupportedEncodingException | RuntimeException ignored){/* Not a supported playlist link. */}
        return null;
    }
}
