package com.jostens.hudson.plugins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Ant;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import antlr.actions.cpp.ActionLexer;

public class TomcatConfigureBuilder extends Builder {

	private static final String ANT_BUILD = "ant-replace-password.xml";
	private static final String copyFiles = "xcopy \"{configFilePath}\" \"{tomcatRootDirectory}\" /S /Y /F /E";

	private final String configFilePath;
	private final String tomcatRootDirectory;
	private final String passwordFilePath;
	private EnvVars environmentVariables;
	private final boolean deployConfiguration;
	private final boolean replacePasswords;

	@DataBoundConstructor
	public TomcatConfigureBuilder(String tomcatRootDirectory, String configFilePath, String passwordFilePath, String deployConfiguration, String replacePasswords) {
		this.tomcatRootDirectory = tomcatRootDirectory;
		this.configFilePath = configFilePath;
		this.passwordFilePath = passwordFilePath;
		this.deployConfiguration = Boolean.parseBoolean(deployConfiguration);
		this.replacePasswords = Boolean.parseBoolean(replacePasswords);
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
		boolean successFlag = false;
		try {
			List<BatchFile> commands = new ArrayList<BatchFile>();
			this.environmentVariables = build.getEnvironment(listener);

			if (deployConfiguration) {
				String copyFileCommand = generateCopyFileScript();
				commands.add(new BatchFile(copyFileCommand));

				for (BatchFile command : commands) {
					successFlag = command.perform(build, launcher, listener);
					if (!successFlag) {
						break;
					}
				}
			}

			if (replacePasswords) {

				// Get build file from class-path, and write to workspace.
				String antBuildFilePath = this.environmentVariables.expand("${WORKSPACE}") + "\\" + ANT_BUILD;
				File antBuild = new File(antBuildFilePath);
				this.writeToFile(this.getClass().getClassLoader().getResourceAsStream(ANT_BUILD), antBuild);

				// Create Ant properties.
				String passwordAntProperty = "password.properties=" + (this.environmentVariables.expand(this.passwordFilePath)).replace("\\", "\\\\");
				String serverConfigAntProperty = "server.configuration.path=" + (this.environmentVariables.expand(this.tomcatRootDirectory)).replace("\\", "\\\\") + "\\\\conf";
				String antProps = passwordAntProperty + System.getProperty("line.separator") + serverConfigAntProperty;

				// Determine Ant Installation.
				String antName = null;
				//TODO: Find a better way to get at the ant installations.
				Ant antInstall = new Ant(null, null, null, null, null);
				AntInstallation antInstallation = antInstall.getDescriptor().getInstallations()[0];
				if(antInstallation != null) {
					antName = antInstallation.getName();
				}
				
				// Call Ant.
				listener.getLogger().println("Using Ant Installation: " + antName);
				Ant ant = new Ant("replace-passwords", antName, null, antBuild.getAbsolutePath(), antProps);
               
				successFlag = ant.perform(build, launcher, listener);

			}

		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to complete build step."));
			return false;
		}

		return successFlag;
	}

	private String generateCopyFileScript() {
		String command = TomcatConfigureBuilder.copyFiles;
		command = command.replace("{configFilePath}", this.configFilePath);
		command = command.replace("{tomcatRootDirectory}", this.tomcatRootDirectory);

		return this.environmentVariables.expand(command);
	}

	private void writeToFile(InputStream input, File file) throws IOException {

		// write the inputStream to a FileOutputStream
		OutputStream out = new FileOutputStream(file);

		int read = 0;
		byte[] bytes = new byte[1024];

		while ((read = input.read(bytes)) != -1) {
			out.write(bytes, 0, read);
		}

		input.close();
		out.flush();
		out.close();

	}

	/**
	 * @return the environmentVariables
	 */
	public EnvVars getEnvironmentVariables() {
		return environmentVariables;
	}

	/**
	 * @param environmentVariables
	 *            the environmentVariables to set
	 */
	public void setEnvironmentVariables(EnvVars environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	/**
	 * @return the configFilePath
	 */
	public String getConfigFilePath() {
		return configFilePath;
	}

	/**
	 * @return the tomcatRootDirectory
	 */
	public String getTomcatRootDirectory() {
		return tomcatRootDirectory;
	}

	/**
	 * @return the passwordFilePath
	 */
	public String getPasswordFilePath() {
		return passwordFilePath;
	}

	/**
	 * @return the deployConfiguration
	 */
	public boolean isDeployConfiguration() {
		return deployConfiguration;
	}

	/**
	 * @return the replacePasswords
	 */
	public boolean isReplacePasswords() {
		return replacePasswords;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public DescriptorImpl() {
			super(TomcatConfigureBuilder.class);
		}

		public String getDisplayName() {
			return "Configure Tomcat";
		}

		public TomcatConfigureBuilder newInstance(StaplerRequest req) throws Descriptor.FormException {
			return new TomcatConfigureBuilder(req.getParameter("tomcatRootDirectory"), req.getParameter("configFilePath"), req.getParameter("passwordFilePath"), req.getParameter("deployConfiguration"), req.getParameter("replacePasswords"));
		}

		public TomcatConfigureBuilder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return (TomcatConfigureBuilder) req.bindJSON(TomcatConfigureBuilder.class, formData);
		}
	}
}