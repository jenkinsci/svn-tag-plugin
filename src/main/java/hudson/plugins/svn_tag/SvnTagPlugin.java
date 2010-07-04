package hudson.plugins.svn_tag;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.SubversionSCM;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Consolidates the work common in Publisher and MavenReporter.
 *
 * @author Kenji Nakamura
 */
@SuppressWarnings(
        {"UtilityClass", "ImplicitCallToSuper", "MethodReturnOfConcreteClass",
                "MethodParameterOfConcreteClass", "InstanceofInterfaces"})
public class SvnTagPlugin {

    /**
     * Creates a new SvnTagPlugin object.
     */
    private SvnTagPlugin() {
    }

    /**
     * True if the operation was successful.
     *
     * @param abstractBuild build
     * @param launcher      launcher
     * @param buildListener build listener
     * @param tagBaseURLStr tag base URL string
     * @param tagComment    tag comment
     * @return true if the operation was successful
     */
    @SuppressWarnings({"FeatureEnvy", "UnusedDeclaration", "TypeMayBeWeakened",
            "LocalVariableOfConcreteClass"})
    public static boolean perform(AbstractBuild<?,?> abstractBuild,
                                  Launcher launcher,
                                  BuildListener buildListener,
                                  String tagBaseURLStr, String tagComment,
                                  String tagDeleteComment) {
        PrintStream logger = buildListener.getLogger();

        if (!Result.SUCCESS.equals(abstractBuild.getResult())) {
            logger.println(Messages.UnsuccessfulBuild());
            return true;
        }

        AbstractProject<?, ?> rootProject =
                abstractBuild.getProject().getRootProject();

        Map<String, String> env;

        if (!(rootProject.getScm() instanceof SubversionSCM)) {
            logger.println(Messages.NotSubversion(rootProject.getScm().toString()));
            return true;
        }

        SubversionSCM scm = SubversionSCM.class.cast(rootProject.getScm());
        try {
            env = abstractBuild.getEnvironment(buildListener);
        } catch (Exception e) {
            logger.println(
                    "Failed to get environment. " + e.getLocalizedMessage());
            return false;
        }

        // Let SubversionSCM fill revision number.
        // It is guaranteed for getBuilds() return the latest build (i.e.
        // current build) at first
        // The passed in abstractBuild may be the sub maven module and not
        // have revision.txt holding Svn revision information, so need to use
        // the build associated with the root level project.
        scm.buildEnvVars(rootProject.getBuilds().get(0), env);

        SubversionSCM.ModuleLocation[] moduleLocations = scm.getLocations();

        // environment variable "SVN_REVISION" doesn't contain revision number when multiple modules are
        // specified. Instead, parse revision.txt and obtain the corresponding revision numbers.
        Map<String, Long> revisions;
        try {
            revisions = parseRevisionFile(abstractBuild);
        } catch (IOException e) {
            logger.println(
                    "Failed to parse revision.txt. " + e.getLocalizedMessage());
            return false;
        }

        ISVNAuthenticationProvider sap =
                scm.getDescriptor().createAuthenticationProvider();
        if (sap == null) {
            logger.println("Subversion authentication info is not set.");
            return false;
        }

        ISVNAuthenticationManager sam =
                SVNWCUtil.createDefaultAuthenticationManager();
        sam.setAuthenticationProvider(sap);

        SVNCommitClient commitClient = new SVNCommitClient(sam, null);


        for (SubversionSCM.ModuleLocation ml : moduleLocations) {
            logger.println("moduleLocation: Remote ->" + ml.remote);

            List locationPathElements =
                    Arrays.asList(StringUtils.split(ml.remote, "/"));

            String evaledTagBaseURLStr =
                    evalGroovyExpression(env, tagBaseURLStr,
                            locationPathElements);

            URI repoURI;
            try {
                repoURI = new URI(StringUtils.replace(ml.remote, " ", "%20"));
            } catch (URISyntaxException e) {
                logger.println("Failed to parse SVN repo URL. " +
                        e.getLocalizedMessage());
                return false;
            }

            SVNURL parsedTagBaseURL = null;
            try {
                parsedTagBaseURL = SVNURL.parseURIEncoded(
                        repoURI.resolve(evaledTagBaseURLStr).toString());
                logger.println(
                        "Tag Base URL: '" + parsedTagBaseURL.toString() + "'.");
            } catch (SVNException e) {
                logger.println(
                        "Failed to parse tag base URL '" + evaledTagBaseURLStr +
                                "'. " + e.getLocalizedMessage());
            }

            try {
                String evalDeleteComment = evalGroovyExpression(
                        env, tagDeleteComment, locationPathElements);
                SVNCommitInfo deleteInfo =
                        commitClient.doDelete(new SVNURL[]{parsedTagBaseURL},
                                evalDeleteComment);
                SVNErrorMessage deleteErrMsg = deleteInfo.getErrorMessage();

                if (null != deleteErrMsg) {
                    logger.println(deleteErrMsg.getMessage());
                } else {
                    logger.println(Messages.DeleteOldTag(evaledTagBaseURLStr));
                }

            } catch (SVNException e) {
                logger.println(Messages.NoOldTag(evaledTagBaseURLStr));
            }

            SVNCopyClient copyClient = new SVNCopyClient(sam, null);

            try {
                String evalComment = evalGroovyExpression(
                        env, tagComment, locationPathElements);
                SVNRevision rev = SVNRevision.create(Long.valueOf(revisions.get(ml.remote)));

                SVNCommitInfo commitInfo =
                        copyClient.doCopy(new SVNCopySource[] {
                                    new SVNCopySource(rev, rev, SVNURL.parseURIEncoded(ml.remote)) },
                                parsedTagBaseURL, false,
                                true, false, evalComment, new SVNProperties());
                SVNErrorMessage errorMsg = commitInfo.getErrorMessage();

                if (null != errorMsg) {
                    logger.println(errorMsg.getFullMessage());
                    return false;
                } else {
                    logger.println(Messages.Tagged(commitInfo.getNewRevision()));
                }
            } catch (SVNException e) {
                logger.println(Messages.CopyFailed(e.getLocalizedMessage()));
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "TypeMayBeWeakened"})
    static String evalGroovyExpression(Map<String, String> env, String evalText,
                                       List locationPathElements) {
        Binding binding = new Binding();
        binding.setVariable("env", env);
        binding.setVariable("sys", System.getProperties());
        if (locationPathElements == null) {
            binding.setVariable("repoURL", Arrays.asList(StringUtils.split(
                    "http://svn.example.com/path1/path2/path3/path4/path5/path6/path7/path8/path9/path10"),
                    "/"));
        } else {
            binding.setVariable("repoURL", locationPathElements);
        }
        CompilerConfiguration config = new CompilerConfiguration();
        GroovyShell shell = new GroovyShell(binding, config);
        Object result = shell.evaluate("return \"" + evalText + "\"");
        if (result == null) {
            return "";
        } else {
            return result.toString().trim();
        }
    }


    /**
     * Reads the revision file of the specified build.
     *
     * @param build build object
     * @return map from Subversion URL to its revision.
     * @throws java.io.IOException thrown when operation failed
     */
    /*package*/
    @SuppressWarnings({"NestedAssignment"})
    static Map<String, Long> parseRevisionFile(AbstractBuild build)
            throws IOException {
        Map<String, Long> revisions =
                new HashMap<String, Long>(); // module -> revision
        // read the revision file of the last build
        File file = SubversionSCM.getRevisionFile(build);
        if (!file.exists()) // nothing to compare against
        {
            return revisions;
        }

        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                int index = line.lastIndexOf('/');
                if (index < 0) {
                    continue;   // invalid line?
                }
                try {
                    revisions.put(line.substring(0, index),
                            Long.parseLong(line.substring(index + 1)));
                } catch (NumberFormatException e) {
                    // perhaps a corrupted line. ignore
                }
            }
        } finally {
            br.close();
        }

        return revisions;
    }
}
