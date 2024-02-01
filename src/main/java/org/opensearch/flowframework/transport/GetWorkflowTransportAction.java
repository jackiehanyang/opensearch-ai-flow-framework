/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.transport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.flowframework.indices.FlowFrameworkIndicesHandler;
import org.opensearch.flowframework.model.Template;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import static org.opensearch.flowframework.common.CommonValue.GLOBAL_CONTEXT_INDEX;

/**
 * Transport action to retrieve a use case template within the Global Context
 */
public class GetWorkflowTransportAction extends HandledTransportAction<WorkflowRequest, GetWorkflowResponse> {

    private final Logger logger = LogManager.getLogger(GetWorkflowTransportAction.class);
    private final FlowFrameworkIndicesHandler flowFrameworkIndicesHandler;
    private final Client client;

    /**
     * Instantiates a new GetWorkflowTransportAction instance
     * @param transportService the transport service
     * @param actionFilters action filters
     * @param flowFrameworkIndicesHandler The Flow Framework indices handler
     * @param client the Opensearch Client
     */
    @Inject
    public GetWorkflowTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        FlowFrameworkIndicesHandler flowFrameworkIndicesHandler,
        Client client
    ) {
        super(GetWorkflowAction.NAME, transportService, actionFilters, WorkflowRequest::new);
        this.flowFrameworkIndicesHandler = flowFrameworkIndicesHandler;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, WorkflowRequest request, ActionListener<GetWorkflowResponse> listener) {
        if (flowFrameworkIndicesHandler.doesIndexExist(GLOBAL_CONTEXT_INDEX)) {

            String workflowId = request.getWorkflowId();
            System.out.println("inside transport action: " + workflowId);
            GetRequest getRequest = new GetRequest(GLOBAL_CONTEXT_INDEX, workflowId);

            // Retrieve workflow by ID
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(getRequest, ActionListener.wrap(response -> {
                    context.restore();

                    if (!response.isExists()) {
                        listener.onFailure(
                            new FlowFrameworkException(
                                "Failed to retrieve template (" + workflowId + ") from global context.",
                                RestStatus.NOT_FOUND
                            )
                        );
                    } else {
                        listener.onResponse(new GetWorkflowResponse(Template.parse(response.getSourceAsString())));
                    }
                }, exception -> {
                    logger.error("Failed to retrieve template from global context.", exception);
                    listener.onFailure(new FlowFrameworkException(exception.getMessage(), ExceptionsHelper.status(exception)));
                }));
            } catch (Exception e) {
                logger.error("Failed to retrieve template from global context.", e);
                listener.onFailure(new FlowFrameworkException(e.getMessage(), ExceptionsHelper.status(e)));
            }

        } else {
            listener.onFailure(new FlowFrameworkException("There are no templates in the global_context", RestStatus.NOT_FOUND));
        }

    }
}
