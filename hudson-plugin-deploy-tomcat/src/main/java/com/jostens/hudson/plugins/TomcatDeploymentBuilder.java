package com.jostens.hudson.plugins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class TomcatDeploymentBuilder extends Builder {

	private static final String copyFile = "copy \"{sourceDirectory}\\{sourceWar}.war\" \"{tomcatRootDirectory}\\webapps\\{sourceWar}.war\"";
	private static final String extractFile = "unzip \"{tomcatRootDirectory}\\webapps\\{sourceWar}.war\" -d\"{tomcatRootDirectory}\\webapps\\{sourceWar}\"";
	private static final String cleanTomcatWebAppDirectory = "rmdir /s /q \"{tomcatRootDirectory}\\webapps\\{sourceWar}\"";
	private static final String cleanTomcatWebAppArtifact = "del \"{tomcatRootDirectory}\\webapps\\{sourceWar}.war\"";
	private static final String cleanTomcatWorkDirectory = "rmdir /s /q \"{tomcatRootDirectory}\\work\\Catalina\\localhost\\{sourceWar}\"";

	private final String sourceWar;
	private final String sourceDirectory;
	private final String tomcatRootDirectory;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public TomcatDeploymentBuilder(String sourceWar, String sourceDirectory, String tomcatRootDirectory) {
		this.sourceWar = sourceWar;
		this.sourceDirectory = sourceDirectory;
		this.tomcatRootDirectory = tomcatRootDirectory;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
		boolean successFlag = false;

		List<BatchFile> commands = new ArrayList<BatchFile>();
		try {
			this.environmentVariables = build.getEnvironment(listener);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to gather Hudson environment variables."));
			return false;
		}

		String copyFileCommand = generateCopyFileScript();
		String extractFileCommand = generateExtractFileScript();
		String cleanTomcatWebAppDirectory = generateCleanTomcatWebAppDirectory();
		String cleanTomcatWorkDirectory = generateCleanTomcatWorkDirectory();
		String cleanTomcatWebAppArtifact = generateCleanTomcatWebAppArtifact();

		commands.add(new BatchFile(cleanTomcatWebAppDirectory));
		commands.add(new BatchFile(cleanTomcatWebAppArtifact));
		commands.add(new BatchFile(cleanTomcatWorkDirectory));
		commands.add(new BatchFile(copyFileCommand));
		commands.add(new BatchFile(extractFileCommand));

		for (BatchFile command : commands) {
			successFlag = command.perform(build, launcher, listener);
			if (!successFlag) {
				break;
			}
		}

		return successFlag;
	}

	private String generateCopyFileScript() {
		String command = TomcatDeploymentBuilder.copyFile;
		command = command.replace("{sourceDirectory}", this.sourceDirectory);
		command = command.replace("{sourceWar}", this.sourceWar);
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);

		return this.environmentVariables.expand(command);
	}

	private String generateExtractFileScript() {
		String command = TomcatDeploymentBuilder.extractFile;
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);
		command = command.replace("{sourceWar}", this.sourceWar);

		return this.environmentVariables.expand(command);
	}

	private String generateCleanTomcatWebAppArtifact() {
		String command = TomcatDeploymentBuilder.cleanTomcatWebAppArtifact;
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);
		command = command.replace("{sourceWar}", this.sourceWar);

		return this.environmentVariables.expand(command);
	}

	private String generateCleanTomcatWebAppDirectory() {
		String command = TomcatDeploymentBuilder.cleanTomcatWebAppDirectory;
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);
		command = command.replace("{sourceWar}", this.sourceWar);

		return this.environmentVariables.expand(command);
	}

	private String generateCleanTomcatWorkDirectory() {
		String command = TomcatDeploymentBuilder.cleanTomcatWorkDirectory;
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);
		command = command.replace("{sourceWar}", this.sourceWar.replace("#", "_"));

		return this.environmentVariables.expand(command);
	}

	public EnvVars getEnvironmentVariables() {
		return this.environmentVariables;
	}

	public void setEnvironmentVariables(EnvVars environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	/**
	 * @return the sourceWar
	 */
	public String getSourceWar() {
		return sourceWar;
	}

	/**
	 * @return the sourceLocation
	 */
	public String getSourceDirectory() {
		return sourceDirectory;
	}

	/**
	 * @return the tomcatRootDirectory
	 */
	public String getTomcatRootDirectory() {
		return tomcatRootDirectory;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public DescriptorImpl() {
			super(TomcatDeploymentBuilder.class);
		}

		public String getDisplayName() {
			return "Deploy WAR to Tomcat";
		}

		public TomcatDeploymentBuilder newInstance(StaplerRequest req) throws Descriptor.FormException {
			return new TomcatDeploymentBuilder(req.getParameter("sourceWar"), req.getParameter("sourceDirectory"), req.getParameter("tomcatRootDirectory"));
		}

		public TomcatDeploymentBuilder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return (TomcatDeploymentBuilder) req.bindJSON(TomcatDeploymentBuilder.class, formData);
		}
	}
}