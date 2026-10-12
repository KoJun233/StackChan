package com.kj.stackchan.delegation;

public class DelegationException extends RuntimeException {
    private final String code;
    private final boolean notFound;
    private final String clarification;
    public DelegationException(String code) { this(code, false); }
    public DelegationException(String code, boolean notFound) {
        super(code); this.code = code; this.notFound = notFound; this.clarification = null;
    }
    public DelegationException(String code, String clarification) {
        super(code); this.code = code; this.notFound = false; this.clarification = clarification;
    }
    public String code() { return code; }
    public boolean notFound() { return notFound; }
    public String clarification() { return clarification; }
}
