package app.cadence;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.playlist.*;

public final class YouTubeRepository implements PlaylistSource, MusicCatalog {
    public static final String USER_AGENT="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    public final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(70,TimeUnit.SECONDS).build();
    private final Map<String,Cached> cache=new HashMap<>();
    private record Cached(AudioSource audio,long expires){}
    public YouTubeRepository(){
        NewPipe.init(new Downloader(){
            @Override public org.schabi.newpipe.extractor.downloader.Response execute(org.schabi.newpipe.extractor.downloader.Request request) throws IOException,ReCaptchaException {
                okhttp3.Request.Builder b=new okhttp3.Request.Builder().url(request.url()).header("User-Agent",USER_AGENT);
                request.headers().forEach((k,values)->{b.removeHeader(k);for(String value:values)b.addHeader(k,value);});
                byte[] data=request.dataToSend();RequestBody body=data==null?null:RequestBody.create(data,null);
                if(body==null&&(request.httpMethod().equals("POST")||request.httpMethod().equals("PUT")))body=RequestBody.create(new byte[0],null);
                b.method(request.httpMethod(),body);
                try(okhttp3.Response r=client.newCall(b.build()).execute()){
                    if(r.code()==429)throw new ReCaptchaException("YouTube temporarily limited requests. Try again later.",request.url());
                    return new org.schabi.newpipe.extractor.downloader.Response(r.code(),r.message(),r.headers().toMultimap(),r.body()==null?"":r.body().string(),r.request().url().toString());
                }
            }
        });
    }
    @Override public MusicCatalog.Result search(String query,Page next,MusicCatalog.Mode mode) throws Exception {
        String id=VideoIds.parse(query);
        if(id!=null){StreamInfo i=StreamInfo.getInfo(ServiceList.YouTube,VideoIds.url(id));return new MusicCatalog.Result(List.of(from(i)),List.of(),null,query);}
        var q=ServiceList.YouTube.getSearchQHFactory().fromQuery(query,List.of(mode.filter),"");
        List<InfoItem> items;Page more;
        if(next==null){SearchInfo info=SearchInfo.getInfo(ServiceList.YouTube,q);items=info.getRelatedItems();more=info.getNextPage();}
        else {var page=SearchInfo.getMoreItems(ServiceList.YouTube,q,next);items=page.getItems();more=page.getNextPage();}
        List<Track> out=new ArrayList<>();List<Suggestion> albums=new ArrayList<>();
        for(InfoItem item:items)if(item instanceof StreamInfoItem s && s.getStreamType()!=StreamType.LIVE_STREAM){
            String video=VideoIds.parse(s.getUrl());if(video==null)continue;
            out.add(new Track(video,s.getName(),s.getUploaderName()==null?"Unknown artist":s.getUploaderName(),s.getThumbnails().isEmpty()?"":s.getThumbnails().get(0).getUrl(),s.getDuration()));
        }
        if(mode==MusicCatalog.Mode.ALBUMS)for(InfoItem item:items)if(item instanceof PlaylistInfoItem p){
            String url=PlaylistLinks.canonical(p.getUrl());if(url!=null)albums.add(new Suggestion(url,p.getName(),p.getUploaderName()==null?"Unknown artist":p.getUploaderName(),p.getThumbnails().isEmpty()?"":p.getThumbnails().get(0).getUrl(),p.getStreamCount()));
        }
        return new MusicCatalog.Result(out,albums,Page.isValid(more)?more:null,query);
    }
    @Override public List<Suggestion> suggest(String query) throws Exception {
        var search=ServiceList.YouTube.getSearchQHFactory().fromQuery(query,List.of("music_playlists"),"");
        SearchInfo info=SearchInfo.getInfo(ServiceList.YouTube,search);
        LinkedHashMap<String,Suggestion> found=new LinkedHashMap<>();
        for(InfoItem item:info.getRelatedItems())if(item instanceof PlaylistInfoItem p){
            String url=PlaylistLinks.canonical(p.getUrl());if(url==null)continue;
            found.putIfAbsent(url,new Suggestion(url,p.getName(),p.getUploaderName()==null?"YouTube":p.getUploaderName(),p.getThumbnails().isEmpty()?"":p.getThumbnails().get(0).getUrl(),p.getStreamCount()));
            if(found.size()==12)break;
        }
        return new ArrayList<>(found.values());
    }
    @Override public Tracks playlist(String value,Page next) throws Exception {
        String url=PlaylistLinks.canonical(value);if(url==null)throw new IllegalArgumentException("Use a valid YouTube playlist link.");
        List<StreamInfoItem> items;Page more;String title="";
        if(next==null){PlaylistInfo info=PlaylistInfo.getInfo(ServiceList.YouTube,url);title=info.getName();items=info.getRelatedItems();more=info.getNextPage();}
        else{var page=PlaylistInfo.getMoreItems(ServiceList.YouTube,url,next);items=page.getItems();more=page.getNextPage();}
        LinkedHashMap<String,Track> tracks=new LinkedHashMap<>();
        for(StreamInfoItem s:items){
            String id=VideoIds.parse(s.getUrl());if(id==null||s.getStreamType()==StreamType.LIVE_STREAM)continue;
            tracks.putIfAbsent(id,new Track(id,s.getName(),s.getUploaderName()==null?"Unknown artist":s.getUploaderName(),s.getThumbnails().isEmpty()?"":s.getThumbnails().get(0).getUrl(),s.getDuration()));
        }
        return new Tracks(title,new ArrayList<>(tracks.values()),Page.isValid(more)?more:null);
    }
    public synchronized String resolve(String id) throws IOException {return audio(id,false).url();}
    public synchronized AudioSource audio(String id,boolean refresh) throws IOException {
        if(refresh)cache.remove(id);
        Cached found=cache.get(id);if(found!=null&&found.expires()>System.currentTimeMillis())return found.audio();
        try{
            StreamInfo info=StreamInfo.getInfo(ServiceList.YouTube,VideoIds.url(id));
            AudioStream chosen=info.getAudioStreams().stream().filter(s->s.isUrl()&&s.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP)
                .max(Comparator.comparingInt(AudioStream::getAverageBitrate)).orElseThrow(()->new IOException("No playable audio stream is available for this video."));
            String url=chosen.getContent();if(!url.startsWith("https://"))throw new IOException("The audio source did not provide a secure stream.");
            long length=0;String raw=java.net.URI.create(url).getRawQuery();
            if(raw!=null)for(String param:raw.split("&"))if(param.startsWith("clen="))try{length=Long.parseLong(param.substring(5));}catch(NumberFormatException ignored){}
            String mime=chosen.getFormat()==null?"application/octet-stream":chosen.getFormat().getMimeType();
            AudioSource audio=new AudioSource(url,mime,chosen.getId(),Math.max(0,length));
            cache.put(id,new Cached(audio,System.currentTimeMillis()+15*60*1000));return audio;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("YouTube could not provide audio. The video may be restricted, or the extractor may need an update.",e);}
    }
    public synchronized void invalidate(String id){cache.remove(id);}
    private Track from(StreamInfo s){return new Track(VideoIds.parse(s.getUrl()),s.getName(),s.getUploaderName()==null?"Unknown artist":s.getUploaderName(),s.getThumbnails().isEmpty()?"":s.getThumbnails().get(0).getUrl(),s.getDuration());}
}
