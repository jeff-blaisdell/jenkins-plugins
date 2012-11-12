package com.jostens.hudson.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;

/**
 * @author blaisj1
 * @brief Hudson build parameter plugin to provide a dropdown of available Nexus
 *        artifacts.
 */
public class NexusVersionDropdownParameterDefinition extends SimpleParameterDefinition {

	private static final long serialVersionUID = -725862645435454408L;
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	private final String groupId;
	private final String artifactId;
	private final String repoIds;
	private final String url;
	private final String maxVersions;
	private List<String> versions;

	@DataBoundConstructor
	public NexusVersionDropdownParameterDefinition(String name, String description, String groupId, String artifactId, String repoIds, String url, String maxVersions) {
		super(name, description);
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.repoIds = repoIds;
		this.url = url;
		this.maxVersions = maxVersions;
		versions = new ArrayList<String>();
		this.refresh();
	}

	private boolean isInt(String value) {
		boolean isInteger = false;

		try {
			Integer.parseInt(value);
			isInteger = true;
		} catch (NumberFormatException e) {
			isInteger = false;
		}

		return isInteger;
	}

	// Method responsible for building the dropdown list.
	public void refresh() {

		// Initialize the repository map.
		String[] repositoriesArray = this.repoIds.split(",\\s*");
		if(repositoriesArray.length == 0) repositoriesArray[0] = "snapshots";
		HashMap<String, String> repos = new HashMap<String, String>();
		for (String repo : repositoriesArray) {
			repos.put(repo, repo);
		}

		String query = "/service/local/data_index?q=" + this.artifactId;
		Stack<String> versionStack = new Stack<String>();
		List<String> versionsList = new ArrayList<String>();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db;
			db = dbf.newDocumentBuilder();
			Document doc;
			doc = db.parse(this.url + query);

			doc.getDocumentElement().normalize();

			NodeList artifacts = doc.getElementsByTagName("artifact");

			if (artifacts != null && artifacts.getLength() > 0) {
				for (int i = 0; i < artifacts.getLength(); i++) {

					Element artifact = (Element) artifacts.item(i);

					String repo = artifact.getElementsByTagName("repoId").item(0).getChildNodes().item(0).getNodeValue();
					String group = artifact.getElementsByTagName("groupId").item(0).getChildNodes().item(0).getNodeValue();
					String artId = artifact.getElementsByTagName("artifactId").item(0).getChildNodes().item(0).getNodeValue();
					if (repos.containsKey(repo) && groupId.equals(group) && artifactId.equals(artId)) {
						String version = artifact.getElementsByTagName("version").item(0).getChildNodes().item(0).getNodeValue();
						versionStack.push(version);
					}
				}
			}

			Integer max = (isInt(this.maxVersions) ? Integer.parseInt(this.maxVersions) : null);
			int i = 0;
			while (!versionStack.isEmpty()) {
				if (max != null && i >= max) {
					break;
				}
				versionsList.add(versionStack.pop());
				i++;
			}

			this.setVersions(versionsList);

		} catch (ParserConfigurationException e) {
			throw new DOMException((short) 0, "Unable to create a javax.xml.parsers.DocumentBuilder instance.");
		} catch (SAXException e) {
			throw new RuntimeException("Unable to Parse Nexus XML response.");
		} catch (IOException e) {
			throw new RuntimeException("Unable to process IO for the Nexus XML response.");
		}
	}

	public List<String> getVersions() {
		return versions;
	}

	public void setVersions(List<String> versions) {
		this.versions = versions;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getRepoIds() {
		return repoIds;
	}

	public String getUrl() {
		return url;
	}

	public String getMaxVersions() {
		return maxVersions;
	}

	private void checkValue(StringParameterValue value) {
		if (!versions.contains(value.value))
			throw new IllegalArgumentException("Illegal choice: " + value.value);
	}

	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		StringParameterValue value = req.bindJSON(NexusVersionDropdownParameterValue.class, jo);
		value.setDescription(getDescription());
		this.checkValue(value);
		return value;
	}

	public NexusVersionDropdownParameterValue createValue(String value) {
		NexusVersionDropdownParameterValue parameterValue = new NexusVersionDropdownParameterValue(getName(), value, getDescription());
		checkValue(parameterValue);
		return parameterValue;
	}

	/**
	 * @brief The descriptor inner class is responsible for communicating
	 *        between the jelly configuration files, and the plugin class.
	 */
	// The @Extension annotation registers the outer class as a plugin.
	@Extension
	public static final class DescriptorImpl extends ParameterDescriptor {

		public DescriptorImpl() {
			super(NexusVersionDropdownParameterDefinition.class);
		}

		// This is the name shown on the Hudson project configuration page.
		// [Build Parameter dropdown menu].
		@Override
		public String getDisplayName() {
			return "Nexus Version Dropdown";
		}

		// Retrieves values from jelly to create a new instance of our builder.
		public NexusVersionDropdownParameterDefinition newInstance(StaplerRequest req) throws FormException {
			return new NexusVersionDropdownParameterDefinition(req.getParameter("nexus_dropdown.name"), req.getParameter("nexus_dropdown.description"), req.getParameter("nexus_dropdown.groupId"), req.getParameter("nexus_dropdown.artifactId"), req.getParameter("nexus_dropdown.repoIds"), req.getParameter("nexus_dropdown.url"), req.getParameter("nexus_dropdown.maxVersions"));
		}

		// This utilizes the @DataBoundConstructor annotation to automatically
		// gather our form data, and call our builder constructor.
		public NexusVersionDropdownParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return (NexusVersionDropdownParameterDefinition) req.bindJSON(NexusVersionDropdownParameterDefinition.class, formData);
		}

	}

}
