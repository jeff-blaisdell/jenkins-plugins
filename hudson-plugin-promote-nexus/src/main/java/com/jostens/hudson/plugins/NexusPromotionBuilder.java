package com.jostens.hudson.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;
import hudson.tasks.BatchFile;

/**
 * @author blaisj1
 * @brief Hudson build step plugin to provide the ability to promote a snapshot
 *        to a release within a Nexus Artifact Repository.
 */
public class NexusPromotionBuilder extends Builder {

	// Maven command to set the pom.xml file's version tag.
	private static final String setVersion = "mvn org.codehaus.mojo:versions-maven-plugin:1.2:set -f./target/pom.xml -DnewVersion={releaseVersion} -DgenerateBackupPoms=false";
	// Maven command to deploy the release artifact to a repository.
	private static final String promoteArtifact = "mvn org.apache.maven.plugins:maven-deploy-plugin:2.5:deploy-file -Durl={repoUrl} -DrepositoryId={repodId} -DpomFile=.\\target\\pom.xml -Dfile=.\\target\\{artifactId}-{releaseVersion}.{ext} -DuniqueVersion=false";

	private final String artifactId;
	private final String releaseVersion;
	private final String type;
	private final String repositoryUrl;
	private final String repositoryId;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public NexusPromotionBuilder(String artifactId, String releaseVersion, String type, String repositoryUrl, String repositoryId) {
		this.artifactId = artifactId;
		this.releaseVersion = releaseVersion;
		this.type = type;
		this.repositoryUrl = repositoryUrl;
		this.repositoryId = repositoryId;
	}

	// This is where you 'build' the project.
	// All build logic should be placed within this method.
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {

		// List of commands to run.
		List<BatchFile> commands = new ArrayList<BatchFile>();

		// Gather Hudson environment variables.
		try {
			this.environmentVariables = build.getEnvironment(listener);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to gather Hudson environment variables."));
			return false;
		}

		// Generate the windows batch scripts to run.
		String setVersionCommand = this.generateSetVersionScript();
		String promoteArtifactCommand = this.generatePromoteArtifactScript();


		commands.add(new BatchFile(setVersionCommand));
		commands.add(new BatchFile(promoteArtifactCommand));

		// Run windows commands.
		for (BatchFile command : commands) {
			boolean successFlag = command.perform(build, launcher, listener);
			if (!successFlag) {
				return false;
			}
		}

		return true;
	}

	private String generateSetVersionScript() {
		String command = NexusPromotionBuilder.setVersion;
		command = command.replace("{releaseVersion}", releaseVersion);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	private String generatePromoteArtifactScript() {
		String command = NexusPromotionBuilder.promoteArtifact;
		command = command.replace("{repoUrl}", this.repositoryUrl);
		command = command.replace("{repodId}", this.repositoryId);
		command = command.replace("{artifactId}", this.artifactId);
		command = command.replace("{releaseVersion}", this.releaseVersion);
		command = command.replace("{ext}", this.type);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	public EnvVars getEnvironmentVariables() {
		return environmentVariables;
	}

	public void setEnvironmentVariables(EnvVars environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getType() {
		return type;
	}

	public String getRepositoryUrl() {
		return repositoryUrl;
	}

	public String getRepositoryId() {
		return repositoryId;
	}

	public String getReleaseVersion() {
		return releaseVersion;
	}

	/**
	 * @brief The descriptor inner class is responsible for communicating
	 *        between the jelly configuration files, and the plugin class.
	 */
	// The @Extension annotation registers the outer class as a plugin.
	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {

		public DescriptorImpl() {
			super(NexusPromotionBuilder.class);
		}

		// This is the name shown on the Hudson project configuration page.
		// [Build Step dropdown menu].
		public String getDisplayName() {
			return "Promote Nexus Artifact to Release";
		}

		// Retrieves values from jelly to create a new instance of our builder.
		public NexusPromotionBuilder newInstance(StaplerRequest req) throws FormException {
			return new NexusPromotionBuilder(req.getParameter("promote.artifactId"), req.getParameter("promote.releaseVersion"), req.getParameter("promote.type"), req.getParameter("promote.repositoryUrl"), req.getParameter("promote.repositoryId"));
		}

		// This utilizes the @DataBoundConstructor annotation to automatically
		// gather our form data, and call our builder constructor.
		public NexusPromotionBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return (NexusPromotionBuilder) req.bindJSON(NexusPromotionBuilder.class, formData);
		}

	}
}
