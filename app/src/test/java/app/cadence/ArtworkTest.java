package app.cadence;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.schabi.newpipe.extractor.Image;
public class ArtworkTest {
    @Test public void largestProviderImageWinsRatherThanFirst(){assertEquals("large",Artwork.largest(List.of(new Image("small",120,120,Image.ResolutionLevel.LOW),new Image("large",1280,1280,Image.ResolutionLevel.HIGH))));}
    @Test public void musicCoverResizesAndOriginalRemainsFallback(){String original="https://lh3.googleusercontent.com/cover=w120-h120-l90-rj";List<String> candidates=Artwork.candidates(original,1080);assertEquals("https://lh3.googleusercontent.com/cover=w1280-h1280-l90-rj",candidates.get(0));assertEquals(original,candidates.get(candidates.size()-1));}
    @Test public void videoCoverOffersLargeAndStandardFallbacks(){assertEquals(List.of("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg","https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg","https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"),Artwork.candidates("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",1080));}
    @Test public void signedOrUnknownUrlsArePreservedAndDecodeKeepsDisplayResolution(){String signed="https://lh3.googleusercontent.com/cover=w120-h120?signature=abc";assertEquals(List.of(signed),Artwork.candidates(signed,1280));String custom="https://example.test/cover=w120-h120";assertEquals(List.of(custom),Artwork.candidates(custom,1080));assertTrue(Artwork.candidates("http://example.test/cover",1080).isEmpty());assertEquals(1,Artwork.sampleSize(1280,1280,1080));assertEquals(2,Artwork.sampleSize(2560,2560,1080));}
}
