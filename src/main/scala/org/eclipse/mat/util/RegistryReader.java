/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.util;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.dynamichelpers.IExtensionChangeHandler;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.mat.report.internal.Messages;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RegistryReader<D> implements IExtensionChangeHandler {
    Set<D> delegates = new HashSet<D>();

    protected RegistryReader() {
    }


    public final void addExtension(IExtensionTracker tracker, IExtension extension) {
        IConfigurationElement[] configs = extension.getConfigurationElements();
        for (int i = 0; i < configs.length; ++i) {
            String name = extension.getNamespaceIdentifier();
            if ("org.eclipse.mat.eclipse".equals(name) ||
                    "org.eclipse.mat.jetty".equals(name))
                continue;

            try {
                D delegate = createDelegate(configs[i]);
                if (delegate != null) {
                    delegates.add(delegate);
                    if (tracker != null)
                        tracker.registerObject(extension, delegate, IExtensionTracker.REF_WEAK);
                }
            } catch (CoreException e) {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                        MessageUtil.format(Messages.RegistryReader_Error_Registry, configs[i]), e);
            }

        }
    }

    @SuppressWarnings("unchecked")
    public final void removeExtension(IExtension extension, Object[] objects) {
        for (int ii = 0; ii < objects.length; ++ii) {
            if (delegates.remove(objects[ii]))
                removeDelegate((D) objects[ii]);
        }
    }

    protected abstract D createDelegate(IConfigurationElement configElement) throws CoreException;

    protected abstract void removeDelegate(D delegate);

    public Collection<D> delegates() {
        return Collections.unmodifiableCollection(delegates);
    }
}
