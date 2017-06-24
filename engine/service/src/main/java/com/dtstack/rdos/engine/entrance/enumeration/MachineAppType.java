package com.dtstack.rdos.engine.entrance.enumeration;

/**
 * Created by sishu.yss on 2017/5/22.
 */
public enum MachineAppType {
	
    ENGINE("engine"),WEB("web"),FLINK120("flink"),FLINK130("flink"),SPARK("spark"),DATAX("datax");

    private String type;

    MachineAppType(String type){
        this.type = type;
    }

    public String getType() {
        return type;
    }
    
    public static MachineAppType getMachineAppType(String type){
    	MachineAppType[] machineAppTypes = MachineAppType.values();
    	for(MachineAppType machineAppType:machineAppTypes){
    		if(machineAppType.name().toLowerCase().equals(type)){
    			return machineAppType;
    		}
    	}
    	return null;
    }
    
}

