package hudson.plugins.svn_tag;

import hudson.tasks.BuildStepMonitor;
import java.io.IOException;
import java.util.HashMap;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.codehaus.groovy.control.CompilationFailedException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 * Performs <tt>svn copy</tt> when the build was successfully done. Note that
 * this plugin is executed after the build state is finalized, and the errors
 * happened in this plugin doesn't affect to the state of the build.
 *
 * @author Kenji Nakamura
 */
@SuppressWarnings({"PublicMethodNotExposedInInterface"})
public class SvnTagPublisher extends Notifier {

    /**
     * tag base URL
     */
    private String tagBaseURL = null;

    private String tagComment = null;

    @Deprecated
    private transient String tagMkdirComment;

    private String tagDeleteComment = null;

    @DataBoundConstructor
    public SvnTagPublisher(String tagBaseURL, String tagComment, String tagDeleteComment) {
        this.tagBaseURL = tagBaseURL;
        this.tagComment = tagComment;
        this.tagDeleteComment = tagDeleteComment;
    }

    /**
     * Returns the tag base URL value.
     *
     * @return the tag base URL value.
     */
    public String getTagBaseURL() {
        return this.tagBaseURL;
    }

    public String getTagComment() {
        return this.tagComment;
    }

    public String getTagDeleteComment() {
        return this.tagDeleteComment;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild,
                           Launcher launcher,
                           BuildListener buildListener)
            throws InterruptedException, IOException {
        return SvnTagPlugin.perform(abstractBuild, launcher, buildListener,
                this.getTagBaseURL(), this.getTagComment(),
                this.getTagDeleteComment());
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
    @Override
    public SvnTagDescriptorImpl getDescriptor() {
        return (SvnTagDescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class SvnTagDescriptorImpl
            extends BuildStepDescriptor<Publisher> {

        private String defaultTagBaseURL = null;

        private String tagComment;

        @Deprecated
        private transient String tagMkdirComment;

        private String tagDeleteComment;

        /**
         * Creates a new SvnTagDescriptorImpl object.
         */
        public SvnTagDescriptorImpl() {
            this.defaultTagBaseURL = Messages.DefaultTagBaseURL();
            this.tagComment = Messages.DefaultTagComment();
            this.tagDeleteComment = Messages.DefaultTagDeleteComment();
            load();
        }

        /**
         * Returns the display name value.
         *
         * @return the display name value.
         */
        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {
            req.bindJSON(this, formData);
            save();

            return super.configure(req, formData);
        }

        public FormValidation doCheckTagBaseURL(@QueryParameter final String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error(Messages.MissingURL());
            }
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error(Messages.BadGroovy(e.getMessage()));
            }
        }

        public FormValidation doCheckDefaultTagBaseURL(@QueryParameter final String value) {
            return doCheckTagBaseURL(value);
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

        public String getTagDeleteComment() {
            return tagDeleteComment;
        }

        public void setTagDeleteComment(String tagDeleteComment) {
            this.tagDeleteComment = tagDeleteComment;
        }

        public FormValidation doCheckTagComment(@QueryParameter final String value) {
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error(Messages.BadGroovy(e.getMessage()));
            }
        }

        public FormValidation doCheckTagDeleteComment(@QueryParameter final String value) {
            return doCheckTagComment(value);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // need to check if this is a subversion project??
            return true;
        }

    }
}
