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
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class LoadBalanceControlBuilder extends Builder {

	private static final String HEARTBEAT_GIF = "heartbeat.gif";
	private static final String removeHeartBeat = "del \"{webAppRootDirectory}\\" + HEARTBEAT_GIF + "\"";
	private static final String HEARTBEAT_TYPE = "HEARTBEAT";
	private static final String ADD_ACTION = "ADD";
	private static final String REMOVE_ACTION = "REMOVE";

	private final String action;
	private final String type;
	private final String webAppRootDirectory;
	private EnvVars environmentVariables;

	@DataBoundConstructor
	public LoadBalanceControlBuilder(String action, String type, String webAppRootDirectory) {
		this.action = action;
		this.type = type;
		this.webAppRootDirectory = webAppRootDirectory;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {

		Boolean successFlag = false;

		// Get Environmental Variables.
		try {
			this.environmentVariables = build.getEnvironment(listener);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to gather Hudson environment variables."));
			return false;
		}

		/*****************************
		 * HEARTBEAT.GIF
		 *****************************/
		if (HEARTBEAT_TYPE.equals(this.type)) {

			// HEARTBEAT.GIF - Add into Load Balancing.
			if (ADD_ACTION.equals(this.action)) {

				listener.getLogger().println("----------------------------------");
				listener.getLogger().println("ADDING LOAD BALANCING HEARTBEAT");
				listener.getLogger().println("----------------------------------");

				successFlag = this.addHeartBeat(listener);
				
				listener.getLogger().println("----------------------------------");
				listener.getLogger().println("PAUSING FOR 30 SECONDS FOR F5");
				listener.getLogger().println("----------------------------------");
				
				Thread.sleep(30000);
			}

			// HEARTBEAT.GIF - Remove from Load Balancing.
			if (REMOVE_ACTION.equals(this.action)) {

				listener.getLogger().println("----------------------------------");
				listener.getLogger().println("REMOVING LOAD BALANCING HEARTBEAT");
				listener.getLogger().println("----------------------------------");

				BatchFile command = new BatchFile(generateRemoveHeartBeatScript());
				successFlag = command.perform(build, launcher, listener);
				
				listener.getLogger().println("----------------------------------");
				listener.getLogger().println("PAUSING FOR 30 SECONDS FOR F5");
				listener.getLogger().println("----------------------------------");
				
				Thread.sleep(30000);				
			}
		} else {
			listener.fatalError("Unsupported Load Balancing Type.");
			return false;
		}

		return successFlag;

	}

	private String generateRemoveHeartBeatScript() {
		String command = LoadBalanceControlBuilder.removeHeartBeat;
		command = command.replace("{webAppRootDirectory}", this.webAppRootDirectory);

		return this.environmentVariables.expand(command);
	}

	private Boolean addHeartBeat(BuildListener listener) {

		Boolean result = false;
		File file = new File(this.environmentVariables.expand(this.webAppRootDirectory) + "\\" + HEARTBEAT_GIF);
		try {
			this.writeToFile(this.getClass().getClassLoader().getResourceAsStream(HEARTBEAT_GIF), file);
			result = true;
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Unable to write heatbeat.gif"));
			result = false;
		}
		return result;
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

	/**
	 * @return the action
	 */
	public String getAction() {
		return action;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the webAppRootDirectory
	 */
	public String getWebAppRootDirectory() {
		return webAppRootDirectory;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<Builder> {
		public DescriptorImpl() {
			super(LoadBalanceControlBuilder.class);
		}

		public String getDisplayName() {
			return "Load Balancing Control";
		}

		public LoadBalanceControlBuilder newInstance(StaplerRequest req) throws Descriptor.FormException {
			return new LoadBalanceControlBuilder(req.getParameter("action"), req.getParameter("type"), req.getParameter("webAppRootDirectory"));
		}

		public LoadBalanceControlBuilder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return (LoadBalanceControlBuilder) req.bindJSON(LoadBalanceControlBuilder.class, formData);
		}
	}
}