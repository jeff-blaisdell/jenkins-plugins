package com.jostens.hudson.plugins;

import java.util.Collection;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;

/**
 * @author blaisj1
 * @brief Hudson build parameter plugin to provide a dropdown of available SVN
 *        branches.
 */
public class SvnBranchDropdownParameterDefinition extends SimpleParameterDefinition {

	private static final long serialVersionUID = -725862645435454408L;
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	private final String svnUrl;
	private final String username;
	private final String password;
	private final String maxVersions;
	private final String subDirectories;
	private SortedSet<String> branches;

	@DataBoundConstructor
	public SvnBranchDropdownParameterDefinition(String name, String description, String svnUrl, String subDirectories, String username, String password, String maxVersions) {
		super(name, description);
		this.svnUrl = svnUrl;
		this.username = username;
		this.password = password;
		this.maxVersions = maxVersions;
		this.subDirectories = subDirectories;
		branches = new TreeSet<String>();
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

		Stack<String> versionStack = new Stack<String>();
		SortedSet<String> versions = new TreeSet<String>();

		try {
			DAVRepositoryFactory.setup();
			SVNRepositoryFactoryImpl.setup();

			SVNURL svnURL = SVNURL.parseURIDecoded(this.svnUrl);
			ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(this.username, this.password);
			SVNRepository repo = SVNRepositoryFactory.create(svnURL);
			repo.setAuthenticationManager(authManager);

			Collection<SVNDirEntry> directories = new TreeSet<SVNDirEntry>();
			String[] subDirs = this.subDirectories.replace("/", "/").replace("\\", "/").split(",");
			if (StringUtils.isEmpty(subDirs[0])) {
				subDirs[0] = "branches";
			}
			
			for (String subdir : subDirs) {
				repo.getDir("./" + subdir, SVNRevision.HEAD.getNumber(), null, directories);
				if (directories != null && directories.size() > 0) {
					for (SVNDirEntry dir : directories) {
						versionStack.push(subdir + "/" + dir.getName());
					}
					directories.clear();
				}
				
			}
			
			versionStack.push("trunk");
			Integer max = (isInt(this.maxVersions) ? Integer.parseInt(this.maxVersions) : null);
			int i = 0;
			while (!versionStack.isEmpty()) {
				if (max != null && i >= max) {
					break;
				}
				versions.add(versionStack.pop());
				i++;
			}
			this.setBranches(versions);
		} catch (SVNException e) {
			throw new RuntimeException("An exception occurred while contacting SVN repository.");
		}
	}

	public SortedSet<String> getBranches() {
		return branches;
	}

	public void setBranches(SortedSet<String> branches) {
		this.branches = branches;
	}

	public String getSvnUrl() {
		return svnUrl;
	}

	public String getSubDirectories() {
		return subDirectories;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getMaxVersions() {
		return maxVersions;
	}

	private void checkValue(StringParameterValue value) {
		if (!branches.contains(value.value))
			throw new IllegalArgumentException("Illegal choice: " + value.value);
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

	@Override
	public ParameterValue getDefaultParameterValue() {
		StringParameterValue parameterValue = new StringParameterValue(getName(), "trunk", getDescription());
		this.checkValue(parameterValue);
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
			super(SvnBranchDropdownParameterDefinition.class);
		}

		// This is the name shown on the Hudson project configuration page.
		// [Build Parameter dropdown menu].		
		@Override
		public String getDisplayName() {
			return "SVN Branch Dropdown";
		}

		// Retrieves values from jelly to create a new instance of our builder.
		public SvnBranchDropdownParameterDefinition newInstance(StaplerRequest req) throws FormException {
			return new SvnBranchDropdownParameterDefinition(req.getParameter("svn_dropdown.name"), req.getParameter("svn_dropdown.description"), req.getParameter("svn_dropdown.svnUrl"), req.getParameter("svn_dropdown.subDirectories"), req.getParameter("svn_dropdown.username"), req.getParameter("svn_dropdown.password"), req.getParameter("svn_dropdown.maxVersions"));
		}

		// This utilizes the @DataBoundConstructor annotation to automatically
		// gather our form data, and call our builder constructor.		
		public SvnBranchDropdownParameterDefinition newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return (SvnBranchDropdownParameterDefinition) req.bindJSON(SvnBranchDropdownParameterDefinition.class, formData);
		}

	}
}
