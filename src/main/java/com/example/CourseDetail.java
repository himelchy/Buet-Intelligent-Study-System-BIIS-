package com.example;

import java.util.List;

public record CourseDetail(
        String code,
        String title,
        int credits,
        String instructor,
        String objective,
        String teacherIntro,
        List<String> resources,
        List<String> pdfs
) {
}
