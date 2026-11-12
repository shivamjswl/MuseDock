package app.cadence;

import java.net.URI;
import java.util.*;
import org.schabi.newpipe.extractor.Image;

/** Upgrade only recognized image CDNs; retain the supplied URL as a fallback. */
public final class Artwork {
    private Artwork() {}
    public static String largest(List<Image> images) {
        return images.stream().max(Comparator.comparingLong(i -> (long)Math.max(0,i.getWidth())*Math.max(0,i.getHeight())))
            .map(Image::getUrl).orElse("");
    }
    public static List<String> candidates(String url,int pixels) {
        if(url==null||url.isBlank())return List.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        try {
            URI uri=URI.create(url);String host=uri.getHost(),path=uri.getPath();
            if(!"https".equals(uri.getScheme())||host==null)return List.of();
            int size=pixels<=256?320:pixels<=640?640:1280;
            if(host.equals("i.ytimg.com")&&path.matches("/vi(?:_webp)?/[A-Za-z0-9_-]{11}/[^/]+")) {
                String base="https://i.ytimg.com/vi/"+path.split("/")[2]+"/";
                if(size>640)result.add(base+"maxresdefault.jpg");
                result.add(base+"sddefault.jpg");result.add(base+"hqdefault.jpg");
            } else if(host.equals("lh3.googleusercontent.com")||host.equals("yt3.googleusercontent.com")||host.equals("yt3.ggpht.com")) {
                // Known Google cover-size suffix, without touching signed query parameters.
                if(uri.getRawQuery()==null) {
                    String upgraded=url.replaceAll("=w\\d+-h\\d+[^?]*$","=w"+size+"-h"+size+"-l90-rj")
                        .replaceAll("=s\\d+[^?]*$","=s"+size+"-c-k-c0x00ffffff-no-rj");
                    result.add(upgraded);
                }
            }
            result.add(url);
        }catch(IllegalArgumentException ignored){}
        return new ArrayList<>(result);
    }
    public static int sampleSize(int width,int height,int target) {
        int sample=1;while(Math.min(width,height)/(sample*2)>=target&&sample<32)sample*=2;while((Math.max(width,height)/sample>4096||(long)(width/sample)*(height/sample)>4000000)&&sample<128)sample*=2;return sample;
    }
}
