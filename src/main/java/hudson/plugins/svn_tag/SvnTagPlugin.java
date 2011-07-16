package hudson.plugins.svn_tag;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.EnvVars;
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
     * @throws InterruptedException 
     * @throws IOException 
     */
    @SuppressWarnings({"FeatureEnvy", "UnusedDeclaration", "TypeMayBeWeakened",
            "LocalVariableOfConcreteClass"})
    public static boolean perform(AbstractBuild<?,?> abstractBuild,
                                  Launcher launcher,
                                  BuildListener buildListener,
                                  String tagBaseURLStr, String tagComment,
                                  String tagDeleteComment) throws IOException, InterruptedException {
        PrintStream logger = buildListener.getLogger();

        if (!Result.SUCCESS.equals(abstractBuild.getResult())) {
            logger.println(Messages.UnsuccessfulBuild());
            return true;
        }

        AbstractProject<?, ?> rootProject =
                abstractBuild.getProject().getRootProject();

        if (!(rootProject.getScm() instanceof SubversionSCM)) {
            logger.println(Messages.NotSubversion(rootProject.getScm().toString()));
            return true;
        }

        SubversionSCM scm = SubversionSCM.class.cast(rootProject.getScm());
        EnvVars envVars = abstractBuild.getEnvironment(buildListener);

        // Let SubversionSCM fill revision number.
        // It is guaranteed for getBuilds() return the latest build (i.e.
        // current build) at first
        // The passed in abstractBuild may be the sub maven module and not
        // have revision.txt holding Svn revision information, so need to use
        // the build associated with the root level project.
        scm.buildEnvVars(rootProject.getBuilds().get(0), envVars);
        
        // environment variable "SVN_REVISION" doesn't contain revision number when multiple modules are
        // specified. Instead, parse revision.txt and obtain the corresponding revision numbers.
        Map<String, Long> revisions;
        try {
            revisions = parseRevisionFile(abstractBuild);
        } catch (IOException e) {
            logger.println(
            		Messages.FailedParsingRevisionFile(e.getLocalizedMessage()));
            return false;
        }

        ISVNAuthenticationProvider sap =
                scm.getDescriptor().createAuthenticationProvider(rootProject);
        if (sap == null) {
            logger.println(Messages.NoSVNAuthProvider());
            return false;
        }

        ISVNAuthenticationManager sam =
                SVNWCUtil.createDefaultAuthenticationManager();
        sam.setAuthenticationProvider(sap);

        SVNCommitClient commitClient = new SVNCommitClient(sam, null);

        for (SubversionSCM.ModuleLocation ml : scm.getLocations(envVars, abstractBuild)) {
			String mlUrl = null;
        	URI repoURI = null;
			try {
				mlUrl = ml.getSVNURL().toString();
				repoURI = new URI(mlUrl);
			} catch (SVNException e) {
        		logger.println(
        				Messages.FailedParsingRepositoryURL(ml.remote, e.getLocalizedMessage()));
        		return false;
			} catch (URISyntaxException e) {
        		logger.println(
        				Messages.FailedParsingRepositoryURL(ml.remote, e.getLocalizedMessage()));
        		return false;
        	}
        	logger.println(Messages.RemoteModuleLocation(mlUrl));

            List<String> locationPathElements = Arrays.asList(StringUtils.split(mlUrl, "/"));
            String evaledTagBaseURLStr = evalGroovyExpression(
            		envVars, tagBaseURLStr, locationPathElements);

            SVNURL parsedTagBaseURL = null;
            try {
                parsedTagBaseURL = SVNURL.parseURIDecoded(
                        repoURI.resolve(evaledTagBaseURLStr).toString());
                logger.println(Messages.TagBaseURL(parsedTagBaseURL.toString()));
            } catch (SVNException e) {
                logger.println(Messages.FailedParsingTagBaseURL(
           				evaledTagBaseURLStr, e.getLocalizedMessage()));
            }

            try {
                String evalDeleteComment = evalGroovyExpression(
                		envVars, tagDeleteComment, locationPathElements);
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
                        envVars, tagComment, locationPathElements);

                Long revision = revisions.get(mlUrl);
                if (revision == null)
                {
                	logger.println(Messages.RevisionNotAvailable(mlUrl));
                }
                SVNRevision rev = SVNRevision.create(revision);

                SVNCommitInfo commitInfo =
                        copyClient.doCopy(new SVNCopySource[] {
                                    new SVNCopySource(rev, rev, 
                                    		SVNURL.parseURIEncoded(mlUrl)) },
                                parsedTagBaseURL, false,
                                true, false, evalComment, new SVNProperties());
                SVNErrorMessage errorMsg = commitInfo.getErrorMessage();

                if (null != errorMsg) {
                    logger.println(Messages.FailedToTag(errorMsg.getFullMessage()));
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
                	revisions.put(SVNURL.parseURIEncoded(line.substring(0, index)).toString(),
                			Long.parseLong(line.substring(index + 1)));
                } catch (NumberFormatException e) {
                    // perhaps a corrupted line. ignore
                } catch(SVNException e) {
                	// perhaps a corrupted line. ignore
                }
            }
        } finally {
            br.close();
        }

        return revisions;
    }
}
