package org.eclipse.jdt.internal.debug.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.text.MessageFormat;
import java.util.*;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.*;
import org.eclipse.jdt.debug.core.*;

import com.sun.jdi.*;

/**
 * The value of a variable
 */

public class JDIValue extends JDIDebugElement implements IValue, IJavaValue {
	
	private Value fValue;
	private List fVariables;
	
	private static final String fgToStringSignature = "()Ljava/lang/String;"; //$NON-NLS-1$
	private static final String fgToString = "toString"; //$NON-NLS-1$
	
	/**
	 * A flag indicating if this value is still allocated (valid)
	 */
	private boolean fAllocated = true;
	
	public JDIValue(JDIDebugTarget target, Value value) {
		super(target);
		fValue = value;
	}
	
	public Object getAdapter(Class adapter) {
		if (adapter == IJavaValue.class) {
			return this;
		}			
		return super.getAdapter(adapter);
	}

	public int getElementType() {
		return VALUE;
	}
	
	/**
	 * @see IValue#getValueString()
	 */
	public String getValueString() throws DebugException {
		if (!isAllocated()) {
			return JDIDebugModelMessages.getString("JDIValue.deallocated"); //$NON-NLS-1$
		}
		if (fValue == null) {
			return JDIDebugModelMessages.getString("JDIValue.null_4"); //$NON-NLS-1$
		}
		if (fValue instanceof StringReference) {
			try {
				return ((StringReference) fValue).value();
			} catch (RuntimeException e) {
				targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_value"), new String[] {e.toString()}), e); //$NON-NLS-1$
				// execution will not reach this line, as
				// #targetRequestFailed will thrown an exception							
				return null;
			}
		}
		if (fValue instanceof ObjectReference) {
			StringBuffer name= new StringBuffer();
			if (fValue instanceof ClassObjectReference) {
				name.append('(');  //$NON-NLS-1$
				name.append(((ClassObjectReference)fValue).reflectedType());
				name.append(')');  //$NON-NLS-1$
			}
			name.append(" ("); //$NON-NLS-1$
			name.append(JDIDebugModelMessages.getString("JDIValue.id_8")); //$NON-NLS-1$
			name.append('=');  //$NON-NLS-1$
			try {
				name.append(((ObjectReference)fValue).uniqueID());
			} catch (RuntimeException e) {
				targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_unique_id"), new String[] {e.toString()}), e); //$NON-NLS-1$
				// execution will not reach this line, as
				// #targetRequestFailed will thrown an exception							
				return null;
			}
			name.append(')'); //$NON-NLS-1$
			return name.toString();
		} else {
			return fValue.toString();
		}
	}
	
	/**
	 * @see IValue#getReferenceTypeName()
	 */
	public String getReferenceTypeName() throws DebugException {
		try {
			if (fValue == null) {
				return JDIDebugModelMessages.getString("JDIValue.null_10"); //$NON-NLS-1$
			}
			return fValue.type().name();
		} catch (RuntimeException e) {
			targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_reference_type_name"), new String[] {e.toString()}), e); //$NON-NLS-1$
			// execution will not reach this line, as
			// #targetRequestFailed will thrown an exception			
			return null;			
		}
	}

	/**
	 * @see Object#hashCode()
	 */
	public int hashCode() {
		if (fValue == null) {
			return getClass().hashCode();
		} else {
			return fValue.hashCode();
		}
	}

	/**
	 * @see Object#equals(Object)
	 */
	public boolean equals(Object o) {
		if (fValue == o) {
			return true;
		}
		if (o instanceof JDIValue) {
			Value other = ((JDIValue)o).getUnderlyingValue();	
			if (fValue == null) {
				return false;
			}
			if (other == null) {
				return false;
			}
			return fValue.equals(other);
		} else {
			return false;
		}
	}	

	/**
	 * @see IValue#getVariables()
	 */
	public IVariable[] getVariables() throws DebugException {
		List list = getVariables0();
		return (IVariable[])list.toArray(new IVariable[list.size()]);
	}
	
	protected List getVariables0() throws DebugException {
		if (!isAllocated()) {
			return Collections.EMPTY_LIST;
		}
		if (fVariables != null) {
			return fVariables;
		} else
			if (fValue instanceof ObjectReference) {
				ObjectReference object= (ObjectReference) fValue;
				fVariables= new ArrayList();
				if (isArray()) {
					int length= getArrayLength();
					fVariables= JDIArrayPartition.splitArray((JDIDebugTarget)getDebugTarget(), (ArrayReference)object, 0, length - 1);
				} else {		
					List fields= null;
					try {
						ReferenceType refType= object.referenceType();
						fields= refType.allFields();
					} catch (RuntimeException e) {
						targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_fields"), new String[] {e.toString()}), e); //$NON-NLS-1$
						// execution will not reach this line, as
						// #targetRequestFailed will thrown an exception			
						return null;
					}
					Iterator list= fields.iterator();
					while (list.hasNext()) {
						Field field= (Field) list.next();
						fVariables.add(new JDIFieldVariable((JDIDebugTarget)getDebugTarget(), field, object));
					}
					Collections.sort(fVariables, new Comparator() {
						public int compare(Object a, Object b) {
							return sortChildren(a, b);
						}
					});
				}
				
				return fVariables;
			} else {
				return Collections.EMPTY_LIST;
			}
	}
	
