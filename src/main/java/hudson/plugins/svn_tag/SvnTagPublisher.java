package hudson.plugins.svn_tag;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import static hudson.plugins.svn_tag.SvnTagPlugin.CONFIG_PREFIX;
import static hudson.plugins.svn_tag.SvnTagPlugin.DESCRIPTION;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidator;
import net.sf.json.JSONObject;
import org.codehaus.groovy.control.CompilationFailedException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;


/**
 * Performs <tt>svn copy</tt> when the build was successfully done. Note that
 * this plugin is executed after the build state is finalized, and the errors
 * happened in this plugin doesn't affect to the state of the build.
 *
 * @author Kenji Nakamura
 */
@SuppressWarnings({"PublicMethodNotExposedInInterface"})
public class SvnTagPublisher extends Publisher {
    /**
     * SvnTag descriptor.
     */
    @SuppressWarnings({"StaticVariableOfConcreteClass"})
    public static final SvnTagDescriptorImpl DESCRIPTOR =
            new SvnTagDescriptorImpl();

    /**
     * tag base URL
     */
    private String tagBaseURL = null;

    private String tagComment = null;

    /**
     * Returns the tag base URL value.
     *
     * @return the tag base URL value.
     */
    public String getTagBaseURL() {
        if ((this.tagBaseURL == null) || (this.tagBaseURL.length() == 0)) {
            return DESCRIPTOR.getDefaultTagBaseURL();
        } else {
            return this.tagBaseURL;
        }
    }

    public void setTagBaseURL(String tagBaseURL) {
        this.tagBaseURL = tagBaseURL;
    }

    public String getTagComment() {
        if ((this.tagComment == null) || (this.tagComment.length() == 0)) {
            return DESCRIPTOR.getTagComment();
        } else {
            return this.tagComment;
        }
    }

    public void setTagComment(String tagComment) {
        this.tagComment = tagComment;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild,
                           Launcher launcher,
                           BuildListener buildListener)
            throws InterruptedException, IOException {
        return SvnTagPlugin.perform(abstractBuild, launcher, buildListener,
                this.tagBaseURL, this.tagComment);
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    /**
     * Returns the descriptor value.
     *
     * @return the descriptor value.
     */
    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class SvnTagDescriptorImpl
            extends Descriptor<Publisher> {

        private String defaultTagBaseURL = null;

        private String tagComment;

        /**
         * Creates a new SvnTagDescriptorImpl object.
         */
        private SvnTagDescriptorImpl() {
            super(SvnTagPublisher.class);
            this.defaultTagBaseURL = "http://subversion_host/project/tags/last-successful/${env['JOB_NAME']}";
            this.tagComment = "Tagged by Hudson svn-tag plugin. Build:${env['BUILD_TAG']}.";
            load();
        }

        /**
         * Returns the display name value.
         *
         * @return the display name value.
         */
        @Override
        public String getDisplayName() {
            return DESCRIPTION;
        }

        @SuppressWarnings({"LocalVariableOfConcreteClass"})
        @Override
        public Publisher newInstance(StaplerRequest staplerRequest,
                                     JSONObject jsonObject)
                throws FormException {
            SvnTagPublisher p = new SvnTagPublisher();
            p.setTagBaseURL(jsonObject.getString("tagBaseURL"));
            p.setTagComment(jsonObject.getString("tagComment"));
            return p;
        }

        @Override
        public boolean configure(StaplerRequest staplerRequest)
                throws FormException {
            this.defaultTagBaseURL =
                    staplerRequest.getParameter(CONFIG_PREFIX +
                            "defaultTagBaseURL");
            this.tagComment =
                    staplerRequest.getParameter(CONFIG_PREFIX + "tagComment");
            save();

            return super.configure(staplerRequest);
        }

        public void doTagBaseURLCheck(final StaplerRequest req,
                                      StaplerResponse rsp)
                throws IOException, ServletException {
            new FormFieldValidator(req, rsp, false) {
                @Override
                protected void check() throws IOException,
                        ServletException {
                    String tagBaseURLString = req.getParameter("value");

                    if ((tagBaseURLString == null) ||
                            (tagBaseURLString.length() == 0)) {
                        error("Please specify URL.");
                    }
                    try {
                        SvnTagPlugin.evalGroovyExpression(new HashMap<String, String>(), tagBaseURLString);
                        ok();
                    } catch (CompilationFailedException e) {
                        error("Check if quotes, braces, or brackets are balanced. " + e.getMessage());
                    }
                }
            }.process();
        }

        /**
         * Returns the default tag base URL value.
         *
         * @return the default tag base URL value.
         */
        public String getDefaultTagBaseURL() {
            return this.defaultTagBaseURL;
        }

        /**
         * Sets the value of default tag base URL.
         *
         * @param defaultTagBaseURL the default tag base URL value.
         */
        public void setDefaultTagBaseURL(String defaultTagBaseURL) {
            this.defaultTagBaseURL = defaultTagBaseURL;
        }

        /**
         * Returns the tag comment value.
         *
         * @return the tag comment value.
         */
        public String getTagComment() {
            return this.tagComment;
        }

        /**
         * Sets the value of tag comment.
         *
         * @param tagComment the tag comment value.
         */
        public void setTagComment(String tagComment) {
            this.tagComment = tagComment;
        }

        public void doTagCommentCheck(StaplerRequest req, StaplerResponse rsp,
                                      @QueryParameter("value") final String value)
                throws IOException, ServletException {
            new FormFieldValidator(req, rsp, false) {
                @Override
                protected void check() throws IOException, ServletException {
                    try {
                        SvnTagPlugin.evalGroovyExpression(new HashMap<String, String>(), value);
                        ok();
                    } catch (CompilationFailedException e) {
                        error("Check if quotes, braces, or brackets are balanced. " + e.getMessage());
                    }
                }
            }.process();
        }
    }
}
