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
import static hudson.plugins.svn_tag.SvnTagPlugin.CONFIG_PREFIX;
import static hudson.plugins.svn_tag.SvnTagPlugin.DESCRIPTION;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.codehaus.groovy.control.CompilationFailedException;
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
     * SvnTag descriptor.
     */
    @Extension @SuppressWarnings({"StaticVariableOfConcreteClass"})
    public static final SvnTagDescriptorImpl DESCRIPTOR =
            new SvnTagDescriptorImpl();

    /**
     * tag base URL
     */
    private String tagBaseURL = null;

    private String tagComment = null;

    private String tagMkdirComment = null;

    private String tagDeleteComment = null;

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

    public String getTagMkdirComment() {
        if ((this.tagMkdirComment == null) ||
                (this.tagMkdirComment.length() == 0)) {
            return DESCRIPTOR.getTagMkdirComment();
        } else {
            return this.tagMkdirComment;
        }
    }

    public String getTagDeleteComment() {
        if ((this.tagDeleteComment == null) ||
                (this.tagDeleteComment.length() == 0)) {
            return DESCRIPTOR.getTagDeleteComment();
        } else {
            return this.tagDeleteComment;
        }
    }

    public void setTagComment(String tagComment) {
        this.tagComment = tagComment;
    }

    public void setTagMkdirComment(String tagMkdirComment) {
        this.tagMkdirComment = tagMkdirComment;
    }

    public void setTagDeleteComment(String tagDeleteComment) {
        this.tagDeleteComment = tagDeleteComment;
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
                this.getTagMkdirComment(),
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
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class SvnTagDescriptorImpl
            extends BuildStepDescriptor<Publisher> {

        private String defaultTagBaseURL = null;

        private String tagComment;

        private String tagMkdirComment;

        private String tagDeleteComment;

        /**
         * Creates a new SvnTagDescriptorImpl object.
         */
        private SvnTagDescriptorImpl() {
            super(SvnTagPublisher.class);
            this.defaultTagBaseURL =
                    "http://subversion_host/project/tags/last-successful/${env['JOB_NAME']}";
            this.tagComment =
                    "Tagged by Hudson svn-tag plugin. Build:${env['BUILD_TAG']}.";
            this.tagDeleteComment = "Delete old tag by SvnTag Hudson plugin.";
            this.tagMkdirComment = "Created by SvnTag Hudson plugin.";
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
            p.setTagMkdirComment(jsonObject.getString("tagMkdirComment"));
            p.setTagDeleteComment(jsonObject.getString("tagDeleteComment"));
            return p;
        }

        @SuppressWarnings({"deprecation"})
        @Override
        public boolean configure(StaplerRequest staplerRequest, JSONObject formData)
                throws FormException {
            this.defaultTagBaseURL =
                    staplerRequest.getParameter(CONFIG_PREFIX +
                            "defaultTagBaseURL");
            this.tagComment =
                    staplerRequest.getParameter(CONFIG_PREFIX + "tagComment");
            this.tagMkdirComment =
                    staplerRequest
                            .getParameter(CONFIG_PREFIX + "tagMkdirComment");
            this.tagDeleteComment =
                    staplerRequest
                            .getParameter(CONFIG_PREFIX + "tagDeleteComment");
            save();

            return super.configure(staplerRequest, formData);
        }

        public FormValidation doTagBaseURLCheck(@QueryParameter final String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Please specify URL.");
            }
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error("Check if quotes, braces, or brackets are balanced. " +
                        e.getMessage());
            }
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

        public String getTagMkdirComment() {
            return tagMkdirComment;
        }

        public void setTagMkdirComment(String tagMkdirComment) {
            this.tagMkdirComment = tagMkdirComment;
        }

        public String getTagDeleteComment() {
            return tagDeleteComment;
        }

        public void setTagDeleteComment(String tagDeleteComment) {
            this.tagDeleteComment = tagDeleteComment;
        }

        public FormValidation doTagCommentCheck(@QueryParameter final String value) {
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error("Check if quotes, braces, or brackets are balanced. " +
                        e.getMessage());
            }
        }

        public FormValidation doTagMkdirCommentCheck(@QueryParameter final String value) {
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error("Check if quotes, braces, or brackets are balanced. " +
                        e.getMessage());
            }
        }

        public FormValidation doTagDeleteCommentCheck(@QueryParameter final String value) {
            try {
                SvnTagPlugin.evalGroovyExpression(
                        new HashMap<String, String>(), value, null);
                return FormValidation.ok();
            } catch (CompilationFailedException e) {
                return FormValidation.error("Check if quotes, braces, or brackets are balanced. " +
                        e.getMessage());
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // need to check if this is a subversion project??
            return true;
        }

    }
}
