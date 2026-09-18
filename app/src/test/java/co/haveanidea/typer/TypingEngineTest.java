package co.haveanidea.typer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TypingEngineTest {
    private final TypingEngine engine=new TypingEngine(Arrays.asList("hello","help","home","house","thanks","there","their"));
    @Test public void preservesUnknownNames(){assertEquals("Siddharth",engine.correction("Siddharth"));}
    @Test public void correctionKeepsCase(){assertEquals("The",engine.correction("Teh"));assertEquals("THE",engine.correction("TEH"));}
    @Test public void suggestsPrefix(){assertEquals(Arrays.asList("hel","hello","help"),engine.suggestions("hel"));}
    @Test public void transposition(){assertEquals(1,TypingEngine.distance("teh","the"));}
    @Test public void localCleanupPreservesNumbersAndNegation(){assertEquals("Don't pay 500.",TypingEngine.basicCleanup("um don't pay 500"));}
    @Test public void emptyCleanup(){assertEquals("",TypingEngine.basicCleanup("um uh"));}
    @Test public void noInventedSwipe(){assertTrue(engine.glide("zx").isEmpty());}
    @Test public void glideRanksKnownWord(){assertEquals("home",engine.glide("home").get(0));}
}
