package com.jostens.hudson.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
 * @brief Hudson build step plugin to provide the ability to deploy an artifact
 *        to a Nexus Artifact Repository.
 */
public class NexusDeploymentBuilder extends Builder {
	
	// Windows command to rename a file.
	private static final String renameArtifact = "cd {dir} \nrename {artifactId}*.{ext} {artifactId}-{version}.{ext}";
	// Maven command to set the version tag located in the pom.xml.
	private static final String mavenChangePomVersion = "cd {dir} \nmvn versions:set -DnewVersion={version} -DgenerateBackupPoms=false";
	// Maven command to deploy an artifact to a repository.
	private static final String mavenDeployArtifact = "cd {dir} \nmvn org.apache.maven.plugins:maven-deploy-plugin:2.5:deploy-file -Durl={repoUrl} -DrepositoryId={repoId} -DpomFile=pom.xml -Dfile={artifactId}-{version}.{ext} -DuniqueVersion=true";

	private final String targetDir;
	private final String artifactId;
	private final String groupId;
	private final String version;
	private final String type;
	private final String svnRevision;
	private final String repositoryUrl;
	private final String repositoryId;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public NexusDeploymentBuilder(String targetDir, String artifactId, String groupId, String version, String type, String svnRevision, String repositoryUrl, String repositoryId) {
		this.targetDir = targetDir;
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.type = type;
		this.svnRevision = svnRevision;
		this.repositoryUrl = repositoryUrl;
		this.repositoryId = repositoryId;
	}

	// This is where you 'build' the project.
	// All build logic should be placed within this method.
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {

		// Flag to track the outcome of each windows command.
		boolean successFlag = false;

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
		String changeVersionCommand = this.generateMavenSetPomVersionScript();
		String renameArtifactCommand = this.generateRenameArtifactScript();
		String deployArtifactCommand = this.generateMavenDeployScript();

		// Change the version tag on our pom.xml.
		successFlag = this.setPomSvnRevision(build, listener);

		if (successFlag) {
			commands.add(new BatchFile(changeVersionCommand));
			commands.add(new BatchFile(renameArtifactCommand));
			commands.add(new BatchFile(deployArtifactCommand));

			// Run all other windows commands.
			for (BatchFile command : commands) {
				successFlag = command.perform(build, launcher, listener);
				if (!successFlag) {
					break;
				}
			}
		}
		return successFlag;
	}

	private String generateMavenSetPomVersionScript() {
		String command = NexusDeploymentBuilder.mavenChangePomVersion;
		command = command.replace("{dir}", this.targetDir);
		command = command.replace("{version}", this.version);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	private String generateRenameArtifactScript() {
		String command = NexusDeploymentBuilder.renameArtifact;
		command = command.replace("{dir}", this.targetDir);
		command = command.replace("{artifactId}", this.artifactId);
		command = command.replace("{ext}", this.type);
		command = command.replace("{version}", this.version);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	private String generateMavenDeployScript() {
		String command = NexusDeploymentBuilder.mavenDeployArtifact;
		command = command.replace("{dir}", this.targetDir);
		command = command.replace("{repoUrl}", this.repositoryUrl);
		command = command.replace("{repoId}", this.repositoryId);
		command = command.replace("{artifactId}", this.artifactId);
		command = command.replace("{version}", this.version);
		command = command.replace("{ext}", this.type);
		// This will replace all environment variables with actual values.
		return this.environmentVariables.expand(command);
	}

	private boolean setPomSvnRevision(AbstractBuild<?, ?> build, BuildListener listener) {
		try {
			listener.getLogger().println("Running: Replace POM SVN Revision Number...");
			String pomFile = this.environmentVariables.expand(this.targetDir + "\\pom.xml");
			String svn = this.environmentVariables.expand(this.svnRevision);

			// Create a DOM Document based on our pom.xml.
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db;
			db = dbf.newDocumentBuilder();
			Document doc;
			doc = db.parse(pomFile);
			doc.getDocumentElement().normalize();

			if (doc.getElementsByTagName("properties").getLength() == 0) {
				// Add properties & svnRevsion tags.
				Element props = doc.createElement("properties");
				Element svnRev = doc.createElement("svnRevision");
				svnRev.setTextContent(svn);
				props.appendChild(svnRev);
				doc.getElementsByTagName("project").item(0).appendChild(props);
			} else if ((doc.getElementsByTagName("properties").getLength() == 1) && (doc.getElementsByTagName("svnRevision").getLength() == 0)) {
				// Add svnRevision tag.
				Element svnRev = doc.createElement("svnRevision");
				svnRev.setTextContent(svn);
				doc.getElementsByTagName("properties").item(0).appendChild(svnRev);
			} else if ((doc.getElementsByTagName("properties").getLength() == 1) && (doc.getElementsByTagName("svnRevision").getLength() == 1)) {
				// Set the svnRevision tag.
				doc.getElementsByTagName("svnRevision").item(0).getChildNodes().item(0).setNodeValue(svn);
			} else {
				// log error message. Invalid POM.
				listener.getLogger().println("Artifact's POM.xml file is malformed.  Aborting deployment...");
				throw new Exception("Artifact's POM.xml file is malformed.  Aborting deployment...");
			}

			// Transform the DOM Document into a file.
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(pomFile));
			transformer.transform(source, result);

			listener.getLogger().println("Successful.");

			return true;
		} catch (Exception e) {
			listener.getLogger().println("Failed.");
			e.printStackTrace(listener.fatalError("Unable to write SVN Revision number.  Check POM to ensure the correct tag is being used: [svnRevision]."));
			return false;
		}
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

	public String getGroupId() {
		return groupId;
	}

	public String getVersion() {
		return version;
	}

	public String getType() {
		return type;
	}

	public String getSvnRevision() {
		return svnRevision;
	}

	public String getRepositoryUrl() {
		return repositoryUrl;
	}

	public String getRepositoryId() {
		return repositoryId;
	}

	public String getTargetDir() {
		return targetDir;
	}

	/**
	 * @brief The descriptor inner class is responsible for communicating
	 *        between the jelly configuration files, and the plugin class.
	 */
	// The @Extension annotation registers the outer class as a plugin.
	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {

		public DescriptorImpl() {
			super(NexusDeploymentBuilder.class);
		}

		// This is the name shown on the Hudson project configuration page.
		// [Build Step dropdown menu].
		public String getDisplayName() {
			return "Deploy Artifact to Nexus";
		}

		// Retrieves values from jelly to create a new instance of our builder.
		public NexusDeploymentBuilder newInstance(StaplerRequest req) throws FormException {
			return new NexusDeploymentBuilder(req.getParameter("deploy.targetDir"), req.getParameter("deploy.artifactId"), req.getParameter("deploy.groupId"), req.getParameter("deploy.version"), req.getParameter("deploy.type"), req.getParameter("deploy.svnRevision"), req.getParameter("deploy.repositoryUrl"), req.getParameter("deploy.repositoryId"));
		}

		// This utilizes the @DataBoundConstructor annotation to automatically
		// gather our form data, and call our builder constructor.
		public NexusDeploymentBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return (NexusDeploymentBuilder) req.bindJSON(NexusDeploymentBuilder.class, formData);
		}

	}
}
