/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package a.b.c;
public record Test(int a, int b) {

	  public void x() {
	    return; // <- Toggling a breakpoint here toggles it on TestInner
	  }

	  public int y() {
	    return a + b; // <- Toggling a breakpoint here toggles it on TestInner
	  }

	  public record TestInner() {
		  static int c;
		  public void getI() {
		   System.out.println(c);
		  }
		 
	  }

	  public int z() {
	    return a + b; // <- Toggling a breakpoint here toggles it here as expected
	  }
}
