/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import jenkins.plugins.git.GitStep;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Customization of shell steps ({@code sh}, {@code cmd}, and {@code powershell}).
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GitStepHandler implements StepHandler {

    private final static Logger LOGGER = Logger.getLogger(GitStepHandler.class.getName());

    @Override
    public boolean canCreateSpanBuilder(@Nonnull FlowNode flowNode) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof GitStep.DescriptorImpl;
    }

    @Nonnull
    @Override
    public SpanBuilder createSpanBuilder(@Nonnull FlowNode node, @Nonnull Tracer tracer) throws Exception {
        final Map<String, Object> arguments = ArgumentsAction.getFilteredArguments(node);
        final String stepFunctionName = node.getDisplayFunctionName();
        return createSpanBuilder(stepFunctionName, arguments, tracer);
    }

    /**
     * See:
     * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md
     * https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols
     */
    @VisibleForTesting
    public SpanBuilder createSpanBuilder(String stepFunctionName, Map<String, Object> arguments, Tracer tracer) throws URISyntaxException {

        String gitUrlAsString = checkNotNull(arguments.get("url")).toString();
        URIish gitUri = new URIish(gitUrlAsString);
        String host = gitUri.getHost();
        String gitRepositoryPath = normalizeGitRepositoryPath(gitUri);

        String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
        final SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
        if ("https".equals(gitUri.getScheme())) {
            // HTTPS URL e.g. https://github.com/open-telemetry/opentelemetry-java

            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, gitUri.getScheme())
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.HTTP_URL, sanitizeUrl(gitUri))
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
            ;

        } else if ("ssh".equals(gitUri.getScheme())
                || (gitUri.getScheme() == null && gitUri.getHost() != null)) {
            // SSH URL e.g. ssh://git@example.com/open-telemetry/opentelemetry-java.git
            // SCP URL e.g. git@github.com:open-telemetry/opentelemetry-java.git

            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, "ssh")
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP.getValue())
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
            ;
        } else if (("file".equals(gitUri.getScheme())
                || (gitUri.getScheme() == null && gitUri.getHost() == null))) {
            // FILE URL e.g. file:///srv/git/project.git
            // IMPLICIT FILE URL e.g. /srv/git/project.git
            spanBuilder
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
            ;
        }
        return spanBuilder;
    }

    private String normalizeGitRepositoryPath(URIish gitUri) {
        String githubOrgAndRepository = gitUri.getPath();
        if (githubOrgAndRepository.startsWith("/")) {
            githubOrgAndRepository = githubOrgAndRepository.substring("/".length());
        }
        if (githubOrgAndRepository.endsWith(".git")) {
            githubOrgAndRepository = githubOrgAndRepository.substring(0, githubOrgAndRepository.length() - ".git".length());
        }
        return githubOrgAndRepository;
    }

    /**
     * Remove the `username` and the `password` params of the URL.
     *
     * Example: "https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git" is sanitized as
     * "https://github.com/open-telemetry/opentelemetry-java.git"
     *
     * @param gitUri to sanitize
     * @return sanitized url
     */
    @Nonnull
    protected String sanitizeUrl(@Nonnull URIish gitUri) {
        String normalizedUrl = gitUri.getScheme() + "://";
        if (gitUri.getHost() != null) {
            normalizedUrl += gitUri.getHost();
        }
        if (gitUri.getPort() != -1) {
            normalizedUrl += ":" + gitUri.getPort();
        }
        normalizedUrl += gitUri.getPath();
        return normalizedUrl;
    }
}