package com.boko.vimusic.model;

import android.util.SparseArray;

public enum HostType {
    LOCAL(0, ""),
    ZING(1, "Zing Mp3");
 
    private int code;
    private String label;
 
    private static SparseArray<HostType> codeToEnumMapping;
 
    private HostType(int code, String label) {
        this.code = code;
        this.label = label;
    }
 
    public static HostType getHost(int i) {
        if (codeToEnumMapping == null) {
            initMapping();
        }
        return codeToEnumMapping.get(i);
    }
 
    private static void initMapping() {
    	codeToEnumMapping = new SparseArray<HostType>();
        for (HostType s : values()) {
        	codeToEnumMapping.put(s.code, s);
        }
    }
 
    public int getCode() {
        return code;
    }
 
    public String getLabel() {
        return label;
    }
 
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Host");
        sb.append("{code=").append(code);
        sb.append(", label='").append(label).append('\'');
        sb.append('}');
        return sb.toString();
    }
}