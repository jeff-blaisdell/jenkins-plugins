package com.jostens.hudson.plugins;

import java.util.Locale;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.StringParameterValue;

public class NexusVersionDropdownParameterValue extends StringParameterValue {

	private static final long serialVersionUID = -7988089343781282522L;
	private static final String MAJOR_VERSION = "major_version";
	private static final String MINOR_VERSION = "minor_version";
	private static final String INCREMENTAL_VERSION = "incremental_version";
	private static final String QUALIFIER = "qualifier";
	private static final String BUILD_NUMBER = "build_number";

	@DataBoundConstructor
	public NexusVersionDropdownParameterValue(String name, String value) {
		this(name, value, null);
	}

	public NexusVersionDropdownParameterValue(String name, String value, String description) {
		super(name, value, description);
	}

	/**
	 * Exposes the name/value as an environment variable.
	 */
	@Override
	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.put(name, value);
		env.put(name.toUpperCase(Locale.ENGLISH), value);

		try {
			Artifact artifact = new Artifact(value);
			if (artifact != null) {
				env.put(MAJOR_VERSION, artifact.getMajorVersion());
				env.put(MAJOR_VERSION.toUpperCase(Locale.ENGLISH), artifact.getMajorVersion());

				env.put(MINOR_VERSION, artifact.getMinorVersion());
				env.put(MINOR_VERSION.toUpperCase(Locale.ENGLISH), artifact.getMinorVersion());

				env.put(INCREMENTAL_VERSION, artifact.getIncrementalVersion());
				env.put(INCREMENTAL_VERSION.toUpperCase(Locale.ENGLISH), artifact.getIncrementalVersion());

				env.put(QUALIFIER, artifact.getQualifier());
				env.put(QUALIFIER.toUpperCase(Locale.ENGLISH), artifact.getQualifier());

				env.put(BUILD_NUMBER, artifact.getBuildNumber());
				env.put(BUILD_NUMBER.toUpperCase(Locale.ENGLISH), artifact.getBuildNumber());
			}
		} catch (Exception e) {
			// Suppress Exception.
		}
	}

	private class Artifact {

		private String qualifier;
		private String buildNumber;
		private String majorVersion;
		private String minorVersion;
		private String incrementalVersion;
		private static final String SNAPSHOT = "SNAPSHOT";
		private static final String RELEASE_CANDIDATE = "RC";
		private static final String RELEASE = "RELEASE";

		public Artifact(String artifactVersion) {
			parseArtifactVersion(artifactVersion);
		}

		private void parseArtifactVersion(String name) {
			try {
				String[] t1 = name.split("-");

				String version = "";
				String qualifier = "";
				String buildNumber = "";
				String majorVersion = "";
				String minorVersion = "";
				String incrementalVersion = "";

				// Parse Version, Qualifier, and Build Number.
				/**
				 * Parse Rules:
				 * VERSION:      Is a collection of up to three numbers separated by a '.'.  It should always come first in string.
				 *               The VERSION is either followed by a QUALIFIER, a BUILD NUMBER, or both.
				 * QUALIFIER:    Is always a String. 
				 * BUILD NUMBER: Is always an Integer.
				 */
				if (t1.length == 1) {
					version = t1[0];
				} else if (t1.length == 2) {
					version = t1[0];
					if (isNumber(t1[1])) {
						buildNumber = t1[1];
					} else {
						if (!isRepositoryQualifier(t1[1])) {
							qualifier = t1[1];
						}
					}
				} else if ((t1.length == 3) || (t1.length == 4)) {
					version = t1[0];
					if (isNumber(t1[1])) {
						buildNumber = t1[1];
					} else {
						if (!isRepositoryQualifier(t1[1])) {
							qualifier = t1[1];
						}
					}
					if (isNumber(t1[2])) {
						buildNumber = t1[2];
					} else {
						if (!isRepositoryQualifier(t1[2])) {
							qualifier = t1[2];
						}
					}
				}

				// Parse Major, Minor, and Incremental version numbers.
				if (version != null) {
					String[] t2 = version.split("\\.");
					if (t2.length == 1) {
						majorVersion = t2[0];
					} else if (t2.length == 2) {
						majorVersion = t2[0];
						minorVersion = t2[1];
					} else if (t2.length == 3) {
						majorVersion = t2[0];
						minorVersion = t2[1];
						incrementalVersion = t2[2];
					}
				}

				setMajorVersion(majorVersion);
				setMinorVersion(minorVersion);
				setIncrementalVersion(incrementalVersion);
				setBuildNumber(buildNumber);
				setQualifier(qualifier);

			} catch (Exception e) {
				// Suppress Exceptions.
			}
		}

		public boolean isNumber(String p) {
			try {
				Integer.parseInt(p);
				return true;
			} catch (NumberFormatException e) {
				return false;
			}
		}

		public boolean isRepositoryQualifier(String p) {
			if (SNAPSHOT.equals(p) || RELEASE_CANDIDATE.equals(p) || RELEASE.equals(p)) {
				return true;
			} else {
				return false;
			}
		}

		public String getQualifier() {
			return qualifier;
		}

		public void setQualifier(String qualifier) {
			this.qualifier = qualifier;
		}

		public String getBuildNumber() {
			return buildNumber;
		}

		public void setBuildNumber(String buildNumber) {
			this.buildNumber = buildNumber;
		}

		public String getMajorVersion() {
			return majorVersion;
		}

		public void setMajorVersion(String majorVersion) {
			this.majorVersion = majorVersion;
		}

		public String getMinorVersion() {
			return minorVersion;
		}

		public void setMinorVersion(String minorVersion) {
			this.minorVersion = minorVersion;
		}

		public String getIncrementalVersion() {
			return incrementalVersion;
		}

		public void setIncrementalVersion(String incrementalVersion) {
			this.incrementalVersion = incrementalVersion;
		}

	}

}
