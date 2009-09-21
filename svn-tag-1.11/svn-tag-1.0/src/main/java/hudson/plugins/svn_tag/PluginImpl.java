package hudson.plugins.svn_tag;

import hudson.Plugin;
import hudson.tasks.BuildStep;


/**
 * Entry point of Subversion Tagging plugin.
 *
 * @author Kenji Nakamura
 */
public class PluginImpl extends Plugin {
    @Override
    public void start() throws Exception {
        BuildStep.PUBLISHERS.add(SvnTagPublisher.DESCRIPTOR);
    }
}
