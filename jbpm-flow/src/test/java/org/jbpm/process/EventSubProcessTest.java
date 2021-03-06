/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process;

import static org.jbpm.process.test.NodeCreator.connect;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.drools.core.process.core.Work;
import org.drools.core.process.core.datatype.impl.type.ObjectDataType;
import org.drools.core.process.core.impl.WorkImpl;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.event.EventTypeFilter;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.test.NodeCreator;
import org.jbpm.process.test.TestWorkItemHandler;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.test.util.AbstractBaseTest;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.EventSubProcessNode;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessContext;
import org.kie.api.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventSubProcessTest extends AbstractBaseTest  {
    
    private static final Logger logger = LoggerFactory.getLogger(EventSubProcessTest.class);
    
	@Test
    public void testNestedEventSubProcess() throws Exception {
        RuleFlowProcess process = new RuleFlowProcess();
        process.setAutoComplete(true);
        String processId = "org.jbpm.process.event.subprocess";
        process.setId(processId);
        process.setName("Event SubProcess Process");
        
        List<Variable> variables = new ArrayList<Variable>();
        Variable variable = new Variable();
        variable.setName("event");
        ObjectDataType personDataType = new ObjectDataType();
        personDataType.setClassName("org.drools.Person");
        variable.setType(personDataType);
        variables.add(variable);
        process.getVariableScope().setVariables(variables);

        NodeCreator<StartNode> startNodeCreator = new NodeCreator<StartNode>(process, StartNode.class);
        NodeCreator<EndNode> endNodeCreator = new NodeCreator<EndNode>(process, EndNode.class);
        NodeCreator<CompositeNode> compNodeCreator = new NodeCreator<CompositeNode>(process, CompositeNode.class);
        
        // outer process
        StartNode startNode = startNodeCreator.createNode("start0");
        CompositeNode compositeNode = compNodeCreator.createNode("comp0");
        connect( startNode, compositeNode );
        
        EndNode endNode = endNodeCreator.createNode("end0");
        connect( compositeNode, endNode );
            
        // 1rst level nested subprocess
        startNodeCreator.setNodeContainer(compositeNode);
        endNodeCreator.setNodeContainer(compositeNode);
        compNodeCreator.setNodeContainer(compositeNode);
        
        startNode = startNodeCreator.createNode("start1");

        compositeNode = compNodeCreator.createNode("comp1");
        connect( startNode, compositeNode );

        endNode = endNodeCreator.createNode("end1");
        connect( compositeNode, endNode );
       
        // 2nd level subprocess
        startNodeCreator.setNodeContainer(compositeNode);
        endNodeCreator.setNodeContainer(compositeNode);
        NodeCreator<WorkItemNode> workItemNodeCreator = new NodeCreator<WorkItemNode>(compositeNode, WorkItemNode.class);

        startNode = startNodeCreator.createNode("start2");
        WorkItemNode workItemNode = workItemNodeCreator.createNode("workItem2");
        Work work = new WorkImpl();
        String workItemName = "play";
        work.setName( workItemName );
        workItemNode.setWork(work);
        connect( startNode, workItemNode );

        endNode = endNodeCreator.createNode("end2");
        connect( workItemNode, endNode );

        // (3rd level) event sub process in 2nd level subprocess
        NodeCreator<EventSubProcessNode> espNodeCreator = new NodeCreator<EventSubProcessNode>(compositeNode, EventSubProcessNode.class);
        EventSubProcessNode espNode = espNodeCreator.createNode("eventSub2");
        EventTypeFilter eventFilter = new EventTypeFilter();
        String EVENT_NAME = "subEvent";
        eventFilter.setType(EVENT_NAME);
        espNode.addEvent(eventFilter);
        
        startNodeCreator.setNodeContainer(espNode);
        endNodeCreator.setNodeContainer(espNode);
        NodeCreator<ActionNode> actionNodeCreator = new NodeCreator<ActionNode>(espNode, ActionNode.class);
       
        startNode = startNodeCreator.createNode("start3*");
        ActionNode actionNode = actionNodeCreator.createNode("print3*");
        actionNode.setName("Print");
        final List<String> eventList = new ArrayList<String>();
        DroolsAction action = new DroolsConsequenceAction("java", null);
        action.setMetaData("Action", new Action() {
            public void execute(ProcessContext context) throws Exception {
                eventList.add("Executed action");
            }
        });
        actionNode.setAction(action);
        connect( startNode, actionNode );
        
        endNode = endNodeCreator.createNode("end3*");
        connect( actionNode, endNode );
        
        // run process
        KieSession ksession = createKieSession(process);    
       
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler(workItemName, workItemHandler);
        ProcessInstance processInstance = ksession.startProcess(processId);
        
        processInstance.signalEvent(EVENT_NAME, null);
        assertEquals("Event " + EVENT_NAME + " did not fire!", 1, eventList.size());
        
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItems().removeLast().getId(), null);
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.getState());
        
    }
    
}