	/**
	 * Group statics and instance variables, 
	 * sort alphabetically within each group. 
	 */
	protected int sortChildren(Object a, Object b) {
		IJavaVariable v1= (IJavaVariable)a;
		IJavaVariable v2= (IJavaVariable)b;
		
		try {
			boolean v1isStatic= v1.isStatic();
			boolean v2isStatic= v2.isStatic();
			if (v1isStatic && !v2isStatic) {
				return -1;
			}
			if (!v1isStatic && v2isStatic) {
				return 1;
			}
			return v1.getName().compareToIgnoreCase(v2.getName());
		} catch (DebugException de) {
			logError(de);
			return -1;
		}
	}

	/**
	 * Returns whether this value is an array
	 */
	protected boolean isArray() {
		return fValue instanceof ArrayReference;
	}
	
	/**
	 * Returns this value as an array reference, or <code>null</code>
	 */
	public ArrayReference getArrayReference() {
		if (isArray()) {
			return (ArrayReference)fValue;
		} else {
			return null;
		}
	}

	/**
	 * @see IValue#isAllocated()
	 */
	public boolean isAllocated() throws DebugException {
		if (fAllocated) {
			if (fValue instanceof ObjectReference) {
				try {
					fAllocated = !((ObjectReference)fValue).isCollected();
				} catch (VMDisconnectedException e) {
					// if the VM disconnects, this value is not allocated
					fAllocated = false;
				} catch (RuntimeException e) {
					targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_is_collected"), new String[] {e.toString()}), e); //$NON-NLS-1$
					// execution will fall through, as
					// #targetRequestFailed will thrown an exception			
				}
			} else {
				IDebugTarget dt = getDebugTarget();
				fAllocated = !dt.isTerminated();
			}
		}
		return fAllocated;
	}
	
	/**
	 * @see IJavaValue#getSignature()
	 */
	public String getSignature() throws DebugException {
		try {
			if (fValue != null) {
				return fValue.type().signature();
			} else {
				return null;
			}
		} catch (RuntimeException e) {
			targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_type_signature"), new String[] {e.toString()}), e); //$NON-NLS-1$
			// execution will not reach this line, as
			// #targetRequestFailed will thrown an exception			
			return null;			
		}
	}

	/**
	 * @see IJavaValue#getArrayLength()
	 */
	public int getArrayLength() throws DebugException {
		if (isArray()) {
			try {
				return getArrayReference().length();
			} catch (RuntimeException e) {
				targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_retrieving_length_of_array"), new String[] {e.toString()}), e); //$NON-NLS-1$
			}
		}
		return -1;
	}
	
	/**
	 * Returns this value's underlying JDI value
	 */
	protected Value getUnderlyingValue() {
		return fValue;
	}
	
	/**
	 * @see IJavaValue#evaluateToString(IJavaThread)
	 */
	public synchronized String evaluateToString(final IJavaThread thread) throws DebugException {
		String sig = getSignature();
		if (sig == null) {
			return JDIDebugModelMessages.getString("JDIValue.null_15"); //$NON-NLS-1$
		}
		if (sig.length() == 1) {
			// primitive
			return getValueString();
		}

		if (!thread.isSuspended()) {
			requestFailed(JDIDebugModelMessages.getString("JDIValue.thread_not_suspended"), null); //$NON-NLS-1$
		}
		
		final String[] toString = new String[1];
		final DebugException[] ex = new DebugException[1];
		Runnable eval= new Runnable() {
			public void run() {
				try {
					toString[0] = evaluateToString0((JDIThread)thread);
				} catch (DebugException e) {
					ex[0]= e;
				}					
				synchronized (JDIValue.this) {
					JDIValue.this.notifyAll();
				}
			}
		};
		
		int timeout = ((JDIThread)thread).getRequestTimeout();
		Thread evalThread = new Thread(eval);
		evalThread.start();
		try {
			wait(timeout);
		} catch (InterruptedException e) {
		}
		
		if (ex[0] != null) {
			throw ex[0];
		}
		
		if (toString[0] != null) {
			return toString[0];
		}	
		
		((JDIThread)thread).abortEvaluation();
		requestFailed(JDIDebugModelMessages.getString("JDIValue.timeout_performing_toString()"), null); //$NON-NLS-1$
		return null;
	}
	
	
	protected String evaluateToString0(JDIThread thread) throws DebugException {
		try {
			ObjectReference object = (ObjectReference)fValue;
			ReferenceType type = object.referenceType();
			List methods = type.methodsByName(fgToString, fgToStringSignature);
			if (methods.size() == 0) {
				requestFailed(JDIDebugModelMessages.getString("JDIValue.toString()_not_implemented"), null); //$NON-NLS-1$
			}
			Method method = (Method)methods.get(0);
			StringReference string = (StringReference)thread.invokeMethod(null, object, method, Collections.EMPTY_LIST);
			return string.value();
		} catch (RuntimeException e) {
			targetRequestFailed(MessageFormat.format(JDIDebugModelMessages.getString("JDIValue.exception_evaluating_toString()"), new String[] {e.toString()}), e); //$NON-NLS-1$
			// exection will never reach this line as
			// #targetRequestFailed will throw an exception			
			return null;
		}
	} 
}