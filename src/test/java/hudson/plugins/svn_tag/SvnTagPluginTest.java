package hudson.plugins.svn_tag;

import java.util.HashMap;

import org.testng.annotations.Test;


/**
 * TODO: Javadoc.
 *
 * @version $Revision$
 */
public class SvnTagPluginTest {
    @Test public void testEvalTagComment() throws Exception {
        String s = SvnTagPlugin.evalGroovyExpression(new HashMap(), "Simple tag");
        assert s.equals("Simple tag") : "Failed simple tag test. Value '" + s + "'";
        System.setProperty("foo", "bar");
        s = SvnTagPlugin.evalGroovyExpression(new HashMap(), "Tag with sys props ${sys['foo']}.");
        assert s.equals("Tag with sys props bar.") : "Failed sys prop embedded tag test. Value '" + s + "'";
        String envValue = System.getenv("ENV_FOO");
        if(envValue != null && envValue.equals("env_bar")) {
            System.out.println("Env value '" + envValue + "'");
            s = SvnTagPlugin.evalGroovyExpression(System.getenv(), "Tag with env ${env['ENV_FOO']}.");
            assert s.equals("Tag with env env_bar.") : "Failed env prop embedded tag test.Value '" + s + "'";
        }
    }

}
