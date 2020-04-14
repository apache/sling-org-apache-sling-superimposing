/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.superimposing.impl;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.apache.sling.superimposing.SuperimposingResourceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Superimposing resource provider.
 * Maps a single source path to the target root path, with or without overlay depending on configuration.
 */
public class SuperimposingResourceProviderImpl extends ResourceProvider<Object> implements SuperimposingResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(SuperimposingResourceProviderImpl.class);

    private final String rootPath;
    private final String rootPrefix;
    private final String sourcePath;
    private final String sourcePathPrefix;
    private final boolean overlayable;
    private final String toString;
    private ServiceRegistration<?> registration;

    SuperimposingResourceProviderImpl(String rootPath, String sourcePath, boolean overlayable) {
        this.rootPath = rootPath;
        this.rootPrefix = rootPath.concat("/");
        this.sourcePath = sourcePath;
        this.sourcePathPrefix = sourcePath.concat("/");
        this.overlayable = overlayable;
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append(" [path=").append(rootPath).append(", ");
        sb.append("sourcePath=").append(sourcePath).append(", ");
        sb.append("overlayable=").append(overlayable).append("]");
        this.toString = sb.toString();
    }

	@SuppressWarnings("rawtypes")
	@Override
	public Resource getResource(ResolveContext<Object> ctx, String path, ResourceContext resourceContext,
			Resource parent) {
		Resource candidate =  getResource(ctx.getResourceResolver(), path);
		if(candidate == null) {
			ResourceProvider<?> parentProvider = ctx.getParentResourceProvider();
			ResolveContext parentCtx = ctx.getParentResolveContext();
			// Ask the parent provider
			if (parentProvider != null) {
				return parentProvider.getResource(parentCtx, path, resourceContext, parent);
			}
		}
		return candidate;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterator<Resource> listChildren(ResolveContext<Object> ctx, Resource parent) {
		Iterator<Resource> children = listChildren(parent);
        if (children == null && ctx.getParentResourceProvider() != null) {
            children = ctx.getParentResourceProvider().listChildren((ResolveContext) ctx.getParentResolveContext(), parent);
        }
		return children;
	}

    /**
     * {@inheritDoc}
     */
    public Resource getResource(ResourceResolver resolver, String path) {
        final String mappedPath = mapPath(this, resolver, path);
        if (null != mappedPath) {
            // the existing resource where the superimposed content is retrieved from
            final Resource mappedResource = resolver.getResource(mappedPath);
            if (null != mappedResource) {
                return new SuperimposingResource(mappedResource, path, mappedPath);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Resource> listChildren(Resource resource) {

        // unwrap resource if it is a wrapped resource
        final Resource currentResource;
        if (resource instanceof ResourceWrapper) {
            currentResource = ((ResourceWrapper)resource).getResource();
        }
        else {
            currentResource = resource;
        }

        // delegate resource listing to resource resolver
        if (currentResource instanceof SuperimposingResource) {
            final SuperimposingResource res = (SuperimposingResource) currentResource;
            final ResourceResolver resolver = res.getResource().getResourceResolver();
            final Iterator<Resource> children = resolver.listChildren(res.getResource());
            return new SuperimposingResourceIterator(this, children);
        }
        return null;
    }

    /**
     * Maps a path below the superimposing root to the target resource's path.
     * @param provider Superimposing resource provider
     * @param resolver Resource resolver
     * @param path Path to map
     * @return Mapped path or null if no mapping available
     */
    static String mapPath(SuperimposingResourceProviderImpl provider, ResourceResolver resolver, String path) {
        if (provider.overlayable) {
            return mapPathWithOverlay(provider, resolver, path);
        }
        else {
            return mapPathWithoutOverlay(provider, path);
        }
    }

    /**
     * Maps a path below the superimposing root to the target resource's path with check for overlaying.
     * @param provider Superimposing resource provider
     * @param resolver Resource resolver
     * @param path Path to map
     * @return Mapped path or null if no mapping available
     */
    static String mapPathWithOverlay(SuperimposingResourceProviderImpl provider, ResourceResolver resolver, String path) {
        if (StringUtils.equals(path, provider.rootPath)) {
            // Superimposing root path cannot be overlayed
            return mapPathWithoutOverlay(provider, path);
        }
        else if (StringUtils.startsWith(path, provider.rootPrefix)) {
            if (hasOverlayResource(resolver, path)) {
                // overlay item exists, allow underlying resource provider to step in
                return null;
            }
            else {
                // overlay item does not exist, overlay cannot be applied, fallback to mapped path without overlay
                return mapPathWithoutOverlay(provider, path);
            }
        }
        return null;
    }

    static boolean hasOverlayResource(ResourceResolver resolver, String path) {
        // check for overlay resource by checking directly in underlying JCR
        final Session session = resolver.adaptTo(Session.class);
        try {
            return (null != session && session.itemExists(path));
        } catch (RepositoryException e) {
            log.error("Error accessing the repository. ", e);
        }
        return false;
    }

    /**
     * Maps a path below the superimposing root to the target resource's path without check for overlaying.
     * @param provider Superimposing resource provider
     * @param resolver Resource resolver
     * @param path Path to map
     * @return Mapped path or null if no mapping available
     */
    static String mapPathWithoutOverlay(SuperimposingResourceProviderImpl provider, String path) {
        final String mappedPath;
        if (StringUtils.equals(path, provider.rootPath)) {
            mappedPath = provider.sourcePath;
        } else if (StringUtils.startsWith(path, provider.rootPrefix)) {
            mappedPath = StringUtils.replaceOnce(path, provider.rootPrefix, provider.sourcePathPrefix);
        } else {
            mappedPath = null;
        }
        return mappedPath;
    }

    /**
     * Maps a path below the target resource to the superimposed resource's path.
     *
     * @param provider
     * @param path
     * @return
     */
    static String reverseMapPath(SuperimposingResourceProviderImpl provider, String path) {
        final String mappedPath;
        if (path.startsWith(provider.sourcePathPrefix)) {
            mappedPath = StringUtils.replaceOnce(path, provider.sourcePathPrefix, provider.rootPrefix);
        } else if (path.equals(provider.sourcePath)) {
            mappedPath = provider.rootPath;
        } else {
            mappedPath = null;
        }
        return mappedPath;
    }
    
	public Resource create(final ResolveContext<Object> ctx, final String path, final Map<String, Object> properties)
			throws PersistenceException {
		Resource createdResource = null;
		if (ctx.getParentResourceProvider() != null) {
			createdResource = ctx.getParentResourceProvider().create((ResolveContext) ctx.getParentResolveContext(),
					path, properties);
		}
		return createdResource;
	}

	@Override
	public void delete(final ResolveContext<Object> ctx, final Resource resource) throws PersistenceException {
		if (ctx.getParentResourceProvider() != null) {
			ctx.getParentResourceProvider().delete((ResolveContext) ctx.getParentResolveContext(), resource);
		}
	}

	@Override
	public void revert(final ResolveContext<Object> ctx) {
		if (ctx.getParentResourceProvider() != null) {
			ctx.getParentResourceProvider().revert((ResolveContext) ctx.getParentResolveContext());
		}
	}

	@Override
	public void commit(final ResolveContext<Object> ctx) throws PersistenceException {
		if (ctx.getParentResourceProvider() != null) {
			ctx.getParentResourceProvider().commit((ResolveContext) ctx.getParentResolveContext());
		}
	}

	@Override
	public boolean hasChanges(final ResolveContext<Object> ctx) {
		if (ctx.getParentResourceProvider() != null) {
			ctx.getParentResourceProvider().hasChanges((ResolveContext) ctx.getParentResolveContext());
		}
		return false;
	}

    /************** Service Registration********************/

    void registerService(BundleContext context) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_DESCRIPTION, "Provider of superimposed resources");
        props.put(Constants.SERVICE_VENDOR, "The Apache Software Foundation");
        props.put(PROPERTY_ROOT, new String[]{rootPath});
        props.put(PROPERTY_MODIFIABLE, true); 
        registration = context.registerService(ResourceProvider.class, this, props);
        log.info("Registered {}", this);
    }

    void unregisterService() {
        if (registration != null) {
            registration.unregister();
            registration = null;
            log.info("Unregistered {}", this);
        }
    }

    /**
     * @return Root path (destination for superimposing)
     */
    public String getRootPath() {
        return rootPath;
    }

    /**
     * @return Source path (source for superimposing)
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * @return Overlayable yes/no
     */
    public boolean isOverlayable() {
        return overlayable;
    }

    /* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(rootPath);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
    public boolean equals(Object o) {
        if (o instanceof SuperimposingResourceProviderImpl) {
            final SuperimposingResourceProviderImpl srp = (SuperimposingResourceProviderImpl)o;
            return this.rootPath.equals(srp.rootPath) && this.sourcePath.equals(srp.sourcePath) && this.overlayable == srp.overlayable;

        }
        return false;
    }

    @Override
    public String toString() {
        return toString;
    }

}
