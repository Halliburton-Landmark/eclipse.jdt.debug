package org.eclipse.jdt.internal.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.JDIDebugModel;

/**
 * The plugin class for the JDI Debug Model plugin.
 */

public class JDIDebugPlugin extends Plugin {
	
	/**
	 * Propery identifier for a breakpoint object on an event request
	 */
	public static final String JAVA_BREAKPOINT_PROPERTY = "org.eclipse.jdt.debug.breakpoint";
	
	protected static JDIDebugPlugin fgPlugin;
	
	protected JavaHotCodeReplaceManager fJavaHCRMgr;
	
	public static JDIDebugPlugin getDefault() {
		return fgPlugin;
	}
		
	public JDIDebugPlugin(IPluginDescriptor descriptor) {
		super(descriptor);
		fgPlugin = this;
	}
	
	/**
	 * Instantiates and starts up the hot code replace
	 * manager.  Also initializes step filter information.
	 */
	public void startup() throws CoreException {
		fJavaHCRMgr= new JavaHotCodeReplaceManager();
		fJavaHCRMgr.startup();		

		JDIDebugModel.setupStepFilterState();
	}
	

	/**
	 * Shutdown the HCR mgr and the debug targets.
	 */
	public void shutdown() throws CoreException {
		fJavaHCRMgr.shutdown();
		ILaunchManager launchManager= DebugPlugin.getDefault().getLaunchManager();
		IDebugTarget[] targets= launchManager.getDebugTargets();
		for (int i= 0 ; i < targets.length; i++) {
			IDebugTarget target= targets[i];
			if (target instanceof JDIDebugTarget) {
				((JDIDebugTarget)target).shutdown();
			}
		}
		JDIDebugModel.saveStepFilterState();

		fgPlugin = null;
		super.shutdown();
	}
		
}