package com.example;

public record StudentAccount(
        StudentProfile profile,
        String password
) {
    public String roll() {
        return profile.roll();
    }
}
