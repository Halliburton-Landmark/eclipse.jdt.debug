package org.eclipse.jdt.internal.launching;/**********************************************************************Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.This file is made available under the terms of the Common Public License v1.0which accompanies this distribution, and is available athttp://www.eclipse.org/legal/cpl-v10.html**********************************************************************/import java.io.File;import java.io.IOException;import java.util.ArrayList;import java.util.List;import org.eclipse.core.runtime.IPath;import org.eclipse.core.runtime.IStatus;import org.eclipse.core.runtime.Path;import org.eclipse.core.runtime.Status;import org.eclipse.debug.core.DebugPlugin;import org.eclipse.debug.core.ILaunchManager;import org.eclipse.debug.core.Launch;import org.eclipse.debug.core.model.IProcess;import org.eclipse.jdt.launching.AbstractVMInstallType;import org.eclipse.jdt.launching.IVMInstall;import org.eclipse.jdt.launching.LibraryLocation;/** * A VM install type for VMs the conform to the standard * JDK installion layout. */public class StandardVMType extends AbstractVMInstallType {		/**	 * The root path for the attached src	 */	private String fDefaultRootPath;		/**	 * The libraries that comprise the standard classes for IBM 1.4 VMs.  The order	 * is significant.	 */	private final static String[] fgIBM14LibraryNames = {"core.jar",  //$NON-NLS-1$															"graphics.jar",  //$NON-NLS-1$															"security.jar",  //$NON-NLS-1$															"server.jar",  //$NON-NLS-1$															"xml.jar",  //$NON-NLS-1$															"charsets.jar"}; //$NON-NLS-1$		/**	 * Convenience handle to the system-specific file separator character	 */																private static final char fgSeparator = File.separatorChar;	/**	 * The list of locations in which to look for the java executable in candidate	 * VM install locations, relative to the VM install location.	 */	private static final String[] fgCandidateJavaLocations = {							"bin" + fgSeparator + "javaw",                                //$NON-NLS-2$ //$NON-NLS-1$							"bin" + fgSeparator + "javaw.exe",                            //$NON-NLS-2$ //$NON-NLS-1$							"jre" + fgSeparator + "bin" + fgSeparator + "javaw",          //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$							"jre" + fgSeparator + "bin" + fgSeparator + "javaw.exe",      //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$							"bin" + fgSeparator + "java",                                 //$NON-NLS-2$ //$NON-NLS-1$							"bin" + fgSeparator + "java.exe",                             //$NON-NLS-2$ //$NON-NLS-1$							"jre" + fgSeparator + "bin" + fgSeparator + "java",           //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$							"jre" + fgSeparator + "bin" + fgSeparator + "java.exe"};      //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$		/**	 * Starting in the specified VM install location, attempt to find the 'java' executable	 * file.  If found, return the corresponding <code>File</code> object, otherwise return	 * <code>null</code>.	 */	protected File findJavaExecutable(File vmInstallLocation) {				// Try each candidate in order.  The first one found wins.  Thus, the order		// of fgCandidateJavaLocations is significant.		for (int i = 0; i < fgCandidateJavaLocations.length; i++) {			File javaFile = new File(vmInstallLocation, fgCandidateJavaLocations[i]);			if (javaFile.isFile()) {				return javaFile;			}		}				return null;								}		/**	 * @see IVMInstallType#getName()	 */	public String getName() {		return LaunchingMessages.getString("StandardVMType.Standard_VM_3"); //$NON-NLS-1$	}		protected IVMInstall doCreateVMInstall(String id) {		return new StandardVM(this, id);	}		/**	 * Return Java version information corresponding to the specified Java executable.	 */	protected JavaVersionInfo getJavaVersion(File javaHome, File javaExecutable) {				// See if we already know the version info for the requested VM.  If not, generate it.		String installPath = javaHome.getAbsolutePath();		JavaVersionInfo versionResults = (JavaVersionInfo) LaunchingPlugin.getJavaVersionInfo(installPath);		if (versionResults == null) {			versionResults = generateJavaVersionResults(javaExecutable);			LaunchingPlugin.setJavaVersionInfo(installPath, versionResults);		} 		return versionResults;	}			/**	 * Invoke the specified Java executable with the '-version' option, parse the results	 * and return them in a JavaVersionInfo object.	 */		protected JavaVersionInfo generateJavaVersionResults(File javaExecutable) {		JavaVersionInfo versionResults = null;		String javaExecutablePath = javaExecutable.getAbsolutePath();		String[] cmdLine = new String[] {javaExecutablePath, "-version"};  //$NON-NLS-1$		Process p = null;		try {			p = Runtime.getRuntime().exec(cmdLine);			IProcess process = DebugPlugin.newProcess(new Launch(null, ILaunchManager.RUN_MODE, null), p, "Java -version");			while (!process.isTerminated()) {				// wait for process to terminate			}						versionResults= determineVersion(process);		} catch (IOException ioe) {			LaunchingPlugin.log(ioe);		} finally {			if (p != null) {				p.destroy();			}		}							if (versionResults == null) {			versionResults = JavaVersionInfo.getEmptyJavaVersionInfo();		}		return versionResults;	}	/**	 * Returns the version information polled from the 	 * error and output streams of the specified process	 * or <code>null</code> if no version information 	 * is found.	 */	protected JavaVersionInfo determineVersion(IProcess process) throws IOException {		// Some VMs put their '-version' output on stderr, some put it on stdout		JavaVersionInfo versionResults;		String text = process.getStreamsProxy().getErrorStreamMonitor().getContents();		if (text != null && text.length() > 0) {			versionResults= parseJavaVersionOutput(text);			if (versionResults != null) {				return versionResults;			}		}		text = process.getStreamsProxy().getOutputStreamMonitor().getContents();		if (text != null && text.length() > 0) {			versionResults= parseJavaVersionOutput(text);			if (versionResults != null) {				return versionResults;			}		} 		return null;	}		/**	 * Parse the output of a 'java -version' command residing in the specified InputStream	 * and put the results in the specified JavaVersionInfo object.	 */	protected JavaVersionInfo parseJavaVersionOutput(String text) {		int index = text.indexOf("java version");    //$NON-NLS-1$		String javaVersion = "";    //$NON-NLS-1$		if (index >= 0) { 			javaVersion = parseVersionNumber(text, index);		} 		return new JavaVersionInfo(javaVersion, ibmDetected(text));	}		protected String parseVersionNumber(String string, int pos) {		int firstQuote = string.indexOf('"', pos);		int lastQuote = string.indexOf('"', firstQuote + 1);		if (firstQuote > -1 && lastQuote > -1 && firstQuote != lastQuote) {			return string.substring(firstQuote + 1, lastQuote);		}		return "";          //$NON-NLS-1$	}		protected boolean ibmDetected(String string) {		int ibmIndex = string.indexOf("IBM");   //$NON-NLS-1$		if (ibmIndex != -1) {			return true;		}		return false;	}		/**	 * Return <code>true</code> if the appropriate system libraries can be found for the	 * specified java executable, <code>false</code> otherwise.	 */	protected boolean canDetectDefaultSystemLibraries(File javaHome, File javaExecutable) {		JavaVersionInfo versionResults = getJavaVersion(javaHome, javaExecutable);		if (versionResults != null) {			return canDetectDefaultSystemLibraries(javaHome, versionResults);		}		return false;			}		/**	 * Return <code>true</code> if the system libraries appropriate to the specified java version	 * results can be found underneath the specified directory, <code>false</code> otherwise.	 */	protected boolean canDetectDefaultSystemLibraries(File javaHome, JavaVersionInfo versionResults) {		if (versionResults.getVersionString().startsWith("1.1")) {  //$NON-NLS-1$			return false;		}				if (versionResults.ibm14Found()) { 			return canDetectIBM14SystemLibraries(javaHome);		} else {			return canDetectSystemLibraries(javaHome);		}	}		protected boolean canDetectSystemLibraries(File javaHome) {		IPath path = getDefaultSystemLibrary(javaHome);		if (path.toFile().exists()) {			return true;		}		return false;	}		protected boolean canDetectIBM14SystemLibraries(File javaHome) {		IPath[] paths = getDefaultIBM14SystemLibraries(javaHome);		for (int i = 0; i < paths.length; i++) {			if (!paths[i].toFile().exists()) {				return false;			}		}		return true;	}			/**	 * @see IVMInstallType#detectInstallLocation()	 */	public File detectInstallLocation() {		File javaHome= new File (System.getProperty("java.home")); //$NON-NLS-1$		if (!javaHome.exists()) {			return null;		}		File javaExecutable = findJavaExecutable(javaHome);		if (javaExecutable == null) {			return null;		}				if (!canDetectDefaultSystemLibraries(javaHome, javaExecutable)) {			return null;		}					if (javaHome.getName().equalsIgnoreCase("jre")) { //$NON-NLS-1$			// Some JDKs with the following structure:			//    jdkx.x/jre/bin/java			//             /bin/java			// report "jdkx.x/jre" as java.home. However, we want the top-level			// "jdkx.x" directory.			File parent= new File(javaHome.getParent());			if (javaExecutable != null) {				javaHome= parent;			}			if (!canDetectDefaultSystemLibraries(javaHome, javaExecutable)) {				return null;			}		}			if ("J9".equals(System.getProperty("java.vm.name"))) {//$NON-NLS-2$ //$NON-NLS-1$			return null;		}		return javaHome;	}	/**	 * Return an <code>IPath</code> corresponding to the single library file containing the	 * standard Java classes for most VMs version 1.2 and above.	 */	protected IPath getDefaultSystemLibrary(File javaHome) {		IPath jreLibPath= new Path(javaHome.getPath()).append("lib").append("rt.jar"); //$NON-NLS-2$ //$NON-NLS-1$		if (jreLibPath.toFile().isFile()) {			return jreLibPath;		}		return new Path(javaHome.getPath()).append("jre").append("lib").append("rt.jar"); //$NON-NLS-3$ //$NON-NLS-2$ //$NON-NLS-1$	}		/**	 * Return an array of <code>IPath</code> objects corresponding to the library files	 * containing the standard Java classes for IBM VMs version 1.4 and above.	 */	protected IPath[] getDefaultIBM14SystemLibraries(File javaHome) {		IPath[] paths = new Path[fgIBM14LibraryNames.length];		for (int i = 0; i < fgIBM14LibraryNames.length; i++) {			paths[i] = new Path(javaHome.getPath()).append("jre").append("lib").append(fgIBM14LibraryNames[i]);  //$NON-NLS-2$ //$NON-NLS-1$		}				return paths;	}	protected IPath getDefaultSystemLibrarySource(File installLocation) {		File parent= installLocation.getParentFile();		if (parent != null) {			File parentsrc= new File(parent, "src.jar"); //$NON-NLS-1$			if (parentsrc.isFile()) {				setDefaultRootPath("src");//$NON-NLS-1$				return new Path(parentsrc.getPath());			}			parentsrc= new File(parent, "src.zip"); //$NON-NLS-1$			if (parentsrc.isFile()) {				setDefaultRootPath(""); //$NON-NLS-1$				return new Path(parentsrc.getPath());			}			parentsrc= new File(installLocation, "src.jar"); //$NON-NLS-1$			if (parentsrc.isFile()) {				setDefaultRootPath("src"); //$NON-NLS-1$				return new Path(parentsrc.getPath());			}						parentsrc= new File(installLocation, "src.zip"); //$NON-NLS-1$			if (parentsrc.isFile()) {				setDefaultRootPath(""); //$NON-NLS-1$				return new Path(parentsrc.getPath());			}					}		setDefaultRootPath(""); //$NON-NLS-1$		return Path.EMPTY; //$NON-NLS-1$	}	protected IPath getDefaultPackageRootPath() {		return new Path(getDefaultRootPath());	}	/**	 * @see IVMInstallType#getDefaultSystemLibraryDescription(File)	 */	public LibraryLocation[] getDefaultLibraryLocations(File installLocation) {		// Determine the java executable that corresponds to the specified install location		// and use this to generate java version info.  If no java executable was found, 		// just create empty results.  This will cause the 'standard' libraries to be		// returned.		File javaExecutable = findJavaExecutable(installLocation);		JavaVersionInfo versionResults;		if (javaExecutable != null) {			versionResults = getJavaVersion(installLocation, javaExecutable);		} else {			versionResults = JavaVersionInfo.getEmptyJavaVersionInfo();		}						// For IBM 1.4 and above VMs, get their particular libraries.  For all other VMs,		// just get the single standard library		LibraryLocation[] libs = null;		if (versionResults.ibm14Found()) {			IPath[] paths = getDefaultIBM14SystemLibraries(installLocation);						libs = new LibraryLocation[paths.length];			for (int i = 0; i < paths.length; i++) {				libs[i] = new LibraryLocation(paths[i],										  getDefaultSystemLibrarySource(installLocation),										  getDefaultPackageRootPath());											  			}		} else {			libs = new LibraryLocation[1];			libs[0] = new LibraryLocation(getDefaultSystemLibrary(installLocation),										  getDefaultSystemLibrarySource(installLocation),										  getDefaultPackageRootPath());		}						// Determine all extension directories and add them to the standard libraries		List extensions = getExtensionLibraries(installLocation);		LibraryLocation[] allLibs = new LibraryLocation[libs.length + extensions.size()];		System.arraycopy(libs, 0, allLibs, 0, libs.length);				for (int i = 0; i < extensions.size(); i++) {			allLibs[i + libs.length] = (LibraryLocation)extensions.get(i);		}		return allLibs;	}		/**	 * Returns a list of default extension jars that should be placed on the build	 * path and runtime classpath, by default.	 * 	 * @param installLocation 	 * @return List	 */	protected List getExtensionLibraries(File installLocation) {		File extDir = getDefaultExtensionDirectory(installLocation);		List extensions = new ArrayList();		if (extDir != null && extDir.exists() && extDir.isDirectory()) {			String[] names = extDir.list();			for (int i = 0; i < names.length; i++) {				String name = names[i];				File jar = new File(extDir, name);				if (jar.isFile()) {					int length = name.length();					if (length > 4) {						String suffix = name.substring(length - 4);						if (suffix.equalsIgnoreCase(".zip") || suffix.equalsIgnoreCase(".jar")) { //$NON-NLS-1$ //$NON-NLS-2$							try {								IPath libPath = new Path(jar.getCanonicalPath());								LibraryLocation library = new LibraryLocation(libPath, Path.EMPTY, Path.EMPTY);								extensions.add(library);							} catch (IOException e) {								LaunchingPlugin.log(e);							}						}					}				}			}		}		return extensions;	}		/**	 * Returns the default location of the extension directory, based on the given	 * install location. The resulting file may not exist, or be <code>null</code>	 * if an extension directory is not supported.	 * 	 * @param installLocation 	 * @return default extension directory or <code>null</code>	 */	protected File getDefaultExtensionDirectory(File installLocation) {		File jre = null;		if (installLocation.getName().equalsIgnoreCase("jre")) { //$NON-NLS-1$			jre = installLocation;		} else {			jre = new File(installLocation, "jre"); //$NON-NLS-1$		}		File lib = new File(jre, "lib"); //$NON-NLS-1$		File ext = new File(lib, "ext"); //$NON-NLS-1$		return ext;	}	protected String getDefaultRootPath() {		return fDefaultRootPath;	}	protected void setDefaultRootPath(String defaultRootPath) {		fDefaultRootPath = defaultRootPath;	}		/**	 * @see org.eclipse.jdt.launching.IVMInstallType#validateInstallLocation(java.io.File)	 */	public IStatus validateInstallLocation(File javaHome) {		IStatus status = null;		File javaExecutable = findJavaExecutable(javaHome);		if (javaExecutable == null) {			status = new Status(IStatus.ERROR, LaunchingPlugin.getUniqueIdentifier(), 0, LaunchingMessages.getString("StandardVMType.Not_a_JDK_Root;_Java_executable_was_not_found_1"), null); //$NON-NLS-1$					} else {			if (canDetectDefaultSystemLibraries(javaHome, javaExecutable)) {				status = new Status(IStatus.OK, LaunchingPlugin.getUniqueIdentifier(), 0, LaunchingMessages.getString("StandardVMType.ok_2"), null); //$NON-NLS-1$			} else {				status = new Status(IStatus.ERROR, LaunchingPlugin.getUniqueIdentifier(), 0, LaunchingMessages.getString("StandardVMType.Not_a_JDK_root._System_library_was_not_found._1"), null); //$NON-NLS-1$			}		}		return status;			}	}