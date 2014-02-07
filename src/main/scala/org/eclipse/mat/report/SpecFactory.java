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
package org.eclipse.mat.report;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;
import org.osgi.framework.Bundle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds a full report based on an xml report definition, which could
 * specify several queries to be run.
 */
public final class SpecFactory extends RegistryReader<SpecFactory.Report> {
    public class Report {
        String name;
        String description;
        IConfigurationElement configElement;

        public Report(IConfigurationElement configElement) {
            this.configElement = configElement;
            this.name = configElement.getAttribute("name");
            this.description = configElement.getAttribute("description");
        }

        public String getExtensionIdentifier() {
            return configElement.getNamespaceIdentifier() + ":" + configElement.getAttribute("id");
        }

        protected URL getURL() {
            Bundle bundle = Platform.getBundle(configElement.getContributor().getName());
            if (bundle == null)
                return null;

            return bundle.getResource(configElement.getAttribute("file"));
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final SpecFactory instance = new SpecFactory();

    public static final SpecFactory instance() {
        return instance;
    }

    private SpecFactory() {

    }

    @Override
    protected Report createDelegate(IConfigurationElement configElement) throws CoreException {
        return new Report(configElement);
    }

    @Override
    protected void removeDelegate(Report delegate) {
    }

    public Spec create(Report report) throws IOException {
        URL url = report.getURL();
        if (url == null)
            return null;

        InputStream in = null;
        try {
            in = url.openStream();
            return read(in);
        } finally {
            if (in != null)
                in.close();
        }
    }

    public Spec create(String extensionIdentifier) throws IOException {
        if (extensionIdentifier.equals("org.eclipse.mat.tests:regression"))
            return createFromResource("/tests/regression.xml");
        if (extensionIdentifier.equals("org.eclipse.mat.api:suspects"))
            return createFromResource("/reports/suspects.xml");
        if (extensionIdentifier.equals("org.eclipse.mat.api:top_components"))
            return createFromResource("/reports/top_components.xml");
        if (extensionIdentifier.equals("org.eclipse.mat.api:overview"))
            return createFromResource("/reports/overview.xml");
        if (extensionIdentifier.equals("org.eclipse.mat.tests:performance"))
            return createFromResource("/tests/performance.xml");
        return null;
    }

    public Spec createFromResource(String resourceName) throws IOException {
        return create(this.getClass().getResourceAsStream(resourceName));
    }
    public Spec create(InputStream in) throws IOException {
        try {
            return read(in);
        } finally {
            in.close();
        }
    }
    public Spec create(File specFile) throws IOException {
        FileInputStream in = new FileInputStream(specFile);

        try {
            return read(in);
        } finally {
            in.close();
        }
    }

    public void resolve(Spec master) throws IOException {
        if (master.getTemplate() != null) {
            String template = master.getTemplate();
            Spec other = create(template);
            if (other != null) {
                resolve(other);
                master.merge(other);
            } else {
                Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                        MessageUtil.format(Messages.SpecFactory_Error_MissingTemplate, template));
            }
        }

        if (master instanceof SectionSpec) {
            for (Spec child : ((SectionSpec) master).getChildren())
                resolve(child);
        }
    }

    // //////////////////////////////////////////////////////////////
    // XML reading
    // //////////////////////////////////////////////////////////////

    private static final Spec read(InputStream input) throws IOException {
        try {
            SpecHandler handler = new SpecHandler();
            XMLReader saxXmlReader = XMLReaderFactory.createXMLReader();
            saxXmlReader.setContentHandler(handler);
            saxXmlReader.setErrorHandler(handler);
            saxXmlReader.parse(new InputSource(input));
            return handler.getSpec();
        } catch (SAXException e) {
            IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
    }

    private static class SpecHandler extends DefaultHandler {
        private LinkedList<Spec> stack;
        private StringBuilder buf;

        private SpecHandler() {
            this.stack = new LinkedList<Spec>();
            this.stack.add(new SectionSpec("root"));
        }

        @SuppressWarnings("nls")
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
            if ("section".equals(localName)) {
                String n = attributes.getValue("name");
                n = translate(n);

                SectionSpec spec = new SectionSpec(n);
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            } else if ("query".equals(localName)) {
                String n = attributes.getValue("name");
                n = translate(n);

                QuerySpec spec = new QuerySpec(n);
                ((SectionSpec) stack.getLast()).add(spec);
                stack.add(spec);
            } else if ("param".equals(localName)) {
                String value = attributes.getValue("value");
                value = translate(value);
                stack.getLast().set(attributes.getValue("key"), value);
            } else if ("template".equals(localName)) {
                buf = new StringBuilder();
            } else if ("command".equals(localName)) {
                buf = new StringBuilder();
            }
        }

        /**
         * Some values are translatable using % prefix and a translation
         * in the plugin.properties file.
         *
         * @param n
         * @return
         */
        private String translate(String n) {
            return n;
        }

        @SuppressWarnings("nls")
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            if ("section".equals(localName)) {
                stack.removeLast();
            } else if ("query".equals(localName)) {
                stack.removeLast();
            } else if ("template".equals(localName)) {
                stack.getLast().setTemplate(buf.toString());
                buf = null;
            } else if ("command".equals(localName)) {
                ((QuerySpec) stack.getLast()).setCommand(buf.toString());
                buf = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (buf != null)
                buf.append(ch, start, length);
        }

        public Spec getSpec() {
            return ((SectionSpec) stack.getFirst()).getChildren().get(0);
        }
    }
}
