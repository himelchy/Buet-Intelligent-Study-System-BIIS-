package com.example;

public record TeacherProfile(
        String id,
        String name,
        String department,
        String designation,
        String email,
        String officeRoom,
        String phone
) {
}
