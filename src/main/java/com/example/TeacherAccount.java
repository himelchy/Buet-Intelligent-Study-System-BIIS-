package com.example;

public record TeacherAccount(
        TeacherWorkspace workspace,
        String password
) {
    public String id() {
        return workspace.profile().id();
    }
}
