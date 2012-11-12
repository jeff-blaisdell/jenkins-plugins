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

public class GetNexusArtifactBuilder extends Builder {

	private static final String POM_XML = "com//jostens//hudson//plugins//GetNexusArtifactBuilder//pom.xml";
	private static final String renameArtifact = "rename .\\target\\{artifactId}*.{ext} {name}";
	private static final String cleanWorkspace = "mvn clean";
	private static final String getArtifactToDeploy = "mvn package -DgrpId={groupId} -DartId={artifactId} -Dvers={version} -Dext={ext}";

	private final String artifactId;
	private final String groupId;
	private final String version;
	private final String type;
	private final String name;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public GetNexusArtifactBuilder(String artifactId, String groupId, String version, String type, String name) {
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.type = type;
		this.name = name;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
		boolean successFlag = false;

		/**
		 * Get HUDSON Environment Variables.
		 */
		List<BatchFile> commands = new ArrayList<BatchFile>();
		try {
			this.environmentVariables = build.getEnvironment(listener);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to gather Hudson environment variables."));
			return false;
		}
		
		/**
		 * We need to get the POM file, and place it in the workspace so we can
		 * issue MVN commands against it.
		 */
		try {
			// Get current workspace directory.
			String workspace = this.environmentVariables.expand("${WORKSPACE}");
			
			// Create a new file in workspace.
			File file = new File(workspace + "\\pom.xml");
			listener.getLogger().println(file.getAbsolutePath());
			
			// Copy the resource into our new file.
			this.writeToFile(this.getClass().getClassLoader().getResourceAsStream(GetNexusArtifactBuilder.POM_XML), file);
			
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to get the required POM file."));
			return false;
		}		

		// Create Windows Scripts.
		String getArtifactCommand = generateGetArtifactScript();
		String renameArtifactCommand = generateRenameArtifactScript();

		commands.add(new BatchFile(GetNexusArtifactBuilder.cleanWorkspace));
		commands.add(new BatchFile(getArtifactCommand));
		if (!GetNexusArtifactBuilder.isBlank(this.name)) {
			commands.add(new BatchFile(renameArtifactCommand));
		}

		for (BatchFile command : commands) {
			successFlag = command.perform(build, launcher, listener);
			if (!successFlag) {
				break;
			}
		}
		listener.getLogger().println("Artifact {" + this.artifactId + "." + this.type + "} has been fetched.");
		return successFlag;
	}

	private String generateRenameArtifactScript() {
		String command = GetNexusArtifactBuilder.renameArtifact;
		command = command.replace("{artifactId}", this.artifactId);
		command = command.replace("{ext}", this.type);
		command = command.replace("{name}", this.name);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	private String generateGetArtifactScript() {
		String command = GetNexusArtifactBuilder.getArtifactToDeploy;
		command = command.replace("{groupId}", this.groupId);
		command = command.replace("{artifactId}", this.artifactId);
		command = command.replace("{version}", this.version);
		command = command.replace("{ext}", this.type);

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

	public EnvVars getEnvironmentVariables() {
		return this.environmentVariables;
	}

	public void setEnvironmentVariables(EnvVars environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	public String getArtifactId() {
		return this.artifactId;
	}

	public String getGroupId() {
		return this.groupId;
	}

	public String getVersion() {
		return this.version;
	}

	public String getType() {
		return this.type;
	}

	public String getName() {
		return name;
	}

	public static boolean isBlank(String str) {
		int strLen;
		if ((str == null) || ((strLen = str.length()) == 0))
			return true;

		for (int i = 0; i < strLen; ++i) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public DescriptorImpl() {
			super(GetNexusArtifactBuilder.class);
		}

		public String getDisplayName() {
			return "Get Nexus Artifact";
		}

		public GetNexusArtifactBuilder newInstance(StaplerRequest req) throws Descriptor.FormException {
			return new GetNexusArtifactBuilder(req.getParameter("artifactId"), req.getParameter("groupId"), req.getParameter("version"), req.getParameter("type"), req.getParameter("name"));
		}

		public GetNexusArtifactBuilder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return (GetNexusArtifactBuilder) req.bindJSON(GetNexusArtifactBuilder.class, formData);
		}
	}
}