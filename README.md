[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-superimposing/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-superimposing/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-superimposing/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-superimposing/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-superimposing&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-superimposing)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-superimposing&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-superimposing)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.superimposing.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.superimposing)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.superimposing/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.superimposing%22)&#32;[![Contrib](https://sling.apache.org/badges/status-contrib.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/status/contrib.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Superimposing Resource Provider

This module is part of the [Apache Sling](https://sling.apache.org) project.

## About

The Superimposing Resource Provider is an extension for the [Apache Sling](http://sling.apache.org/) framework. It implements the [ResourceProvider](http://sling.apache.org/apidocs/sling6/org/apache/sling/api/resource/ResourceProvider.html) interface.

Goals of the solution:

* Mirroring resource trees
 * Reflect changes from master tree
 * Avoid unnecessary copies
* Superimposing resources
 * Add
 * Remove
 * Overlay

There is a presentation from [adaptTo() 2013](https://adapt.to) with more background information:<br/>
[Superimposing Content Presentation adaptTo() 2013](https://adapt.to/content/dam/adaptto/production/presentations/2013/adaptto2013-lightning-superimposing-content-julian-sedding-stefan-seifert.pdf/_jcr_content/renditions/original./adaptto2013-lightning-superimposing-content-julian-sedding-stefan-seifert.pdf)

The implementation of this provider is based on the great work of Julian Sedding from [SLING-1778](https://issues.apache.org/jira/browse/SLING-1778).


## How to use

Preparations:

* Deploy the Superimposing Resource Provider Bundle to your Sling instance
* By default the resource provider is _not_ active. You have to enable it via OSGi configuration in the Felix Console (see below)

To create a superimposed resource create a node in JCR with:

* Node type **sling:SuperimposeResource**
 * Alternatively you can create a node with any other node type and use the mixin **sling:Superimpose**
* Property **sling:superimposeSourcePath**: points to an absolute path - this content is mirrored to the location of the new node
* (Optional) Property **sling:superimposeRegisterParent**: If set to true, not the new node itself but its parent is used as root node for the superimposed content. This is useful if you have no control about the parent node itself (e.g. due to node type restrictions).
* (Optional) Property **sling:superimposeOverlayable**: If set to true, the content is not only mirrored, but can be overlayed by nodes in the target tree below the superimposing root node. _Please note that this feature is still experimental._


## Configuration

In the Felix console you can configure the creation of Superimposing Resource Providers via the service "Apache Sling Superimposing Resource Manager":

* **enabled**: If set to true, the superimposing is active
* **findAllQueries**: Defines JCR queries that are executed on service startup to detect all superimposing nodes that are already created in the JCR. By default only the /content subtree is scanned.
* **obervationPaths**: Paths on which the new, updated or removed superimposing nodes are automatically detected on runtime.


## Remarks

* The superimposing resource provider depends on an underlying JCR repository. It currently does only work with JCR and supports mirroring or overlaying JCR nodes.
* The Superimposing Resource Provider provides an API in the package org.apache.sling.superimposing. For the basic superimposing content features you do not need this API. It is a read-only API which allows to query which superimposing resource providers are currently active with which configuration. The API is useful if you want to react on JCR events on the source tree and actions on the mirrored trees as well (e.g. for sending invalidation events to clean an external cache).
* If you want to use the superimposing resource provider within a CMS application that allows to modify resource content via it's GUI make sure that this CMS application supports this resource provider in it's authoring environment (and does make direct JCR access, because this bypassed the mirroring and affects the original JCR node - risk of data loss!). If you cannot be sure of this please activate the provider only on the sling instances that render the content for the public (publishing instances), and not in the authoring instance.


## Comparison with Sling Resource Merger

In Sling Contrib a [Apache Sling Resource Merger](https://sling.apache.org/documentation/bundles/resource-merger.html) bundle is provided. Although both Sling Resource Merger and the Superimposing Resource Provider take care of mirroring and merging resources they solve quite different problems and have different usecases:

* Sling Resource Merger is primary about Merging resources of content structures from /apps and /libs, e.g. dialog definitions of an CMS application. It mounts the merged resources at a new path (e.g. /mnt/overlay) which can be included in the script resolution.
* The Superimposing Content Resource Provider is targeted at content. Think of a scenario with one master site that is rolled out to hundreds of slave sites with mostly identical contents but some site-specific overrides and customizations. This is not possible with Sling Resource Merger and vice versa.
