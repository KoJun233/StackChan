package com.kj.stackchan.role;
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException() { super("Companion role was not found"); }
}
