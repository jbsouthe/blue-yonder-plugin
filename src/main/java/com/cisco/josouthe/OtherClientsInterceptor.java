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

public class OtherClientsInterceptor extends MyBaseInterceptor {
    List<String> excludeList;
    IReflector getUserId, getWorkstation;
    IReflector getDeviceCode, getTerminalId, getHardwareSerialNumber, getInternetAddress, getMocaHost, getMocaEnvMap, getGlobalMap;

    public OtherClientsInterceptor () {
        super();
        this.excludeList = Arrays.asList(".*voice.*", ".*sl_queue probe_string_value.*", ".*sl_set personal_server.*");

        this.getUserId = makeInvokeInstanceMethodReflector("getUserId"); //returns a String
        this.getWorkstation = makeInvokeInstanceMethodReflector("getWorkstation"); //returns an Object
        this.getDeviceCode = makeInvokeInstanceMethodReflector("getDeviceCode"); //returns a String
        this.getTerminalId = makeInvokeInstanceMethodReflector("getTerminalId"); //returns a String
        this.getHardwareSerialNumber = makeInvokeInstanceMethodReflector("getHardwareSerialNumber"); //returns a String
        this.getInternetAddress = makeInvokeInstanceMethodReflector("getInternetAddress"); //returns a String
        this.getMocaHost = makeInvokeInstanceMethodReflector("getMocaHost"); //returns a String
        this.getMocaEnvMap = makeInvokeInstanceMethodReflector("getMocaEnvMap"); //returns a String
        this.getGlobalMap = makeInvokeInstanceMethodReflector("getGlobalMap"); //returns a String

    }

    @Override
    public List<Rule> initializeRules () {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.redprairie.moca.advice.ForwardingMocaContext")
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
        String btName = "OtherClients";
        Object commandParam = params[0];

        String name = String.valueOf(commandParam).split("where")[0].trim();
        if( !this.excludeList.stream().noneMatch(regex -> name.matches(regex)))
            btName = btName + "." + name;

        transaction = AppdynamicsAgent.startTransaction(btName, null, EntryTypes.POJO, false);
        transaction.collectData("Command", String.valueOf(commandParam), this.dataScopes);
        transaction.collectData("User ID", getReflectiveString(objectIntercepted, getUserId, "NULL"), this.dataScopes);
        Object workstation = getReflectiveObject(objectIntercepted, getWorkstation);
        transaction.collectData("Device Code", getReflectiveString(workstation, getDeviceCode, "NULL"), this.dataScopes);
        transaction.collectData("Terminal ID", getReflectiveString(workstation, getTerminalId, "NULL"), this.dataScopes);
        transaction.collectData("Hardware Serial Number", getReflectiveString(workstation, getHardwareSerialNumber, "NULL"), this.dataScopes);
        transaction.collectData("Internet Address", getReflectiveString(workstation, getInternetAddress, "NULL"), this.dataScopes);
        transaction.collectData("MOCA Host", getReflectiveString(workstation, getMocaHost, "NULL"), this.dataScopes);
        transaction.collectData("MOCA Env", getReflectiveString(workstation, getMocaEnvMap, "NULL"), this.dataScopes);
        transaction.collectData("Global Variables Map", getReflectiveString(workstation, getGlobalMap, "NULL"), this.dataScopes);

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
