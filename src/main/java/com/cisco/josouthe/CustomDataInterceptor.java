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

public class CustomDataInterceptor extends MyBaseInterceptor {
    List<String> excludeList;
    IReflector getJobId;

    public CustomDataInterceptor () {
        super();

        getJobId = getNewReflectionBuilder().invokeInstanceMethod( "getJobDef", true).invokeInstanceMethod( "getJobId", true).build();
    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.redprairie.moca.job.JobExecution")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("setStatus")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

        rules.add(new Rule.Builder(
                "com.redprairie.seamles.applications.background.BackgroundProcess")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("_executeQuery")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

        rules.add(new Rule.Builder(
                "com.redprairie.moca.server.db.JDBCAdapter")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("executeSQL")
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
        if( className.equals("com.redprairie.moca.job.JobExecution") && methodName.equals("setStatus") ) {
            transaction.collectData("Job ID", getReflectiveString(objectIntercepted, getJobId, "NULL"), this.dataScopes);
        }
        if( className.equals("com.redprairie.seamles.applications.background.BackgroundProcess") && methodName.equals("_executeQuery") ) {
            transaction.collectData("Command", String.valueOf(params[0]), this.dataScopes);
        }
        if( className.equals("com.redprairie.moca.server.db.JDBCAdapter") && methodName.equals("executeSQL") ) {
            transaction.collectData("SQL Statement", String.valueOf(params[2]), this.dataScopes);
            transaction.collectData("Command Path", String.valueOf(params[6]), this.dataScopes);
        }
        return null;
    }

    @Override
    public void onMethodEnd (Object state, Object objectIntercepted, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return;
    }
}
