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

public class MOCATaskInterceptor extends MyBaseInterceptor {
    IReflector getStandardOptions, getUserId, getInstanceNumber, getArgs;

    public MOCATaskInterceptor () {
        super();
        this.getUserId = makeInvokeInstanceMethodReflector("getUserId"); //returns a String
        this.getStandardOptions = makeInvokeInstanceMethodReflector("getStandardOptions"); //returns an Object
        this.getInstanceNumber = makeInvokeInstanceMethodReflector("getInstanceNumber"); //returns a String
        this.getArgs = makeInvokeInstanceMethodReflector("getArgs"); //returns a String

    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.redprairie.seamles.applications.background.BackgroundProcess")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("process")
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
        String btName = "MOCA Task";
        //TODO figure out a good split on this BT

        transaction = AppdynamicsAgent.startTransaction(btName, null, EntryTypes.POJO, false);
        Object standardOptions = getReflectiveObject(objectIntercepted, getStandardOptions);
        transaction.collectData("User ID", getReflectiveString(standardOptions, getUserId, "NULL"), this.dataScopes);
        transaction.collectData("Instance Number", getReflectiveString(standardOptions, getInstanceNumber, "NULL"), this.dataScopes);
        transaction.collectData("Arguments", getReflectiveString(standardOptions, getArgs, "NULL"), this.dataScopes);

        return transaction;
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
