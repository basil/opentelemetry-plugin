/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.jenkins;

import hudson.ExtensionList;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public interface PipelineListener {

    @NonNull
    static List<PipelineListener> all() {
        return ExtensionList.lookup(PipelineListener.class);
    }

    /**
     * Just before the pipeline starts
     */
    void onStartPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run);

    /**
     * Just before the `node` step starts.
     */
    void onStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run);

    /**
     * Just after the `node` step starts.
     */
    void onAfterStartNodeStep(@NonNull StepStartNode stepStartNode, @Nullable String nodeLabel, @NonNull WorkflowRun run);

    /**
     * Just after the `node` step ends
     */
    void onEndNodeStep(@NonNull StepEndNode nodeStepEndNode, @NonNull String nodeName, @NonNull WorkflowRun run);

    /**
     * Just before the `stage`step starts
     */
    void onStartStageStep(@NonNull StepStartNode stepStartNode, @NonNull String stageName, @NonNull WorkflowRun run);

    /**
     * Just after the `stage` step ends
     */
    void onEndStageStep(@NonNull StepEndNode stageStepEndNode, @NonNull String stageName, @NonNull WorkflowRun run);

    /**
     * Just before the `parallel` branch starts
     */
    void onStartParallelStepBranch(@NonNull StepStartNode stepStartNode, @NonNull String branchName, @NonNull WorkflowRun run);

    /**
     * Just before the `parallel` branch ends
     */
    void onEndParallelStepBranch(@NonNull StepEndNode stepStepNode, @NonNull String branchName, @NonNull WorkflowRun run);

    /**
     * Just before the atomic step starts
     */
    void onAtomicStep(@NonNull StepAtomNode node, @NonNull WorkflowRun run);

    /**
     * Just after the atomic step
     */
    void onAfterAtomicStep(@NonNull StepAtomNode stepAtomNode, @NonNull WorkflowRun run);

    /**
     * Just after the pipeline ends
     */
    void onEndPipeline(@NonNull FlowNode node, @NonNull WorkflowRun run);

}
