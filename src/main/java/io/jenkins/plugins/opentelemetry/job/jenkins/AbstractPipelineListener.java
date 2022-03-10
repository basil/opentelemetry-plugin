/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class AbstractPipelineListener implements PipelineListener {
    @Override
    public void onStartPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {

    }

    @Override
    public void onStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {

    }

    @Override
    public void onStartStageStep(@NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run) {

    }

    @Override
    public void onAfterStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run) {

    }

    @Override
    public void onEndNodeStep(@NonNull StepEndNode nodeStepEndNode, @NonNull String nodeName, @NonNull WorkflowRun run) {

    }

    @Override
    public void onEndStageStep(@NonNull StepEndNode stageStepEndNode, @NonNull String stageName, @NonNull WorkflowRun run) {

    }

    @Override
    public void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run) {

    }

    @Override
    public void onAfterAtomicStep(@NonNull StepAtomNode stepAtomNode, @NonNull WorkflowRun run) {

    }

    @Override
    public void onStartParallelStepBranch(@NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run) {

    }

    @Override
    public void onEndParallelStepBranch(@NonNull StepEndNode stepStepNode, @NonNull String branchName, @NonNull WorkflowRun run) {

    }

    @Override
    public void onEndPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run) {

    }
}
