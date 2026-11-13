package app.cadence;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Explainable on-device taste ranking over provider results, without an account or remote profile. */
public final class Recommendations {
    public enum Mood {
        FOR_YOU("Picked for you","From your searches, likes and listening",0xff32664a,"new music discoveries"),
        FOCUS("Deep focus","Instrumental, ambient and lo-fi",0xff385c83,"instrumental ambient lofi focus music"),
        CHILL("Slow it down","Acoustic, mellow and laid-back",0xff746397,"mellow acoustic chill soul music"),
        ENERGY("Turn it up","Upbeat dance, workout and electronic",0xff9b543b,"upbeat dance workout electronic music"),
        DISCOVERY("Fresh finds","A little outside your usual rotation",0xff467a77,"new releases emerging artists music"),
        REPEAT("On repeat","The favorites you keep coming back to",0xff875c78,"");
        public final String title,description,intent;public final int color;
        Mood(String title,String description,int color,String intent){this.title=title;this.description=description;this.color=color;this.intent=intent;}
    }
    public record Profile(List<Library.Taste> taste,List<Library.Search> searches,Set<String> hidden,MusicLanguage language,long rotation,List<Library.Feedback> feedback,Track current){
        public Profile(List<Library.Taste> taste,List<Library.Search> searches,Set<String> hidden,MusicLanguage language,long rotation){this(taste,searches,hidden,language,rotation,List.of(),null);}
        public Profile withCurrent(Track track){return new Profile(taste,searches,hidden,language,rotation,feedback,track);}
        public String fingerprint(){StringBuilder value=new StringBuilder(language.name()).append(rotation);for(var t:taste)value.append(t.track().id()).append(t.track().artist()).append(t.track().title()).append(t.plays()).append(t.lastPlayed()).append(t.liked());for(var s:searches)value.append(s.query()).append(s.searched()).append(s.count());new TreeSet<>(hidden).forEach(value::append);for(var f:feedback)value.append(f);if(current!=null)value.append(current.id());try{return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    }
    public interface Songs {List<Track> search(String query)throws Exception;}
    public static Profile profile(Library library,MusicLanguage language,long rotation){return new Profile(library.taste(),library.searches(),library.hidden(),language,rotation,library.feedback(),null);}
    public static List<String> artists(Profile profile){
        Map<String,Double> weights=new LinkedHashMap<>();for(var value:profile.taste()){String artist=artist(value.track().artist());if(recentlySkipped(profile,value.track().id()))continue;if(artist.isBlank()||artist.equalsIgnoreCase("Unknown artist")||artist.equalsIgnoreCase("YouTube history"))continue;weights.merge(artist,2+Math.log1p(value.plays())*3+(value.liked()?5:0)+(value.lastPlayed()>0?3*Math.exp(-Math.max(0,System.currentTimeMillis()-value.lastPlayed())/(7d*86400000)):0),Double::sum);}
        if(profile.current()!=null&&!blocked(profile,profile.current().id()))weights.merge(artist(profile.current().artist()),18d,Double::sum);
        List<String> out=new ArrayList<>(weights.keySet());out.sort(Comparator.<String>comparingDouble(weights::get).reversed().thenComparing(String::compareTo));if(out.size()>8)out=new ArrayList<>(out.subList(0,8));if(!out.isEmpty())Collections.rotate(out,-Math.floorMod((int)profile.rotation(),out.size()));return out;
    }
    public static String artist(String value){return value==null?"":value.replaceFirst("(?i)\\s*-\\s*Topic$","").trim();}
    /** Explicit style clues only; no guessed genre labels for artists without catalog metadata. */
    public static List<String> styles(Profile profile){
        Map<String,Double> weights=new LinkedHashMap<>();
        for(var search:profile.searches())addStyles(weights,search.query(),3*Math.log1p(search.count()));
        for(var taste:profile.taste())if(!recentlySkipped(profile,taste.track().id()))addStyles(weights,taste.track().title(),1+Math.log1p(taste.plays()));
        if(profile.current()!=null&&!blocked(profile,profile.current().id()))addStyles(weights,profile.current().title(),6);
        List<String> out=new ArrayList<>(weights.keySet());out.sort(Comparator.<String>comparingDouble(weights::get).reversed());return out.subList(0,Math.min(3,out.size()));
    }
    private static void addStyles(Map<String,Double> weights,String text,double weight){
        String value=text.toLowerCase(Locale.ROOT).replace('-', ' ');
        for(String style:List.of("hip hop","rap","pop","rock","jazz","classical","acoustic","lofi","lo fi","ambient","instrumental","electronic","dance","soul","r&b","folk","indie","reggae","metal","romantic","sad","workout","devotional","bhangra","sufi"))
            if(java.util.regex.Pattern.compile("(?<![\\p{L}])"+java.util.regex.Pattern.quote(style)+"(?![\\p{L}])").matcher(value).find())weights.merge(style,weight,Double::sum);
    }
    public static boolean blocked(Profile profile,String id){return profile.hidden().contains(id)||recentlySkipped(profile,id);}
    private static boolean recentlySkipped(Profile profile,String id){for(var f:profile.feedback())if(f.id().equals(id)&&f.lastSkip()>System.currentTimeMillis()-7L*86400000)return true;return false;}
    private static double skipPenalty(Profile profile,String id){for(var f:profile.feedback())if(f.id().equals(id))return Math.min(12,Math.max(0,f.skips()-f.qualified())*3);return 0;}
    public static List<String> queries(Profile profile,Mood mood){
        LinkedHashSet<String> queries=new LinkedHashSet<>();List<String> artists=artists(profile),styles=styles(profile);String seed=artists.isEmpty()?"":artists.get(Math.floorMod(mood.ordinal()-1,artists.size()));
        if(mood==Mood.FOR_YOU){
            if(profile.current()!=null&&!blocked(profile,profile.current().id()))queries.add(artist(profile.current().artist())+" "+profile.current().title()+" similar songs");
            else if(!artists.isEmpty())queries.add(artists.get(0)+" music");
            if(!profile.searches().isEmpty())queries.add(profile.searches().get(Math.floorMod((int)profile.rotation(),Math.min(3,profile.searches().size()))).query());
            if(!styles.isEmpty())queries.add(styles.get(0)+" songs"+(artists.isEmpty()?"":" like "+artists.get(0)));
            if(artists.size()>1)queries.add(artists.get(1)+" similar artists music");queries.add("discover new music songs");
        } else if(mood!=Mood.REPEAT){queries.add(mood.intent+(seed.isEmpty()?"":" "+seed));queries.add(mood.intent+(profile.rotation()%2==0?" mix":" discoveries"));}
        List<String> out=new ArrayList<>();for(String q:queries)out.add(profile.language().query(q));return out;
    }
    public static String retrievalKey(Profile profile,Mood mood){return "music-only-v3|"+profile.language().name()+"|"+profile.rotation()+"|"+String.join("|",queries(profile,mood));}
    public static List<String> playlistQueries(Profile profile){List<String> out=new ArrayList<>();for(String artist:artists(profile)){out.add(profile.language().query(artist+" music mix playlists"));if(out.size()==2)break;}if(!profile.searches().isEmpty())out.add(profile.language().query(profile.searches().get(Math.floorMod((int)profile.rotation(),profile.searches().size())).query()+" playlists"));if(out.isEmpty())out.add(profile.language().query("discover music mix playlists"));return out;}
    public static List<Track> load(Profile profile,Mood mood,Songs source)throws Exception{
        return rank(profile,mood,candidates(profile,mood,source),12);
    }
    public static List<Track> candidates(Profile profile,Mood mood,Songs source)throws Exception {
        if(mood==Mood.REPEAT)return repeat(profile);
        LinkedHashMap<String,Track> pool=new LinkedHashMap<>();Exception failure=null;int requests=0;
        for(String query:queries(profile,mood)){if(Thread.currentThread().isInterrupted())throw new InterruptedException();try{for(Track track:source.search(query))pool.putIfAbsent(track.id(),track);}catch(Exception error){failure=error;}requests++;if(requests>=(mood==Mood.FOR_YOU?3:2))break;}
        List<Track> result=new ArrayList<>(pool.values());if(result.size()>72)result=new ArrayList<>(result.subList(0,72));if(result.isEmpty()&&failure!=null)throw failure;return result;
    }
    public static List<Track> catalogCandidates(Profile profile,Mood mood,MusicCatalog catalog)throws Exception{
        return candidates(profile,mood,q->catalog.search(q,null,MusicCatalog.Mode.SONGS).tracks());
    }
    public static List<Track> repeat(Profile profile){List<Library.Taste> ordered=new ArrayList<>(profile.taste());ordered.sort(Comparator.<Library.Taste>comparingInt(t->t.plays()+(t.liked()?3:0)).reversed().thenComparing(Comparator.comparingLong(Library.Taste::lastPlayed).reversed()));return ordered.stream().map(Library.Taste::track).filter(t->!blocked(profile,t.id())).limit(12).collect(java.util.stream.Collectors.toList());}
    public static List<Track> rank(Profile profile,Mood mood,List<Track> candidates,int limit){
        Map<String,Double> affinity=new HashMap<>();Set<String> familiar=new HashSet<>();for(var taste:profile.taste()){if(recentlySkipped(profile,taste.track().id()))continue;familiar.add(taste.track().id());affinity.merge(artist(taste.track().artist()).toLowerCase(Locale.ROOT),Math.min(10,2+Math.log1p(taste.plays())+(taste.liked()?3:0)),Double::sum);}
        if(profile.current()!=null)affinity.merge(artist(profile.current().artist()).toLowerCase(Locale.ROOT),8d,Double::sum);
        LinkedHashMap<String,Track> unique=new LinkedHashMap<>();for(Track track:candidates)if(VideoIds.valid(track.id())&&!blocked(profile,track.id())&&(profile.current()==null||!track.id().equals(profile.current().id()))&&(mood!=Mood.DISCOVERY||!familiar.contains(track.id())))unique.putIfAbsent(track.id(),track);
        List<String> styleHints=styles(profile);Map<String,Double> scores=new HashMap<>();for(Track t:unique.values())scores.put(t.id(),score(profile,mood,t,affinity,familiar,styleHints));
        List<Track> ranked=new ArrayList<>(unique.values());ranked.sort(Comparator.<Track>comparingDouble(t->scores.get(t.id())).reversed().thenComparing(Track::id));
        List<Track> out=new ArrayList<>();Map<String,Integer> perArtist=new HashMap<>();for(Track track:ranked){String artist=artist(track.artist()).toLowerCase(Locale.ROOT);if(perArtist.getOrDefault(artist,0)>=2)continue;out.add(track);perArtist.merge(artist,1,Integer::sum);if(out.size()==limit)break;}return out;
    }
    private static double score(Profile profile,Mood mood,Track t,Map<String,Double> affinity,Set<String> familiar,List<String> styleHints){double score=affinity.getOrDefault(artist(t.artist()).toLowerCase(Locale.ROOT),0d);String text=(t.title()+" "+t.artist()).toLowerCase(Locale.ROOT);for(var search:profile.searches())for(String token:search.query().toLowerCase(Locale.ROOT).split("\\s+"))if(token.length()>2&&text.contains(token))score+=1.5*Math.log1p(search.count())/Math.log(2);score+=familiar.contains(t.id())?-2:3;score-=skipPenalty(profile,t.id());for(String style:styleHints)if(text.contains(style))score+=4;if(mood!=Mood.FOR_YOU&&mood!=Mood.REPEAT){score*=.25;for(String token:mood.intent.split(" "))if(token.length()>3&&text.contains(token))score+=6;}return score+Math.floorMod((t.id()+profile.rotation()+mood.name()).hashCode(),1000)/1000d;}
    public static List<PlaylistSource.Suggestion> playlists(Profile profile,PlaylistSource source)throws Exception{
        LinkedHashMap<String,PlaylistSource.Suggestion> pool=new LinkedHashMap<>();Exception failure=null;for(String q:playlistQueries(profile)){try{for(var p:source.suggest(q))pool.putIfAbsent(p.url(),p);}catch(Exception error){failure=error;}}
        List<PlaylistSource.Suggestion> values=new ArrayList<>(pool.values());List<String> artists=artists(profile);values.sort(Comparator.<PlaylistSource.Suggestion>comparingInt(p->{String text=(p.title()+" "+p.author()).toLowerCase(Locale.ROOT);int score=0;for(String artist:artists)if(text.contains(artist.toLowerCase(Locale.ROOT)))score+=3;for(var search:profile.searches())if(text.contains(search.query().toLowerCase(Locale.ROOT)))score+=2;return score;}).reversed());if(!values.isEmpty())Collections.rotate(values,-Math.floorMod((int)profile.rotation(),values.size()));if(values.isEmpty()&&failure!=null)throw failure;return new ArrayList<>(values.subList(0,Math.min(12,values.size())));
    }
    public static String encode(List<Track> tracks)throws JSONException{JSONArray array=new JSONArray();for(Track t:tracks)array.put(new JSONObject().put("id",t.id()).put("title",t.title()).put("artist",t.artist()).put("art",t.artwork()).put("duration",t.duration()));return array.toString();}
    public static List<Track> decode(String value)throws JSONException{JSONArray array=new JSONArray(value);List<Track> out=new ArrayList<>();for(int i=0;i<Math.min(72,array.length());i++){JSONObject t=array.getJSONObject(i);out.add(new Track(t.getString("id"),t.getString("title"),t.getString("artist"),t.getString("art"),t.getLong("duration")));}return out;}
}
