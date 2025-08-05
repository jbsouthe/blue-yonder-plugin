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

public class MOCAJobInterceptor extends MyBaseInterceptor {
    List<String> excludeList;

    public MOCAJobInterceptor () {
        super();
        this.excludeList = Arrays.asList(".*DEFERRED-EXECUTION.*", ".*UC-PCKRELMGR-DAL01.*", ".*UC-PCKRELMGR-RIV01.*",
                ".*REPLENMGR.*",".*UC_ISSUE_FIXER.*",".*UC-PCKRELMGR-CAR01.*");

    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.redprairie.moca.job.JobCallable")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("call")
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
        String btName = "MOCA Job";
        Object commandParam = params[0];

        String name = String.valueOf(objectIntercepted);
        if( !this.excludeList.stream().noneMatch(regex -> name.matches(regex)))
            btName = btName + "." + name;

        transaction = AppdynamicsAgent.startTransaction(btName, null, EntryTypes.POJO, false);
        transaction.collectData("Command", String.valueOf(commandParam), this.dataScopes);

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
