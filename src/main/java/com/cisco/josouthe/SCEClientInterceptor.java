package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.agent.api.impl.NoOpTransaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SCEClientInterceptor extends MyBaseInterceptor {
    List<String> excludeList;
    IReflector getVariableReflector;

    public SCEClientInterceptor() {
        super();
        this.excludeList = Arrays.asList("move inventory", "process pick release", "list work queue entries",
            "allocate pick group", "sl_process dwnld", "Process inventory move", "Complete rf pick list for deposit",
            "Process inventory adjustment", "Process cross dock requirements", "Allocate location", "noop");

        this.getVariableReflector = makeInvokeInstanceMethodReflector("getVariable", "String"); //returns a String
    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.redprairie.moca.server.dispatch.RequestDispatcher")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("executeCommand")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        return rules;
    }

    @Override
    public Object onMethodBegin (Object objectIntercepted, String className, String methodName, Object[] params) {
        Transaction transaction = AppdynamicsAgent.getTransaction();
        if( transaction instanceof NoOpTransaction ) // BT already started externally, get out of here
            return null;
        String btName = "ThickClients";
        Object commandParam = params[0];
        Object metaParam = params[3];

        String name = String.valueOf(commandParam).split("where")[0].trim();
        if( !this.excludeList.stream().noneMatch(s -> s.equals(name)))
            btName = btName + "." + name;

        transaction = AppdynamicsAgent.startTransaction(btName, null, EntryTypes.POJO, false);
        transaction.collectData("Command", String.valueOf(commandParam), this.dataScopes);
        transaction.collectData("User ID", getVariable(metaParam, "USR_ID"), this.dataScopes);
        transaction.collectData("LOCALE_ID", getVariable(metaParam, "LOCALE_ID"), this.dataScopes);
        transaction.collectData("WEB_CLIENT_ADDR", getVariable(metaParam, "WEB_SESSIONID"), this.dataScopes);
        transaction.collectData("MOCA_APPL_ID", getVariable(metaParam, "MOCA_APPL_ID"), this.dataScopes);

        return transaction;
    }

    private String getVariable(Object object, String key) {
        Object stringObject = getReflectiveObject(object, getVariableReflector, key);
        return String.valueOf(stringObject);
    }

    @Override
    public void onMethodEnd (Object state, Object objectIntercepted, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return;
        Transaction transaction = (Transaction) state;
        if (exception != null) {
            transaction.markAsError(String.format("Exception: %s",exception.toString()));
        }
        transaction.end();
    }
}
