package com.jostens.hudson.plugins;

import java.io.File;
import java.util.SortedSet;
import java.util.TreeSet;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;

/**
 * @author dekarsb
 * @brief Hudson build parameter plugin to provide a dropdown of available directories on a staging server.
 */
public class StagingDirectoryDropdownParameterDefinition extends SimpleParameterDefinition {
	
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	private final String stagingPath;
	private final String maxVersions;
	private SortedSet<String> tags;

	@DataBoundConstructor
	public StagingDirectoryDropdownParameterDefinition(String name, String description, String stagingPath, String maxVersions) {
		super(name, description);
		this.stagingPath = stagingPath;
		this.maxVersions = maxVersions;
		tags = new TreeSet<String>();
		this.refresh();
	}

	// Method responsible for building the dropdown list.
	public void refresh() {
		File stagingDir = new File(stagingPath);
		if (!stagingDir.isDirectory() || !stagingDir.canRead()) {
			throw new RuntimeException("Invalid staging path or no access");
		}

		File[] tagDirs = stagingDir.listFiles();
		if (tagDirs == null || tagDirs.length == 0) {
			throw new RuntimeException("No subdirectories found at given path");
		}

		Integer max = null;
		try {
			max = Integer.valueOf(Integer.parseInt(maxVersions));
		} catch (NumberFormatException e) {
			max = null;
		}

		for (File tagDir : tagDirs) {
			if (max != null && tags.size() > max.intValue()) {
				break;
			}
			if (tagDir.isDirectory()) {
				tags.add(tagDir.getName());
			}
		}
	}

	public SortedSet<String> getTags() {
		return tags;
	}

	public void setTags(SortedSet<String> tags) {
		this.tags = tags;
	}

	public String getStagingPath() {
		return stagingPath;
	}

	public String getMaxVersions() {
		return maxVersions;
	}

	private void checkValue(StringParameterValue value) {
		if (!tags.contains(value.value)) {
			throw new IllegalArgumentException("Illegal choice: " + value.value);
		}
	}

	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
		value.setDescription(getDescription());
		this.checkValue(value);
		return value;
	}

	public StringParameterValue createValue(String value) {
		StringParameterValue parameterValue = new StringParameterValue(getName(), value, getDescription());
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
			super(StagingDirectoryDropdownParameterDefinition.class);
		}

		// This is the name shown on the Hudson project configuration page.
		// [Build Parameter dropdown menu].		
		@Override
		public String getDisplayName() {
			return "Staging Directory Dropdown";
		}

		// Retrieves values from jelly to create a new instance of our builder.
		public StagingDirectoryDropdownParameterDefinition newInstance(StaplerRequest req) throws FormException {
			return new StagingDirectoryDropdownParameterDefinition(req.getParameter("staging_dir.name"), req.getParameter("staging_dir.description"), req.getParameter("staging_dir.stagingPath"), req.getParameter("staging_dir.maxVersions"));
		}

		// This utilizes the @DataBoundConstructor annotation to automatically
		// gather our form data, and call our builder constructor.		
		public StagingDirectoryDropdownParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return (StagingDirectoryDropdownParameterDefinition) req.bindJSON(StagingDirectoryDropdownParameterDefinition.class, formData);
		}
	}
}
