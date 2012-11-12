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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class WindowsServiceControlBuilder extends Builder {

	private static final String executeWindowsService = "sc {server} {action} {service}";
	private static final String queryWindowsService = "sc {server} query {service} | FIND \"STATE\" | FIND \"{state}\"";
	private static final String ACTION_START = "start";
	private static final String ACTION_STOP = "stop";
	private static final String STATE_RUNNING = "RUNNING";
	private static final String STATE_STOPPED = "STOPPED";

	private final String server;
	private final String serviceName;
	private final String action;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public WindowsServiceControlBuilder(String server, String serviceName, String action) {
		this.server = server;
		this.serviceName = serviceName;
		this.action = action;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
		boolean successFlag = false;
		String state = "";

		// Get HUDSON Environment Variables.
		try {
			this.environmentVariables = build.getEnvironment(listener);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to gather Hudson environment variables."));
			return false;
		}

		// Determine whether we should START / STOP the Windows Service.
		if (WindowsServiceControlBuilder.ACTION_START.equals(this.action)) {
			state = WindowsServiceControlBuilder.STATE_RUNNING;
		} else {
			state = WindowsServiceControlBuilder.STATE_STOPPED;
		}

		// Create the Windows Scripts.
		BatchFile serviceControlScript = new BatchFile(generateWindowsServiceScript("\\\\" + server, this.action, this.serviceName));
		BatchFile serviceQueryScript = new BatchFile(generateWindowsServiceQueryScript("\\\\" + server, this.serviceName, state));

		// Start or Stop Windows Service.
		successFlag = serviceControlScript.perform(build, launcher, listener);

		// If successful, query for completion.
		if (successFlag) {
			while (!serviceQueryScript.perform(build, launcher, listener)) {
				Thread.sleep(15000);
				listener.getLogger().println("STILL WAITING FOR WINDOWS SERVICE ACTION TO COMPLETE...");
			}
		} else {
			return successFlag;
		}

		return successFlag;
	}

	private String generateWindowsServiceScript(String server, String action, String serviceName) {
		String command = WindowsServiceControlBuilder.executeWindowsService;
		command = command.replace("{server}", server);
		command = command.replace("{action}", action);
		command = command.replace("{service}", serviceName);

		return this.environmentVariables.expand(command);
	}

	private String generateWindowsServiceQueryScript(String server, String serviceName, String state) {
		String command = WindowsServiceControlBuilder.queryWindowsService;
		command = command.replace("{server}", server);
		command = command.replace("{service}", serviceName);
		command = command.replace("{state}", state);

		return this.environmentVariables.expand(command);
	}

	public EnvVars getEnvironmentVariables() {
		return this.environmentVariables;
	}

	public void setEnvironmentVariables(EnvVars environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	public String getServer() {
		return this.server;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public String getAction() {
		return action;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public DescriptorImpl() {
			super(WindowsServiceControlBuilder.class);
		}

		public String getDisplayName() {
			return "Windows Service Control";
		}

		public WindowsServiceControlBuilder newInstance(StaplerRequest req) throws Descriptor.FormException {
			return new WindowsServiceControlBuilder(req.getParameter("attr.server"), req.getParameter("attr.serviceName"), req.getParameter("attr.action"));
		}

		public WindowsServiceControlBuilder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return (WindowsServiceControlBuilder) req.bindJSON(WindowsServiceControlBuilder.class, formData);
		}
	}
}